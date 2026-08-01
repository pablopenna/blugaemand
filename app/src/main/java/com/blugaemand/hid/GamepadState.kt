package com.blugaemand.hid

/**
 * Direction of the D-pad, encoded the way an HID "hat switch" control expects: eight compass
 * positions clockwise from north, and a null value for centred.
 */
enum class Hat(val value: Int) {
    NORTH(0),
    NORTH_EAST(1),
    EAST(2),
    SOUTH_EAST(3),
    SOUTH(4),
    SOUTH_WEST(5),
    WEST(6),
    NORTH_WEST(7),

    /** Any value above the logical maximum of 7 reads as "not pressed" thanks to the null-state flag. */
    CENTER(8),
    ;

    companion object {
        /**
         * Resolves the four cardinal presses into one of the eight hat positions. Opposing presses
         * cancel out, which is how physical D-pads behave.
         */
        fun of(up: Boolean, down: Boolean, left: Boolean, right: Boolean): Hat {
            val vertical = (if (up) -1 else 0) + (if (down) 1 else 0)
            val horizontal = (if (left) -1 else 0) + (if (right) 1 else 0)
            return when {
                vertical < 0 && horizontal == 0 -> NORTH
                vertical < 0 && horizontal > 0 -> NORTH_EAST
                vertical == 0 && horizontal > 0 -> EAST
                vertical > 0 && horizontal > 0 -> SOUTH_EAST
                vertical > 0 && horizontal == 0 -> SOUTH
                vertical > 0 && horizontal < 0 -> SOUTH_WEST
                vertical == 0 && horizontal < 0 -> WEST
                vertical < 0 && horizontal < 0 -> NORTH_WEST
                else -> CENTER
            }
        }
    }
}

/**
 * The buttons this gamepad exposes, and the HID button number each one occupies.
 *
 * The numbering is not arbitrary. Linux's `hid-input` driver maps the buttons of a gamepad
 * collection onto evdev codes in a fixed order — BTN_SOUTH, BTN_EAST, BTN_C, BTN_NORTH, BTN_WEST,
 * BTN_Z, BTN_TL, BTN_TR, BTN_TL2, BTN_TR2, BTN_SELECT, BTN_START, BTN_MODE, BTN_THUMBL,
 * BTN_THUMBR — and Android inherits that mapping. Following it means Linux, Raspberry Pi and
 * Android hosts label the buttons correctly with no per-device configuration. Windows numbers
 * buttons positionally and does not care either way, so matching the convention costs nothing now
 * and saves work when the other hosts land.
 *
 * Buttons 3, 6 and 16 are intentionally unused: skipping them is what keeps the rest aligned.
 *
 * The face buttons are named by position rather than by letter, since the letters are not portable:
 * Xbox puts Y north and X west, Nintendo swaps both pairs, and HID's own aliases are a third
 * arrangement again. Which glyph a slot wears belongs to the layout's face plate.
 */
enum class GamepadButton(val hidButtonNumber: Int) {
    SOUTH(1), // BTN_SOUTH
    EAST(2), // BTN_EAST
    NORTH(4), // BTN_NORTH, aliased BTN_X
    WEST(5), // BTN_WEST, aliased BTN_Y
    L1(7), // BTN_TL
    R1(8), // BTN_TR
    L2(9), // BTN_TL2  - digital companion to the analog left trigger
    R2(10), // BTN_TR2  - digital companion to the analog right trigger
    BACK(11), // BTN_SELECT
    START(12), // BTN_START
    GUIDE(13), // BTN_MODE
    L3(14), // BTN_THUMBL
    R3(15), // BTN_THUMBR
    ;

    /** Position of this button within the 16-bit button mask. */
    val bit: Int get() = 1 shl (hidButtonNumber - 1)
}

/**
 * A complete snapshot of the gamepad at one instant. Immutable and free of Android dependencies so
 * it can be exercised in plain JVM tests.
 *
 * Axes use the 0..255 range the HID descriptor declares. Sticks are centred at [AXIS_CENTER] with
 * lower values meaning left/up; triggers rest at [AXIS_MIN].
 */
data class GamepadState(
    val leftStickX: Int = AXIS_CENTER,
    val leftStickY: Int = AXIS_CENTER,
    val rightStickX: Int = AXIS_CENTER,
    val rightStickY: Int = AXIS_CENTER,
    val leftTrigger: Int = AXIS_MIN,
    val rightTrigger: Int = AXIS_MIN,
    val hat: Hat = Hat.CENTER,
    val buttons: Int = 0,
) {
    fun isPressed(button: GamepadButton): Boolean = buttons and button.bit != 0

    fun withButton(button: GamepadButton, pressed: Boolean): GamepadState = copy(
        buttons = if (pressed) buttons or button.bit else buttons and button.bit.inv(),
    )

    companion object {
        const val AXIS_MIN = 0
        const val AXIS_CENTER = 128
        const val AXIS_MAX = 255

        val NEUTRAL = GamepadState()

        /** Clamps a raw value into the 0..255 range the descriptor declares. */
        fun clampAxis(value: Int): Int = value.coerceIn(AXIS_MIN, AXIS_MAX)

        /**
         * Converts a -1f..1f stick displacement into the 0..255 axis range, where -1 is fully
         * left/up and +1 is fully right/down.
         *
         * A centre of 128 in a 0..255 range is not symmetric — there are 128 steps below it and
         * only 127 above. Scaling by 128 and clamping puts full deflection on the rails in both
         * directions; the cost is that the top 1/128th of travel saturates, which is well below
         * anything a thumb can resolve. Scaling by 127 instead would leave full-left reading 1,
         * and a stick that never quite reaches its stop is the more noticeable flaw.
         */
        fun axisFromUnit(unit: Float): Int =
            clampAxis(Math.round(AXIS_CENTER + unit.coerceIn(-1f, 1f) * 128f))

        /** Converts a 0f..1f trigger pull into the 0..255 axis range. */
        fun triggerFromUnit(unit: Float): Int =
            clampAxis(Math.round(unit.coerceIn(0f, 1f) * AXIS_MAX))
    }
}
