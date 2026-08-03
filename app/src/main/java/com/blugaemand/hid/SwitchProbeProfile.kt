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
}
