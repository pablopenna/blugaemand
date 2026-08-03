package com.blugaemand.hid

/**
 * Stage 0 scratch profile: [GenericHidProfile]'s descriptor and encoder unchanged, wearing as much
 * of a Pro Controller's identity as Android will let an app set.
 *
 * This exists to be measured, not shipped. Iteration 4 turns on whether a Switch will open a
 * connection to a device it cannot see Nintendo's vendor and product IDs on — and it cannot see
 * them here, because `BluetoothHidDeviceAppSdpSettings` has no field for them and the DeviceID
 * record belongs to the stack. What is left is the SDP strings and the adapter name, so this sets
 * those to exactly what a real pad publishes and leaves the rest to the console. See TODO.md.
 *
 * Delete this file once the measurement is recorded, whichever way it goes.
 */
object SwitchProbeProfile : GamepadProfile by GenericHidProfile {

    override val id: String = "switch-probe"

    /** Verbatim from the reference controller's SDP record. */
    override val sdpName: String = "Wireless Gamepad"
    override val sdpDescription: String = "Gamepad"
    override val sdpProvider: String = "Nintendo"

    /**
     * Renames the phone's Bluetooth adapter globally, which is why this is scratch-only:
     * [HidGamepadService] restores the previous name when the profile is released, but a kill
     * mid-session would leave the phone called this until the next clean exit.
     */
    override val requiredAdapterName: String = "Pro Controller"

    /**
     * The reference controller's own descriptor, read off its SDP record — see TODO.md.
     *
     * The console bonds and then unpairs without ever opening the HID channel, so it is rejecting
     * us on something it reads from SDP. Only two things in that record could be it: the DeviceID
     * record, which is the stack's and says Xiaomi, or this. This is the half we can change.
     *
     * **170 bytes, not the 171 that were captured.** The trailing zero is appended by Android to
     * whatever it is given — `GenericHidProfile` ends `C0 C0` and arrives as `c0 c0 00` — so
     * including the captured one would produce two. Ending at `C0` reproduces the pad exactly.
     *
     * [encode] is still [GenericHidProfile]'s and still emits report `0x01`, which this descriptor
     * does not declare. That mismatch is deliberate: this measures whether SDP gets us past the
     * bond, and nothing beyond that point works until Stage 2 writes the real reports anyway.
     */
    override val descriptor: ByteArray = byteArrayOf(
        0x05.b, 0x01.b, //   Usage Page (Generic Desktop)
        0x09.b, 0x05.b, //   Usage (Game Pad)
        0xA1.b, 0x01.b, //   Collection (Application)
        0x06.b, 0x01.b, 0xFF.b, //  Usage Page (Vendor 0xFF01)

        // Vendor-defined input reports: 0x21 subcommand replies and 0x30 full state, 48 bytes each.
        0x85.b, 0x21.b, 0x09.b, 0x21.b, 0x75.b, 0x08.b, 0x95.b, 0x30.b, 0x81.b, 0x02.b,
        0x85.b, 0x30.b, 0x09.b, 0x30.b, 0x75.b, 0x08.b, 0x95.b, 0x30.b, 0x81.b, 0x02.b,

        // 0x31, 0x32, 0x33 — 361 bytes each, NFC and IR.
        0x85.b, 0x31.b, 0x09.b, 0x31.b, 0x75.b, 0x08.b, 0x96.b, 0x69.b, 0x01.b, 0x81.b, 0x02.b,
        0x85.b, 0x32.b, 0x09.b, 0x32.b, 0x75.b, 0x08.b, 0x96.b, 0x69.b, 0x01.b, 0x81.b, 0x02.b,
        0x85.b, 0x33.b, 0x09.b, 0x33.b, 0x75.b, 0x08.b, 0x96.b, 0x69.b, 0x01.b, 0x81.b, 0x02.b,

        // 0x3F — the simple report, and the only one a driverless host can read.
        0x85.b, 0x3F.b, //   Report ID (0x3F)
        0x05.b, 0x09.b, //     Usage Page (Button)
        0x19.b, 0x01.b, //     Usage Minimum (1)
        0x29.b, 0x10.b, //     Usage Maximum (16)
        0x15.b, 0x00.b, //     Logical Minimum (0)
        0x25.b, 0x01.b, //     Logical Maximum (1)
        0x75.b, 0x01.b, //     Report Size (1)
        0x95.b, 0x10.b, //     Report Count (16)
        0x81.b, 0x02.b, //     Input (Data, Variable, Absolute)
        0x05.b, 0x01.b, //     Usage Page (Generic Desktop)
        0x09.b, 0x39.b, //     Usage (Hat switch)
        0x15.b, 0x00.b, //     Logical Minimum (0)
        0x25.b, 0x07.b, //     Logical Maximum (7)
        0x75.b, 0x04.b, //     Report Size (4)
        0x95.b, 0x01.b, //     Report Count (1)
        0x81.b, 0x42.b, //     Input (Data, Variable, Absolute, Null State)
        0x05.b, 0x09.b, //     Usage Page (Button)
        0x75.b, 0x04.b, //     Report Size (4)
        0x95.b, 0x01.b, //     Report Count (1)
        0x81.b, 0x01.b, //     Input (Constant) — padding
        0x05.b, 0x01.b, //     Usage Page (Generic Desktop)
        0x09.b, 0x30.b, //     Usage (X)
        0x09.b, 0x31.b, //     Usage (Y)
        0x09.b, 0x33.b, //     Usage (Rx)
        0x09.b, 0x34.b, //     Usage (Ry)
        0x16.b, 0x00.b, 0x00.b, //   Logical Minimum (0)
        0x27.b, 0xFF.b, 0xFF.b, 0x00.b, 0x00.b, // Logical Maximum (65535)
        0x75.b, 0x10.b, //     Report Size (16)
        0x95.b, 0x04.b, //     Report Count (4)
        0x81.b, 0x02.b, //     Input (Data, Variable, Absolute)

        // Output reports: rumble and subcommands, 48 bytes each.
        0x06.b, 0x01.b, 0xFF.b, // Usage Page (Vendor 0xFF01)
        0x85.b, 0x01.b, 0x09.b, 0x01.b, 0x75.b, 0x08.b, 0x95.b, 0x30.b, 0x91.b, 0x02.b,
        0x85.b, 0x10.b, 0x09.b, 0x10.b, 0x75.b, 0x08.b, 0x95.b, 0x30.b, 0x91.b, 0x02.b,
        0x85.b, 0x11.b, 0x09.b, 0x11.b, 0x75.b, 0x08.b, 0x95.b, 0x30.b, 0x91.b, 0x02.b,
        0x85.b, 0x12.b, 0x09.b, 0x12.b, 0x75.b, 0x08.b, 0x95.b, 0x30.b, 0x91.b, 0x02.b,

        0xC0.b, //           End Collection
    )
}

private inline val Int.b: Byte get() = this.toByte()
