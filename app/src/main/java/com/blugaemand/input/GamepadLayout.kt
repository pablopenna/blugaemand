package com.blugaemand.input

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.ControlId.Side

/**
 * An arrangement of on-screen controls.
 *
 * Plain data on purpose. Making layouts user-configurable later means serialising this and adding
 * an editor; nothing else has to change, because [com.blugaemand.ui.GamepadScreen] takes the
 * layout as a parameter and never reaches for a global.
 */
data class GamepadLayout(
    val id: String,
    val name: String,
    val controls: List<ControlSpec>,
) {
    companion object {
        /**
         * The built-in Xbox-style layout: two sticks, a D-pad, four face buttons, four shoulder
         * controls, the three centre buttons and the two stick clicks.
         *
         * Positions were laid out against a 16:9 landscape screen. Circular controls size
         * themselves from screen height so they stay round, which means unusually tall or wide
         * screens shift the clusters slightly — acceptable until the layout editor exists.
         */
        val XBOX_DEFAULT = GamepadLayout(
            id = "xbox-default",
            name = "Xbox style",
            controls = listOf(
                // Left hand: stick above, D-pad below.
                ControlSpec(
                    id = ControlId.Stick(Side.LEFT),
                    shape = ControlSpec.Shape.Stick(0.16f, 0.46f, radius = 0.20f, knobRadius = 0.09f),
                    label = "L",
                ),
                ControlSpec(
                    id = ControlId.Dpad,
                    shape = ControlSpec.Shape.Dpad(0.13f, 0.83f, radius = 0.13f),
                ),

                // Right hand: face buttons above, stick below.
                ControlSpec(
                    id = ControlId.Button(GamepadButton.Y),
                    shape = ControlSpec.Shape.Circle(0.870f, 0.310f, radius = 0.075f),
                    label = "Y",
                ),
                ControlSpec(
                    id = ControlId.Button(GamepadButton.X),
                    shape = ControlSpec.Shape.Circle(0.820f, 0.420f, radius = 0.075f),
                    label = "X",
                ),
                ControlSpec(
                    id = ControlId.Button(GamepadButton.B),
                    shape = ControlSpec.Shape.Circle(0.920f, 0.420f, radius = 0.075f),
                    label = "B",
                ),
                ControlSpec(
                    id = ControlId.Button(GamepadButton.A),
                    shape = ControlSpec.Shape.Circle(0.870f, 0.530f, radius = 0.075f),
                    label = "A",
                ),
                ControlSpec(
                    id = ControlId.Stick(Side.RIGHT),
                    shape = ControlSpec.Shape.Stick(0.78f, 0.80f, radius = 0.18f, knobRadius = 0.08f),
                    label = "R",
                ),

                // Shoulders and triggers along the top edge, triggers outermost.
                ControlSpec(
                    id = ControlId.Trigger(Side.LEFT),
                    shape = ControlSpec.Shape.Rect(0.09f, 0.08f, width = 0.12f, height = 0.11f),
                    label = "LT",
                ),
                ControlSpec(
                    id = ControlId.Button(GamepadButton.L1),
                    shape = ControlSpec.Shape.Rect(0.23f, 0.08f, width = 0.12f, height = 0.11f),
                    label = "LB",
                ),
                ControlSpec(
                    id = ControlId.Button(GamepadButton.R1),
                    shape = ControlSpec.Shape.Rect(0.77f, 0.08f, width = 0.12f, height = 0.11f),
                    label = "RB",
                ),
                ControlSpec(
                    id = ControlId.Trigger(Side.RIGHT),
                    shape = ControlSpec.Shape.Rect(0.91f, 0.08f, width = 0.12f, height = 0.11f),
                    label = "RT",
                ),

                // Centre cluster.
                ControlSpec(
                    id = ControlId.Button(GamepadButton.BACK),
                    shape = ControlSpec.Shape.Circle(0.42f, 0.20f, radius = 0.055f),
                    label = "◀◀",
                ),
                ControlSpec(
                    id = ControlId.Button(GamepadButton.GUIDE),
                    shape = ControlSpec.Shape.Circle(0.50f, 0.20f, radius = 0.06f),
                    label = "⌂",
                ),
                ControlSpec(
                    id = ControlId.Button(GamepadButton.START),
                    shape = ControlSpec.Shape.Circle(0.58f, 0.20f, radius = 0.055f),
                    label = "▶▶",
                ),

                // Stick clicks, kept as their own buttons rather than a press-the-stick gesture so
                // they cannot fire by accident mid-movement.
                ControlSpec(
                    id = ControlId.Button(GamepadButton.L3),
                    shape = ControlSpec.Shape.Circle(0.42f, 0.82f, radius = 0.06f),
                    label = "L3",
                ),
                ControlSpec(
                    id = ControlId.Button(GamepadButton.R3),
                    shape = ControlSpec.Shape.Circle(0.58f, 0.82f, radius = 0.06f),
                    label = "R3",
                ),
            ),
        )
    }
}
