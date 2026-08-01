package com.blugaemand.hid

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.blugaemand.MainActivity
import com.blugaemand.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Owns the Bluetooth HID peripheral session: acquires the HID_DEVICE profile proxy, registers the
 * gamepad's SDP record and report descriptor, and pushes input reports to whichever host connects.
 *
 * This lives in a foreground service rather than the activity so registration survives rotation
 * and brief backgrounding — losing it mid-game would drop the connection and force a re-pair.
 */
class HidGamepadService : Service() {

    private val binder = LocalBinder()

    private val _status = MutableStateFlow<HidStatus>(HidStatus.Initializing)
    val status: StateFlow<HidStatus> = _status.asStateFlow()

    private val profile: GamepadProfile = GenericHidProfile

    /**
     * Reports go out on a dedicated thread. The Bluetooth stack call is blocking, and sharing a
     * pool with anything else risks a scheduling hiccup showing up as input lag.
     */
    private val reportExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "hid-report").apply { priority = Thread.MAX_PRIORITY }
    }
    private val scope = CoroutineScope(SupervisorJob())
    private var sendJob: Job? = null

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null

    /** Latest state the UI has produced. Read by the send loop, written by the touch handler. */
    @Volatile
    private var desiredState: GamepadState = GamepadState.NEUTRAL

    /** Last report actually put on the wire, used to suppress duplicates. */
    private var lastSentReport: ByteArray? = null

    /** Whether the promotion to a foreground service has succeeded. */
    private var isForeground = false

    inner class LocalBinder : Binder() {
        val service: HidGamepadService get() = this@HidGamepadService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * Picks the session back up when the user switches Bluetooth on, so enabling it from the
     * system prompt is enough on its own — no trip back to the app to hit Retry.
     */
    private val adapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> retry()
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                    releaseProfile()
                    _status.value = HidStatus.BluetoothOff
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ContextCompat.registerReceiver(
            this,
            adapterStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        initialise()
    }

    /**
     * Promotes to a foreground service.
     *
     * Deliberately not done in [onCreate]: a `connectedDevice` foreground service requires a
     * Bluetooth runtime permission to have been *granted*, not merely declared, and the framework
     * throws if it has not. The activity therefore binds first — which creates the service so the
     * UI can read its status — and only starts it once the user has answered the permission
     * prompt.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(adapterStateReceiver) }
        sendJob?.cancel()
        scope.cancel()
        releaseProfile()
        reportExecutor.shutdownNow()
        super.onDestroy()
    }

    // -- Public surface used by the UI ------------------------------------------------------

    /**
     * Records the current control state. Cheap enough to call on every touch event; the send loop
     * decides what actually reaches the host.
     */
    fun updateState(state: GamepadState) {
        desiredState = state
    }

    /** Bonded devices we could plausibly connect to as a gamepad. */
    @SuppressLint("MissingPermission") // Guarded by withBluetoothPermission.
    fun bondedDevices(): List<BluetoothDevice> = withBluetoothPermission(emptyList()) {
        bluetoothAdapter?.bondedDevices?.toList().orEmpty()
    }

    /**
     * Initiates the HID connection to an already-paired host. For a first-time pairing the host
     * normally initiates instead, once the phone has been made discoverable.
     */
    @SuppressLint("MissingPermission") // Guarded by withBluetoothPermission.
    fun connectTo(device: BluetoothDevice) = withBluetoothPermission(Unit) {
        hidDevice?.connect(device)
        Unit
    }

    @SuppressLint("MissingPermission") // Guarded by withBluetoothPermission.
    fun disconnect() = withBluetoothPermission(Unit) {
        connectedHost?.let { hidDevice?.disconnect(it) }
        Unit
    }

    /** Re-runs setup after the user grants permission or switches Bluetooth on. */
    fun retry() {
        releaseProfile()
        initialise()
    }

    // -- Profile lifecycle ------------------------------------------------------------------

    private fun initialise() {
        val manager = getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter
        if (adapter == null) {
            _status.value = HidStatus.Unsupported("This device has no Bluetooth adapter.")
            return
        }
        bluetoothAdapter = adapter

        if (!hasBluetoothPermission()) {
            _status.value = HidStatus.PermissionRequired
            return
        }
        if (!adapter.isEnabled) {
            _status.value = HidStatus.BluetoothOff
            return
        }

        _status.value = HidStatus.Initializing
        val requested = adapter.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE)
        if (!requested) {
            // The profile is not part of this build of Android. Nothing the app can do about it.
            _status.value = HidStatus.Unsupported(
                "This phone's Android build does not include the Bluetooth HID Device profile.",
            )
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profileId: Int, proxy: BluetoothProfile) {
            if (profileId != BluetoothProfile.HID_DEVICE) return
            hidDevice = proxy as BluetoothHidDevice
            registerApp()
        }

        override fun onServiceDisconnected(profileId: Int) {
            if (profileId != BluetoothProfile.HID_DEVICE) return
            hidDevice = null
            connectedHost = null
            stopSendLoop()
            _status.value = HidStatus.Idle
        }
    }

    @SuppressLint("MissingPermission") // Guarded by withBluetoothPermission.
    private fun registerApp() = withBluetoothPermission(Unit) {
        val device = hidDevice ?: return@withBluetoothPermission

        profile.requiredAdapterName?.let { name ->
            if (bluetoothAdapter?.name != name) bluetoothAdapter?.name = name
        }

        val sdp = BluetoothHidDeviceAppSdpSettings(
            profile.sdpName,
            profile.sdpDescription,
            profile.sdpProvider,
            profile.subclass,
            profile.descriptor,
        )

        // Null QoS lets the stack pick sensible defaults, which every host we target accepts.
        val ok = device.registerApp(sdp, null, null, reportExecutor, hidCallback)
        if (!ok) {
            _status.value = HidStatus.Error("Could not register the gamepad with the Bluetooth stack.")
        }
    }

    @SuppressLint("MissingPermission") // Guarded by withBluetoothPermission.
    private fun releaseProfile() = withBluetoothPermission(Unit) {
        stopSendLoop()
        hidDevice?.let { device ->
            runCatching { device.unregisterApp() }
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, device)
        }
        hidDevice = null
        connectedHost = null
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.i(TAG, "App status changed: registered=$registered")
            if (registered) {
                _status.value = HidStatus.Advertising
            } else {
                connectedHost = null
                stopSendLoop()
                _status.value = HidStatus.Idle
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.i(TAG, "Connection state changed: $state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHost = device
                    lastSentReport = null // Force a full report so the host sees a known baseline.
                    startSendLoop()
                    _status.value = HidStatus.Connected(device.displayName())
                }

                BluetoothProfile.STATE_CONNECTING -> {
                    _status.value = HidStatus.Connecting(device.displayName())
                }

                else -> {
                    connectedHost = null
                    stopSendLoop()
                    _status.value =
                        if (hidDevice != null) HidStatus.Advertising else HidStatus.Idle
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            // Windows does issue GET_REPORT during enumeration and will stall waiting for an
            // answer, so this must always reply with something.
            val hid = hidDevice ?: return
            if (device == null) return
            when (type) {
                BluetoothHidDevice.REPORT_TYPE_INPUT -> {
                    hid.replyReport(device, type, id, profile.encode(desiredState))
                }

                BluetoothHidDevice.REPORT_TYPE_FEATURE -> {
                    val feature = profile.featureReport(id.toInt())
                    if (feature != null) {
                        hid.replyReport(device, type, id, feature)
                    } else {
                        hid.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
                    }
                }

                else -> hid.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            // Output and feature reports (rumble, LEDs) are not part of this profile.
            val hid = hidDevice ?: return
            if (device != null) {
                hid.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
            }
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice?) {
            Log.i(TAG, "Host unplugged the virtual cable")
            connectedHost = null
            stopSendLoop()
            _status.value = HidStatus.Advertising
        }
    }

    // -- Report pump ------------------------------------------------------------------------

    /**
     * Sends at most one report per tick, and only when something changed.
     *
     * Sending straight from the touch handler would put a report on the wire for every pointer
     * move — hundreds per second across several fingers — which saturates the L2CAP interrupt
     * channel and shows up as lag rather than responsiveness. Coalescing to a fixed rate keeps
     * latency bounded and predictable.
     */
    private fun startSendLoop() {
        if (sendJob?.isActive == true) return
        sendJob = scope.launch(reportExecutor.asCoroutineDispatcher()) {
            while (isActive) {
                sendIfChanged()
                delay(SEND_INTERVAL_MS)
            }
        }
    }

    private fun stopSendLoop() {
        sendJob?.cancel()
        sendJob = null
        lastSentReport = null
    }

    @SuppressLint("MissingPermission")
    private fun sendIfChanged() {
        val host = connectedHost ?: return
        val hid = hidDevice ?: return
        val report = profile.encode(desiredState)
        if (report.contentEquals(lastSentReport)) return

        runCatching { hid.sendReport(host, profile.reportId, report) }
            .onSuccess { lastSentReport = report }
            .onFailure { Log.w(TAG, "sendReport failed", it) }
    }

    // -- Plumbing ---------------------------------------------------------------------------

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Runs [block] only when the connect permission is held, returning [fallback] otherwise. The
     * catch is a backstop: permission can be revoked between the check and the call.
     */
    private inline fun <T> withBluetoothPermission(fallback: T, block: () -> T): T {
        if (!hasBluetoothPermission()) {
            _status.value = HidStatus.PermissionRequired
            return fallback
        }
        return try {
            block()
        } catch (e: SecurityException) {
            Log.w(TAG, "Bluetooth permission denied", e)
            _status.value = HidStatus.PermissionRequired
            fallback
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice?.displayName(): String = withBluetoothPermission("host") {
        this?.name ?: this?.address ?: "host"
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundCompat() {
        if (isForeground) return
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Even with the activity gating this on the permission being granted, the user can revoke
        // it from Settings while the app is alive. Degrading to a bound-only service beats taking
        // the whole process down.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } else {
                // API 28 predates foreground service types, and the permission checks that come
                // with them.
                startForeground(NOTIFICATION_ID, notification)
            }
            isForeground = true
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot run as a foreground service without a Bluetooth permission", e)
            _status.value = HidStatus.PermissionRequired
        }
    }

    companion object {
        private const val TAG = "Blugaemand"
        private const val CHANNEL_ID = "hid_gamepad"
        private const val NOTIFICATION_ID = 1

        /** 100 Hz. Faster gains nothing perceptible; slower starts to feel sluggish. */
        private const val SEND_INTERVAL_MS = 10L
    }
}
