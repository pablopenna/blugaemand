package com.blugaemand.input.layouts

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.ControlId
import com.blugaemand.input.ControlId.Side
import com.blugaemand.input.ControlSpec
import com.blugaemand.input.GamepadLayout
import com.blugaemand.input.LayoutStyle

/**
 * The built-in Xbox-style layout drawn as shapes and labels: two sticks, a D-pad, four face
 * buttons, four shoulder controls, the three centre buttons and the two stick clicks.
 *
 * Authored against a 16:9 landscape screen. Sizes are relative to
 * [com.blugaemand.input.ResolvedLayout.unit], so squarer screens scale the controls down rather
 * than letting them collide; the clusters still drift a little as the aspect ratio changes, which
 * is what the layout editor will eventually let people fix to taste.
 *
 * This is also the geometry [XBOX_LAYOUT] draws with art, so a position tuned here moves on both.
 */
val DEFAULT_LAYOUT = GamepadLayout(
    id = "default",
    name = "Default",
    style = LayoutStyle.Colors(),
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

        // Right hand: face buttons above, stick below. The diamond is spread wider than the
        // buttons strictly need so there is a clear gap between neighbours — at these sizes a
        // thumb covers far more than one button's worth of glass.
        //
        // X and Y are deliberately crossed. Xbox letters the diamond by position — Y north, X
        // west — while HID letters it by slot: `BTN_X` is an alias of `BTN_NORTH` and `BTN_Y` of
        // `BTN_WEST`, a leftover from six-button A/B/C/X/Y/Z pads where the letters ran in numeric
        // order rather than around a diamond. Hosts report the alias, so wiring each key to its
        // same-letter slot makes a press of X arrive as Y and vice versa. The face plate is a
        // property of the layout, not of the wire format, so the crossing belongs here rather than
        // in [GamepadButton] — a Nintendo-style layout, which also swaps A and B, will do the same
        // thing. Anything keyed off these controls has to follow the label, not the id; see the
        // icon table in [XBOX_LAYOUT].
        ControlSpec(
            id = ControlId.Button(GamepadButton.X),
            shape = ControlSpec.Shape.Circle(0.870f, 0.295f, radius = 0.072f),
            label = "Y",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.Y),
            shape = ControlSpec.Shape.Circle(0.812f, 0.420f, radius = 0.072f),
            label = "X",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.B),
            shape = ControlSpec.Shape.Circle(0.928f, 0.420f, radius = 0.072f),
            label = "B",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.A),
            shape = ControlSpec.Shape.Circle(0.870f, 0.545f, radius = 0.072f),
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

        // Stick clicks, kept as their own buttons rather than a press-the-stick gesture so they
        // cannot fire by accident mid-movement.
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
