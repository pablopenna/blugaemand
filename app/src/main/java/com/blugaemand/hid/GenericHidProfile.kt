package com.blugaemand.hid

/**
 * A standards-compliant HID gamepad: two analog sticks, two analog triggers, an eight-way hat
 * switch and sixteen buttons.
 *
 * Windows enumerates this under Game Controllers as a DirectInput device. It will not appear as an
 * XInput device — XInput is a driver-level interface that Bluetooth HID cannot reach — so the
 * handful of games that speak only XInput will not see it. Everything that reads DirectInput or
 * raw HID (Steam, emulators, most engines) works.
 *
 * Report ID 1, nine bytes:
 *
 * ```
 * byte 0   left stick X    0..255, centred at 128
 * byte 1   left stick Y    0..255, centred at 128
 * byte 2   right stick X   0..255, centred at 128
 * byte 3   right stick Y   0..255, centred at 128
 * byte 4   left trigger    0..255 (Brake)
 * byte 5   right trigger   0..255 (Accelerator)
 * byte 6   low nibble: hat switch 0..7, 8 = centred; high nibble: padding
 * byte 7   buttons 1..8
 * byte 8   buttons 9..16
 * ```
 */
object GenericHidProfile : GamepadProfile {

    override val id: String = "generic-hid-gamepad"
    override val sdpName: String = "Blugaemand Gamepad"
    override val sdpDescription: String = "Virtual Bluetooth Gamepad"
    override val sdpProvider: String = "Blugaemand"

    /** `BluetoothHidDevice.SUBCLASS2_GAMEPAD`. */
    override val subclass: Byte = 0x02

    override val reportId: Int = 1

    /** Number of bytes [encode] produces. */
    const val REPORT_SIZE = 9

    /**
     * Threshold above which an analog trigger also asserts its digital button. Real controllers
     * report both, and hosts that only read buttons would otherwise see nothing.
     */
    private const val TRIGGER_DIGITAL_THRESHOLD = 32

    override val descriptor: ByteArray = byteArrayOf(
        0x05.b, 0x01.b, //  Usage Page (Generic Desktop)
        0x09.b, 0x05.b, //  Usage (Gamepad)
        0xA1.b, 0x01.b, //  Collection (Application)
        0x85.b, 0x01.b, //    Report ID (1)
        0xA1.b, 0x00.b, //    Collection (Physical)

        //  Four stick axes, one byte each, centred at 128.
        0x09.b, 0x30.b, //      Usage (X)   - left stick X
        0x09.b, 0x31.b, //      Usage (Y)   - left stick Y
        0x09.b, 0x32.b, //      Usage (Z)   - right stick X
        0x09.b, 0x35.b, //      Usage (Rz)  - right stick Y
        0x15.b, 0x00.b, //      Logical Minimum (0)
        0x26.b, 0xFF.b, 0x00.b, // Logical Maximum (255)
        0x75.b, 0x08.b, //      Report Size (8)
        0x95.b, 0x04.b, //      Report Count (4)
        0x81.b, 0x02.b, //      Input (Data, Variable, Absolute)

        //  Triggers live on the Simulation Controls page as Brake and Accelerator. This is the
        //  convention DualShock and Xbox Bluetooth controllers use, and both Windows and Android
        //  map it to their trigger axes without help.
        0x05.b, 0x02.b, //      Usage Page (Simulation Controls)
        0x09.b, 0xC5.b, //      Usage (Brake)       - left trigger
        0x09.b, 0xC4.b, //      Usage (Accelerator) - right trigger
        0x15.b, 0x00.b, //      Logical Minimum (0)
        0x26.b, 0xFF.b, 0x00.b, // Logical Maximum (255)
        0x75.b, 0x08.b, //      Report Size (8)
        0x95.b, 0x02.b, //      Report Count (2)
        0x81.b, 0x02.b, //      Input (Data, Variable, Absolute)

        //  D-pad as a hat switch. The null-state flag is what lets a value above the logical
        //  maximum mean "centred" rather than "north".
        0x05.b, 0x01.b, //      Usage Page (Generic Desktop)
        0x09.b, 0x39.b, //      Usage (Hat switch)
        0x15.b, 0x00.b, //      Logical Minimum (0)
        0x25.b, 0x07.b, //      Logical Maximum (7)
        0x35.b, 0x00.b, //      Physical Minimum (0)
        0x46.b, 0x3B.b, 0x01.b, // Physical Maximum (315 degrees)
        0x65.b, 0x14.b, //      Unit (English Rotation: Degrees)
        0x75.b, 0x04.b, //      Report Size (4)
        0x95.b, 0x01.b, //      Report Count (1)
        0x81.b, 0x42.b, //      Input (Data, Variable, Absolute, Null State)
        0x65.b, 0x00.b, //      Unit (None)
        0x75.b, 0x04.b, //      Report Size (4)
        0x95.b, 0x01.b, //      Report Count (1)
        0x81.b, 0x03.b, //      Input (Constant) - pads byte 6 out to a whole byte

        //  Sixteen buttons. See GamepadButton for why the numbering is what it is.
        0x05.b, 0x09.b, //      Usage Page (Button)
        0x19.b, 0x01.b, //      Usage Minimum (Button 1)
        0x29.b, 0x10.b, //      Usage Maximum (Button 16)
        0x15.b, 0x00.b, //      Logical Minimum (0)
        0x25.b, 0x01.b, //      Logical Maximum (1)
        0x75.b, 0x01.b, //      Report Size (1)
        0x95.b, 0x10.b, //      Report Count (16)
        0x81.b, 0x02.b, //      Input (Data, Variable, Absolute)

        0xC0.b, //            End Collection (Physical)
        0xC0.b, //          End Collection (Application)
    )

    override fun encode(state: GamepadState): ByteArray {
        var buttons = state.buttons

        // Mirror the analog triggers onto their digital buttons, the way physical controllers do.
        if (state.leftTrigger >= TRIGGER_DIGITAL_THRESHOLD) buttons = buttons or GamepadButton.L2.bit
        if (state.rightTrigger >= TRIGGER_DIGITAL_THRESHOLD) buttons = buttons or GamepadButton.R2.bit

        return byteArrayOf(
            GamepadState.clampAxis(state.leftStickX).toByte(),
            GamepadState.clampAxis(state.leftStickY).toByte(),
            GamepadState.clampAxis(state.rightStickX).toByte(),
            GamepadState.clampAxis(state.rightStickY).toByte(),
            GamepadState.clampAxis(state.leftTrigger).toByte(),
            GamepadState.clampAxis(state.rightTrigger).toByte(),
            (state.hat.value and 0x0F).toByte(),
            (buttons and 0xFF).toByte(),
            ((buttons shr 8) and 0xFF).toByte(),
        )
    }
}

/** Shorthand so descriptor literals above 0x7F stay readable. */
private inline val Int.b: Byte get() = this.toByte()
