package com.blugaemand.input

import com.blugaemand.hid.GamepadButton

/**
 * A control the user can touch. Distinct from [GamepadButton] because not every on-screen control
 * maps to a single button — sticks produce axes, the D-pad produces a hat, and the triggers
 * produce both an axis and a button.
 */
sealed interface ControlId {

    /** A plain button that maps one-to-one onto an HID button. */
    data class Button(val button: GamepadButton) : ControlId

    /** An analog thumbstick. */
    data class Stick(val side: Side) : ControlId

    /** An analog trigger. */
    data class Trigger(val side: Side) : ControlId

    /** The whole D-pad, which resolves to a hat value based on where within it the touch lands. */
    data object Dpad : ControlId

    enum class Side { LEFT, RIGHT }
}

/**
 * Where a control sits on screen and how big it is, in coordinates normalised to the 0..1 range.
 *
 * Normalised rather than absolute so a layout renders identically on any screen size, and so the
 * whole thing serialises cleanly once layouts become user-editable.
 *
 * Positions are fractions of the screen's width and height. Sizes are fractions of
 * [ResolvedLayout.unit] instead, so controls stay round and keep their proportions whatever the
 * aspect ratio.
 */
data class ControlSpec(
    val id: ControlId,
    val shape: Shape,
    val label: String = "",
) {
    sealed interface Shape {
        /** Centre of the control, both components in 0..1. */
        val centerX: Float
        val centerY: Float

        /** A circular control; [radius] is a fraction of the layout unit. */
        data class Circle(
            override val centerX: Float,
            override val centerY: Float,
            val radius: Float,
        ) : Shape

        /**
         * A rectangular control. [width] is a fraction of screen width so shoulder buttons stretch
         * across the top edge; [height] is a fraction of the layout unit.
         */
        data class Rect(
            override val centerX: Float,
            override val centerY: Float,
            val width: Float,
            val height: Float,
        ) : Shape

        /**
         * A thumbstick: [radius] is the base the finger may roam within, [knobRadius] the moving
         * cap drawn on top. Both are fractions of the layout unit.
         */
        data class Stick(
            override val centerX: Float,
            override val centerY: Float,
            val radius: Float,
            val knobRadius: Float,
        ) : Shape

        /**
         * A square D-pad cross. [radius] is half the width of the cross, as a fraction of the
         * layout unit. The dead zone in the middle is a fraction of [radius].
         */
        data class Dpad(
            override val centerX: Float,
            override val centerY: Float,
            val radius: Float,
            val deadZone: Float = 0.25f,
        ) : Shape
    }
}
