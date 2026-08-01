package com.blugaemand.input.layouts

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.ControlId
import com.blugaemand.input.ControlId.Side
import com.blugaemand.input.ControlSpec
import com.blugaemand.input.GamepadLayout
import com.blugaemand.input.LayoutStyle
import com.blugaemand.input.art.SWITCH_ART

/**
 * A Nintendo Switch Pro Controller.
 *
 * **Authored in full rather than derived from [DEFAULT_LAYOUT]**, unlike [XBOX_LAYOUT] — the point
 * of this plate is that a Pro Controller is not an Xbox pad with different letters on it, and a
 * derived geometry would make it exactly that.
 *
 * What actually distinguishes it, and what is tuned here:
 *
 * - **Both lower controls lean inboard.** The Pro Controller's D-pad and right stick sit noticeably
 *   closer to the middle than the stick and diamond above them, so the two clusters splay outwards
 *   as they rise. That inward lean is the single most recognisable thing about the pad's front, and
 *   it is what an Xbox-derived layout gets wrong.
 * - **The D-pad is small.** Nintendo's is a modest cross well inboard, not the slab a DualSense
 *   carries — see [PS5_LAYOUT] for the other extreme.
 * - **Minus and Plus sit high and wide apart**, flanking the middle rather than queuing beside the
 *   guide button, with Home centred below them. That is the Pro Controller's own arrangement: the
 *   two shoulder-height buttons are a pair, and Home is not one of them.
 * - **The stick clicks drop to the bottom edge.** A Pro Controller has no L3/R3 buttons at all —
 *   you press the sticks — so there is no authentic place for them. Out of the way, below the
 *   space the inboard right stick now occupies, is the next best thing.
 */
val SWITCH_LAYOUT: GamepadLayout = GamepadLayout(
    id = "switch",
    name = "Switch",
    style = LayoutStyle.Images(SWITCH_ART),
    controls = listOf(
        // Left hand: stick high and outboard, D-pad low and pulled in towards the middle.
        ControlSpec(
            id = ControlId.Stick(Side.LEFT),
            shape = ControlSpec.Shape.Stick(0.15f, 0.40f, radius = 0.20f, knobRadius = 0.09f),
            label = "L",
        ),
        ControlSpec(
            id = ControlId.Dpad,
            shape = ControlSpec.Shape.Dpad(0.28f, 0.80f, radius = 0.115f),
        ),

        // Right hand: the diamond where every plate has it, the stick pulled in to match the
        // D-pad. The north and west keys drive each other's slot, as everywhere else -- and on
        // this plate the letters swap as well; see SWITCH_ART for the whole table.
        ControlSpec(
            id = ControlId.Button(GamepadButton.WEST),
            shape = ControlSpec.Shape.Circle(0.870f, 0.295f, radius = 0.072f),
            label = "Y",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.NORTH),
            shape = ControlSpec.Shape.Circle(0.812f, 0.420f, radius = 0.072f),
            label = "X",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.EAST),
            shape = ControlSpec.Shape.Circle(0.928f, 0.420f, radius = 0.072f),
            label = "B",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.SOUTH),
            shape = ControlSpec.Shape.Circle(0.870f, 0.545f, radius = 0.072f),
            label = "A",
        ),
        ControlSpec(
            id = ControlId.Stick(Side.RIGHT),
            shape = ControlSpec.Shape.Stick(0.68f, 0.80f, radius = 0.18f, knobRadius = 0.08f),
            label = "R",
        ),

        // Shoulders along the top edge, triggers outermost, as on every plate.
        ControlSpec(
            id = ControlId.Trigger(Side.LEFT),
            shape = ControlSpec.Shape.Rect(0.09f, 0.08f, width = 0.12f, height = 0.11f),
            label = "ZL",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.L1),
            shape = ControlSpec.Shape.Rect(0.23f, 0.08f, width = 0.12f, height = 0.11f),
            label = "L",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.R1),
            shape = ControlSpec.Shape.Rect(0.77f, 0.08f, width = 0.12f, height = 0.11f),
            label = "R",
        ),
        ControlSpec(
            id = ControlId.Trigger(Side.RIGHT),
            shape = ControlSpec.Shape.Rect(0.91f, 0.08f, width = 0.12f, height = 0.11f),
            label = "ZR",
        ),

        // Minus and Plus as a high, wide pair; Home centred beneath them.
        ControlSpec(
            id = ControlId.Button(GamepadButton.BACK),
            shape = ControlSpec.Shape.Circle(0.41f, 0.27f, radius = 0.055f),
            label = "−",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.START),
            shape = ControlSpec.Shape.Circle(0.59f, 0.27f, radius = 0.055f),
            label = "+",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.GUIDE),
            shape = ControlSpec.Shape.Circle(0.50f, 0.44f, radius = 0.06f),
            label = "⌂",
        ),

        // Stick clicks along the bottom, between the two inboard clusters.
        ControlSpec(
            id = ControlId.Button(GamepadButton.L3),
            shape = ControlSpec.Shape.Circle(0.42f, 0.90f, radius = 0.055f),
            label = "L3",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.R3),
            shape = ControlSpec.Shape.Circle(0.52f, 0.90f, radius = 0.055f),
            label = "R3",
        ),
    ),
)
