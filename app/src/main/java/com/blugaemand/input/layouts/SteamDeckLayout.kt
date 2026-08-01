package com.blugaemand.input.layouts

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.ControlId
import com.blugaemand.input.ControlId.Side
import com.blugaemand.input.ControlSpec
import com.blugaemand.input.GamepadLayout
import com.blugaemand.input.LayoutStyle
import com.blugaemand.input.art.STEAM_DECK_ART

/**
 * A Steam Deck.
 *
 * Authored in full rather than derived from [DEFAULT_LAYOUT], for the same reason [SWITCH_LAYOUT]
 * is. Valve did keep the Xbox arrangement of stick-above-D-pad and diamond-above-stick — that much
 * is genuinely the same, and pretending otherwise would be inventing a difference. What is not the
 * same is where all of it sits:
 *
 * - **Everything rides high.** On the real thing the lower third of each side is trackpad, so the
 *   sticks, the D-pad and the diamond are all pushed up and out towards the grips. Nothing here
 *   reaches as far down as an Xbox pad's right stick does.
 * - **The face diamond is tighter and higher.** The Deck's ABXY is a compact cluster near the top
 *   edge rather than the spread-out diamond a full-size pad has room for.
 * - **The centre is spread, not stacked.** View and Options sit high and well apart, up level with
 *   the top of each cluster; the Steam button sits alone, low and central. On a full-size pad those
 *   three are a row.
 *
 * The trackpads and the four back paddles are not here, because the pad exposes a standard HID
 * gamepad and there is no control for them to be.
 */
val STEAM_DECK_LAYOUT: GamepadLayout = GamepadLayout(
    id = "steamdeck",
    name = "Steam Deck",
    style = LayoutStyle.Images(STEAM_DECK_ART),
    controls = listOf(
        // Left hand, both pushed up: the stick high and hard outboard, the D-pad above where an
        // Xbox pad would put it because the trackpad owns the space below.
        ControlSpec(
            id = ControlId.Stick(Side.LEFT),
            shape = ControlSpec.Shape.Stick(0.13f, 0.34f, radius = 0.19f, knobRadius = 0.085f),
            label = "L",
        ),
        ControlSpec(
            id = ControlId.Dpad,
            shape = ControlSpec.Shape.Dpad(0.20f, 0.72f, radius = 0.12f),
        ),

        // Right hand. The diamond is tighter than the shared one -- 0.055 across and 0.115 down
        // from its centre, against the default's 0.058 and 0.125 -- and sits higher.
        ControlSpec(
            id = ControlId.Button(GamepadButton.WEST),
            shape = ControlSpec.Shape.Circle(0.870f, 0.215f, radius = 0.066f),
            label = "Y",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.NORTH),
            shape = ControlSpec.Shape.Circle(0.815f, 0.330f, radius = 0.066f),
            label = "X",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.EAST),
            shape = ControlSpec.Shape.Circle(0.925f, 0.330f, radius = 0.066f),
            label = "B",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.SOUTH),
            shape = ControlSpec.Shape.Circle(0.870f, 0.445f, radius = 0.066f),
            label = "A",
        ),
        ControlSpec(
            id = ControlId.Stick(Side.RIGHT),
            shape = ControlSpec.Shape.Stick(0.79f, 0.74f, radius = 0.175f, knobRadius = 0.08f),
            label = "R",
        ),

        // Shoulders along the top edge, triggers outermost.
        ControlSpec(
            id = ControlId.Trigger(Side.LEFT),
            shape = ControlSpec.Shape.Rect(0.09f, 0.08f, width = 0.12f, height = 0.11f),
            label = "L2",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.L1),
            shape = ControlSpec.Shape.Rect(0.23f, 0.08f, width = 0.12f, height = 0.11f),
            label = "L1",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.R1),
            shape = ControlSpec.Shape.Rect(0.77f, 0.08f, width = 0.12f, height = 0.11f),
            label = "R1",
        ),
        ControlSpec(
            id = ControlId.Trigger(Side.RIGHT),
            shape = ControlSpec.Shape.Rect(0.91f, 0.08f, width = 0.12f, height = 0.11f),
            label = "R2",
        ),

        // View and Options high and wide apart, inboard of each cluster; Steam alone, low.
        //
        // Bigger than the centre buttons on the other plates, because Valve's glyphs for these
        // three are wide pills rather than discs, and a glyph is drawn square at the smaller of the
        // control's two extents -- so a pill in a circle's box renders at a fraction of the height
        // it has room for. The touch area grows with it, which for buttons this far from a thumb is
        // no loss.
        ControlSpec(
            id = ControlId.Button(GamepadButton.BACK),
            shape = ControlSpec.Shape.Circle(0.36f, 0.20f, radius = 0.075f),
            label = "⧉",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.START),
            shape = ControlSpec.Shape.Circle(0.64f, 0.20f, radius = 0.075f),
            label = "☰",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.GUIDE),
            shape = ControlSpec.Shape.Circle(0.50f, 0.66f, radius = 0.085f),
            label = "STEAM",
        ),

        // Stick clicks along the bottom, in the gap the two raised clusters leave behind.
        ControlSpec(
            id = ControlId.Button(GamepadButton.L3),
            shape = ControlSpec.Shape.Circle(0.44f, 0.90f, radius = 0.055f),
            label = "L3",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.R3),
            shape = ControlSpec.Shape.Circle(0.56f, 0.90f, radius = 0.055f),
            label = "R3",
        ),
    ),
)
