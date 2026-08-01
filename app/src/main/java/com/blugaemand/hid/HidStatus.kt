package com.blugaemand.hid

/** Where the HID session currently stands, as far as the UI needs to care. */
sealed interface HidStatus {

    /** Waiting for the Bluetooth profile proxy to arrive. */
    data object Initializing : HidStatus

    /** The nearby-devices permission has not been granted. */
    data object PermissionRequired : HidStatus

    /**
     * The permission is granted but the Bluetooth adapter is switched off.
     *
     * Kept distinct from [PermissionRequired] because the two need different things from the user,
     * and an app cannot resolve either on its own — `BluetoothAdapter.enable()` has been a no-op
     * for ordinary apps since Android 13, so the only route is a system prompt.
     */
    data object BluetoothOff : HidStatus

    /**
     * This device cannot act as an HID peripheral. Some manufacturer builds ship without the HID
     * Device profile even on supported Android versions, and there is no workaround.
     */
    data class Unsupported(val reason: String) : HidStatus

    /** Proxy is ready but the gamepad is not advertised yet. */
    data object Idle : HidStatus

    /** Registered and advertising; the host can now pair with or connect to us. */
    data object Advertising : HidStatus

    /** A host is negotiating the connection. */
    data class Connecting(val deviceName: String) : HidStatus

    /** A host is connected and reports are flowing. */
    data class Connected(val deviceName: String) : HidStatus

    /** Something failed in a way worth showing the user. */
    data class Error(val message: String) : HidStatus
}
