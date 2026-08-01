package com.blugaemand

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blugaemand.data.LayoutStore
import com.blugaemand.hid.GamepadState
import com.blugaemand.hid.HidGamepadService
import com.blugaemand.hid.HidStatus
import com.blugaemand.input.LayoutLibrary
import com.blugaemand.input.layouts.DEFAULT_LAYOUT
import com.blugaemand.ui.GamepadScreen
import com.blugaemand.ui.HostOption
import com.blugaemand.ui.TopBar
import com.blugaemand.ui.TopPanel
import com.blugaemand.ui.theme.BlugaemandTheme
import com.blugaemand.ui.theme.OverlayColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Hosts the pad and owns the connection to [HidGamepadService].
 *
 * The activity binds to the service rather than driving Bluetooth itself, so registration and any
 * live host connection outlive configuration changes and backgrounding.
 */
class MainActivity : ComponentActivity() {

    private var service: HidGamepadService? by mutableStateOf(null)

    /** Stands in for the service's status flow until the bind lands. */
    private val fallbackStatus = MutableStateFlow<HidStatus>(HidStatus.Initializing)

    private var bonded: List<HostOption> by mutableStateOf(emptyList())

    private val layoutStore by lazy { LayoutStore(this) }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? HidGamepadService.LocalBinder)?.service
            refreshBondedDevices()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // Only now is it safe to promote the service: a connectedDevice foreground service needs a
        // Bluetooth permission actually granted, and the framework throws otherwise.
        if (hasBluetoothPermission()) startForegroundSession()
        service?.retry()
        refreshBondedDevices()
    }

    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshBondedDevices() }

    /**
     * Asks Android to switch Bluetooth on. An app cannot do this itself — `BluetoothAdapter.enable`
     * has been a no-op for ordinary apps since Android 13 — so a system prompt is the only route.
     * The service also listens for the adapter coming up, so no follow-up is needed here.
     */
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { service?.retry() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        goImmersive()

        setContent {
            BlugaemandTheme {
                var openPanel by remember { mutableStateOf<TopPanel?>(null) }
                val status by (service?.status ?: fallbackStatus).collectAsStateWithLifecycle()

                val scope = rememberCoroutineScope()
                val library by layoutStore.library
                    .collectAsStateWithLifecycle(initialValue = LayoutLibrary())
                val selectedId by layoutStore.selectedId
                    .collectAsStateWithLifecycle(initialValue = DEFAULT_LAYOUT.id)
                // A selection can outlive the layout it names -- deleting the active layout is the
                // obvious way, but so is a stored id whose layout failed to parse. Falling back
                // rather than showing nothing keeps the pad usable either way.
                val layout = library.byId(selectedId) ?: DEFAULT_LAYOUT

                // The connection panel only exists to get connected. Once that has happened it is
                // just covering the pad, so fold it away rather than making the user dismiss it.
                // The menu is left alone: it is not about connecting, and closing it under the
                // user's thumb because a host turned up would be startling.
                LaunchedEffect(status) {
                    if (status is HidStatus.Connected && openPanel == TopPanel.Connection) {
                        openPanel = null
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    GamepadScreen(
                        layout = layout,
                        onStateChange = { service?.updateState(it) },
                    )

                    // Sits between the pad and the bar while a panel is open: dismisses on a
                    // touch anywhere else, and swallows that touch so it cannot also press a
                    // button underneath. Releasing the pad's held controls avoids leaving anything
                    // stuck down on the host.
                    if (openPanel != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(OverlayColors.Scrim)
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false).consume()
                                        openPanel = null
                                    }
                                },
                        )
                    }

                    TopBar(
                        status = status,
                        hosts = bonded,
                        layouts = library.all,
                        selectedLayoutId = layout.id,
                        openPanel = openPanel,
                        onOpenPanelChange = { panel ->
                            openPanel = panel
                            if (panel != null) {
                                // Either panel covers the pad, so anything held is about to be
                                // unreachable; do not leave it asserted on the host.
                                service?.updateState(GamepadState.NEUTRAL)
                            }
                            if (panel == TopPanel.Connection) refreshBondedDevices()
                        },
                        onFixBlocker = { fixBlocker(status) },
                        onMakeDiscoverable = ::launchDiscoverable,
                        onConnect = ::connectTo,
                        onRetry = {
                            if (hasBluetoothPermission()) {
                                startForegroundSession()
                                service?.retry()
                            } else {
                                ensurePermissions()
                            }
                        },
                        onSelectLayout = { chosen ->
                            scope.launch { layoutStore.select(chosen) }
                            // Switching rebuilds the router, which drops its pointer bindings
                            // without ever emitting a release — so whatever the last report
                            // asserted would stay asserted on the host.
                            service?.updateState(GamepadState.NEUTRAL)
                        },
                        onQuit = {
                            openPanel = null
                            stopGamepad()
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp),
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind unconditionally so the UI can read the service's status even when permissions are
        // missing; only promote it to the foreground once we are allowed to.
        bindToService()
        if (hasBluetoothPermission()) startForegroundSession() else ensurePermissions()
    }

    override fun onStop() {
        super.onStop()
        // Only the binding goes away; the foreground service keeps the HID session alive.
        runCatching { unbindService(connection) }
        service = null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            goImmersive()
        } else {
            // A shade pull or task switch must not leave buttons stuck down on the host.
            service?.updateState(GamepadState.NEUTRAL)
        }
    }

    // -- Service and permissions --------------------------------------------------------------

    private fun bindToService() {
        val intent = Intent(this, HidGamepadService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun startForegroundSession() {
        startForegroundService(Intent(this, HidGamepadService::class.java))
    }

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun stopGamepad() {
        runCatching { unbindService(connection) }
        service = null
        stopService(Intent(this, HidGamepadService::class.java))
        finish()
    }

    private fun ensurePermissions() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    /** Resolves whatever is currently blocking the session. */
    private fun fixBlocker(status: HidStatus) {
        when (status) {
            HidStatus.PermissionRequired -> ensurePermissions()
            HidStatus.BluetoothOff -> runCatching {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }

            else -> Unit
        }
    }

    private fun launchDiscoverable() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_SECONDS)
        }
        runCatching { discoverableLauncher.launch(intent) }
    }

    @SuppressLint("MissingPermission") // The service re-checks before touching the adapter.
    private fun refreshBondedDevices() {
        bonded = service?.bondedDevices().orEmpty().map { device ->
            HostOption(
                name = runCatching { device.name }.getOrNull() ?: device.address,
                address = device.address,
            )
        }
    }

    private fun connectTo(host: HostOption) {
        val device = service?.bondedDevices()?.firstOrNull { it.address == host.address } ?: return
        service?.connectTo(device)
    }

    /**
     * Full-screen with no system bars, and the side edges opted out of the back gesture — an
     * edge swipe mid-game should move a stick, not navigate away.
     */
    private fun goImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val view = window.decorView
            view.post {
                val strip = (view.width * GESTURE_EXCLUSION_FRACTION).toInt()
                view.systemGestureExclusionRects = listOf(
                    Rect(0, 0, strip, view.height),
                    Rect(view.width - strip, 0, view.width, view.height),
                )
            }
        }
    }

    private companion object {
        const val DISCOVERABLE_SECONDS = 300
        const val GESTURE_EXCLUSION_FRACTION = 0.06f
    }
}
