package com.blugaemand.hid

/**
 * Everything that is specific to how one class of host expects a gamepad to present itself: the
 * SDP metadata it sees while pairing, the HID report descriptor it parses, and the wire format of
 * the input reports.
 *
 * This is the seam for supporting additional hosts. Windows, Linux and Android all accept the
 * standards-compliant [GenericHidProfile]; fussier hosts (the Nintendo Switch in particular)
 * expect a specific report layout and get their own implementation rather than special cases
 * threaded through the service.
 *
 * Deliberately free of Android imports so implementations can be unit-tested on the JVM.
 */
interface GamepadProfile {
    /** Stable identifier, used for persistence and logging. */
    val id: String

    /** Service name advertised in the SDP record. */
    val sdpName: String

    /** Human-readable description advertised in the SDP record. */
    val sdpDescription: String

    /** Provider string advertised in the SDP record. */
    val sdpProvider: String

    /**
     * HID device subclass byte, published as SDP attribute `0x0202`.
     *
     * It does **not** change the class-of-device the host sees, which is the adapter's and stays
     * *Phone* however this is set — measured against a BlueZ host, see TODO.md. So it does not
     * decide the icon or category the phone is filed under either. What it is, per the HID
     * profile, is a copy of the low byte of a Class of Device: device kind at bits 7-6, device
     * type at bits 5-2.
     *
     * Kept as a plain byte so this file stays Android-free — and note the
     * `BluetoothHidDevice.SUBCLASS2_*` constants are *not* shifted into place, so they are the
     * wrong thing to assign here.
     */
    val subclass: Byte

    /** The raw HID report descriptor handed to the host during SDP. */
    val descriptor: ByteArray

    /** Report ID that [encode] produces data for. */
    val reportId: Int

    /**
     * A Bluetooth adapter name the host insists on before it will accept the connection, or null
     * to leave the adapter name alone.
     *
     * This exists because Android's `BluetoothHidDeviceAppSdpSettings` exposes only name,
     * description, provider, subclass and descriptor — there is no way to set a USB vendor or
     * product ID. Hosts that identify controllers by VID/PID cannot be fully impersonated, and the
     * adapter name is the only remaining lever.
     */
    val requiredAdapterName: String? get() = null

    /** Serialises a state snapshot into the report body, excluding the report ID. */
    fun encode(state: GamepadState): ByteArray

    /**
     * Answers a host-initiated GET_REPORT for a feature report, or null if this profile has none.
     * Input reports are answered from live state by the service instead.
     */
    fun featureReport(reportId: Int): ByteArray? = null
}
