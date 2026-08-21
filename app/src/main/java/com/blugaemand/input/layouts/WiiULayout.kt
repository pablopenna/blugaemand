package com.blugaemand.input.layouts

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.ControlId
import com.blugaemand.input.ControlId.Side
import com.blugaemand.input.ControlSpec
import com.blugaemand.input.GamepadLayout
import com.blugaemand.input.LayoutStyle
import com.blugaemand.input.art.WII_U_ART

/**
 * A Wii U Pro Controller.
 *
 * **Authored in full**, like [SWITCH_LAYOUT] and [STEAM_DECK_LAYOUT], because the one thing this
 * pad is remembered for is an arrangement no other controller here uses:
 *
 * - **Both sticks sit above both button clusters.** Every other plate in the catalog puts a stick
 *   opposite a cluster on each side — the D-pad under the left stick, the diamond above the right
 *   one, or the mirror of that. The Wii U Pro puts the two sticks in a row along the top and the
 *   D-pad and the diamond in a row underneath, which is why it looks wrong to anyone expecting a
 *   Switch pad and exactly right to anyone who owned one.
 * - **The sticks are level with each other**, not splayed. That falls out of the row, and it is the
 *   detail that makes the difference visible at a glance rather than only when reaching for one.
 * - **The D-pad is a plain small cross** low on the left, where the Switch's leans inboard and the
 *   DualSense's is a slab; see [PS5_LAYOUT] for that other extreme.
 *
 * The face letters are Nintendo's, crossed both ways against the slots that report them — the key
 * drawn A sends what an Xbox pad's B sends. [com.blugaemand.input.art.SWITCH_ART] has the table;
 * this pad is where that crossing came from.
 *
 * The stick clicks keep the bottom-centre spot the other Nintendo plate gives them. A Wii U Pro
 * does have clickable sticks, unlike a GameCube, but Kenney draws no pressed stick for this pack,
 * so the two fall back to their labels rather than borrowing a picture that says nothing about
 * being clicked.
 */
val WII_U_LAYOUT: GamepadLayout = GamepadLayout(
    id = "wiiu",
    name = "Wii U",
    style = LayoutStyle.Images(WII_U_ART),
    controls = listOf(
        // The row that defines the pad: two sticks, level, one under each thumb's resting arc.
        ControlSpec(
            id = ControlId.Stick(Side.LEFT),
            shape = ControlSpec.Shape.Stick(0.155f, 0.36f, radius = 0.175f, knobRadius = 0.08f),
            label = "L",
        ),
        ControlSpec(
            id = ControlId.Stick(Side.RIGHT),
            shape = ControlSpec.Shape.Stick(0.845f, 0.36f, radius = 0.175f, knobRadius = 0.08f),
            label = "R",
        ),

        // And the row underneath it: cross on the left, diamond on the right.
        ControlSpec(
            id = ControlId.Dpad,
            shape = ControlSpec.Shape.Dpad(0.20f, 0.78f, radius = 0.12f),
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.WEST),
            shape = ControlSpec.Shape.Circle(0.845f, 0.655f, radius = 0.065f),
            label = "Y",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.NORTH),
            shape = ControlSpec.Shape.Circle(0.787f, 0.775f, radius = 0.065f),
            label = "X",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.EAST),
            shape = ControlSpec.Shape.Circle(0.903f, 0.775f, radius = 0.065f),
            label = "B",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.SOUTH),
            shape = ControlSpec.Shape.Circle(0.845f, 0.895f, radius = 0.065f),
            label = "A",
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

        // Minus and Plus flanking the middle, Home centred below them -- the Pro Controller
        // inherited this arrangement from here.
        ControlSpec(
            id = ControlId.Button(GamepadButton.BACK),
            shape = ControlSpec.Shape.Circle(0.41f, 0.30f, radius = 0.055f),
            label = "−",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.START),
            shape = ControlSpec.Shape.Circle(0.59f, 0.30f, radius = 0.055f),
            label = "+",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.GUIDE),
            shape = ControlSpec.Shape.Circle(0.50f, 0.46f, radius = 0.06f),
            label = "⌂",
        ),

        // Stick clicks along the bottom, clear of both lower clusters.
        ControlSpec(
            id = ControlId.Button(GamepadButton.L3),
            shape = ControlSpec.Shape.Circle(0.44f, 0.88f, radius = 0.055f),
            label = "L3",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.R3),
            shape = ControlSpec.Shape.Circle(0.56f, 0.88f, radius = 0.055f),
            label = "R3",
        ),
    ),
)
