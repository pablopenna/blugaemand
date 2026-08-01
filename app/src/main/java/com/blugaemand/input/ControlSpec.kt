package com.blugaemand.input

import com.blugaemand.hid.GamepadButton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A control the user can touch. Distinct from [GamepadButton] because not every on-screen control
 * maps to a single button — sticks produce axes, the D-pad produces a hat, and the triggers
 * produce both an axis and a button.
 *
 * Every variant names its own discriminator. Left to itself kotlinx would write the fully-qualified
 * Kotlin name, and then moving this file to another package would orphan every layout already saved
 * on a phone; see [LayoutJson].
 */
@Serializable
sealed interface ControlId {

    /** A plain button that maps one-to-one onto an HID button. */
    @Serializable
    @SerialName("button")
    data class Button(val button: GamepadButton) : ControlId

    /** An analog thumbstick. */
    @Serializable
    @SerialName("stick")
    data class Stick(val side: Side) : ControlId

    /** An analog trigger. */
    @Serializable
    @SerialName("trigger")
    data class Trigger(val side: Side) : ControlId

    /** The whole D-pad, which resolves to a hat value based on where within it the touch lands. */
    @Serializable
    @SerialName("dpad")
    data object Dpad : ControlId

    @Serializable
    enum class Side { LEFT, RIGHT }

    companion object {
        /**
         * Every control a layout could hold, which is what the editor's *add control* page offers
         * once the ones already placed are taken out.
         *
         * [GamepadButton.L2] and [GamepadButton.R2] appear as buttons here even though the built-in
         * layouts reach them through [Trigger] instead: they are the digital companions to the
         * analog axes, and a layout is free to place one as a plain button. That is also why
         * [missingButtons] excuses them.
         */
        val ALL: List<ControlId> = buildList {
            add(Dpad)
            for (side in Side.entries) add(Stick(side))
            for (side in Side.entries) add(Trigger(side))
            for (button in GamepadButton.entries) add(Button(button))
        }
    }
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
@Serializable
data class ControlSpec(
    val id: ControlId,
    val shape: Shape,
    /**
     * Drawn in the middle of the control. In [LayoutStyle.Images] mode it shows only where the
     * pack has no glyph for the control and it falls back to its shape.
     */
    val label: String = "",
) {
    @Serializable
    sealed interface Shape {
        /** Centre of the control, both components in 0..1. */
        val centerX: Float
        val centerY: Float

        /** A circular control; [radius] is a fraction of the layout unit. */
        @Serializable
        @SerialName("circle")
        data class Circle(
            override val centerX: Float,
            override val centerY: Float,
            val radius: Float,
        ) : Shape

        /**
         * A rectangular control. [width] is a fraction of screen width so shoulder buttons stretch
         * across the top edge; [height] is a fraction of the layout unit.
         */
        @Serializable
        @SerialName("rect")
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
        @Serializable
        @SerialName("stick")
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
        @Serializable
        @SerialName("dpad")
        data class Dpad(
            override val centerX: Float,
            override val centerY: Float,
            val radius: Float,
            val deadZone: Float = 0.25f,
        ) : Shape
    }
}
