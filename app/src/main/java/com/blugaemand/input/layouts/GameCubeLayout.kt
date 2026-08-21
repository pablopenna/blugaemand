package com.blugaemand.input.layouts

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.ControlId
import com.blugaemand.input.ControlId.Side
import com.blugaemand.input.ControlSpec
import com.blugaemand.input.GamepadLayout
import com.blugaemand.input.LayoutStyle
import com.blugaemand.input.art.GAMECUBE_ART

/**
 * A Nintendo GameCube controller.
 *
 * **The only plate in the catalog whose face buttons are not a diamond**, which is the entire
 * reason it is worth shipping and why it is authored in full:
 *
 * - **A is large and central**, and everything else orbits it. Nintendo made the button you press
 *   most the one you cannot miss, and a GameCube plate that draws four equal circles is not a
 *   GameCube plate.
 * - **B sits low and to the left of A, X to the right, Y above.** Three satellites around one hub,
 *   not four points of a compass. The satellites are drawn smaller than A here for the same reason
 *   they are smaller on the pad.
 * - **The C-stick is under the face cluster**, where the right stick on every other plate is a peer
 *   of the left one. On a GameCube it is a small yellow nub in its own space, and it is the second
 *   most recognisable thing about the front.
 *
 * The letters are **not crossed**: unlike every other Nintendo pad here, a GameCube's printed
 * letters land on the slots that report them, so the key drawn A really does send A. See
 * [GAMECUBE_ART], and [com.blugaemand.input.art.SWITCH_ART] for the pads where that stops being
 * true.
 *
 * **Five controls draw as plain shapes because the pad has no such button.** The GameCube predates
 * Select, Home and clickable sticks entirely, and its shoulder row is three buttons — L, R and
 * Z — for the four slots the profile declares. Those five keep neutral labels: `L1` is the HID name
 * for the shoulder slot the pad does not fill, and Back, Home and the stick clicks keep the
 * vocabulary [DEFAULT_LAYOUT] uses. Giving any of them a borrowed prompt would put another
 * button's picture under the thumb.
 */
val GAMECUBE_LAYOUT: GamepadLayout = GamepadLayout(
    id = "gamecube",
    name = "GameCube",
    style = LayoutStyle.Images(GAMECUBE_ART),
    controls = listOf(
        // Left hand: the big stick high, the small cross low and pulled in towards the middle.
        ControlSpec(
            id = ControlId.Stick(Side.LEFT),
            shape = ControlSpec.Shape.Stick(0.155f, 0.38f, radius = 0.185f, knobRadius = 0.085f),
            label = "L",
        ),
        ControlSpec(
            id = ControlId.Dpad,
            shape = ControlSpec.Shape.Dpad(0.30f, 0.82f, radius = 0.105f),
        ),

        // The hub and its three satellites. A is on SOUTH, which is the slot a host reports as A,
        // so for once the picture and the letter and the slot all agree.
        ControlSpec(
            id = ControlId.Button(GamepadButton.SOUTH),
            shape = ControlSpec.Shape.Circle(0.855f, 0.46f, radius = 0.095f),
            label = "A",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.EAST),
            shape = ControlSpec.Shape.Circle(0.755f, 0.545f, radius = 0.058f),
            label = "B",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.NORTH),
            shape = ControlSpec.Shape.Circle(0.955f, 0.40f, radius = 0.058f),
            label = "X",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.WEST),
            shape = ControlSpec.Shape.Circle(0.815f, 0.295f, radius = 0.058f),
            label = "Y",
        ),

        // The C-stick, below the cluster and smaller than the one under the left thumb.
        ControlSpec(
            id = ControlId.Stick(Side.RIGHT),
            shape = ControlSpec.Shape.Stick(0.80f, 0.80f, radius = 0.155f, knobRadius = 0.075f),
            label = "C",
        ),

        // Shoulders along the top edge. The analog pulls sit outermost as on every plate, with Z
        // inboard of R where the pad has it -- and nothing inboard of L, where the pad has nothing.
        ControlSpec(
            id = ControlId.Trigger(Side.LEFT),
            shape = ControlSpec.Shape.Rect(0.09f, 0.08f, width = 0.12f, height = 0.11f),
            label = "L",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.L1),
            shape = ControlSpec.Shape.Rect(0.23f, 0.08f, width = 0.12f, height = 0.11f),
            label = "L1",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.R1),
            shape = ControlSpec.Shape.Rect(0.77f, 0.08f, width = 0.12f, height = 0.11f),
            label = "Z",
        ),
        ControlSpec(
            id = ControlId.Trigger(Side.RIGHT),
            shape = ControlSpec.Shape.Rect(0.91f, 0.08f, width = 0.12f, height = 0.11f),
            label = "R",
        ),

        // Start alone in the middle, which is the whole centre cluster on a GameCube. Back and
        // Home go under it, drawn as shapes, because the pad has neither.
        ControlSpec(
            id = ControlId.Button(GamepadButton.START),
            shape = ControlSpec.Shape.Circle(0.50f, 0.28f, radius = 0.06f),
            label = "START",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.BACK),
            shape = ControlSpec.Shape.Circle(0.42f, 0.44f, radius = 0.052f),
            label = "◀◀",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.GUIDE),
            shape = ControlSpec.Shape.Circle(0.58f, 0.44f, radius = 0.052f),
            label = "⌂",
        ),

        // Stick clicks along the bottom. A GameCube's sticks do not click at all, so there is no
        // authentic place for these -- out of the way is the next best thing, as on SWITCH_LAYOUT.
        ControlSpec(
            id = ControlId.Button(GamepadButton.L3),
            shape = ControlSpec.Shape.Circle(0.46f, 0.88f, radius = 0.052f),
            label = "L3",
        ),
        ControlSpec(
            id = ControlId.Button(GamepadButton.R3),
            shape = ControlSpec.Shape.Circle(0.56f, 0.88f, radius = 0.052f),
            label = "R3",
        ),
    ),
)
