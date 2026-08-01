package com.blugaemand.input.layouts

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.ControlId
import com.blugaemand.input.ControlId.Side
import com.blugaemand.input.ControlSpec
import com.blugaemand.input.GamepadLayout
import com.blugaemand.input.LayoutStyle
import com.blugaemand.input.art.PLAYSTATION_ART

/**
 * The D-pad where a DualSense has it: opposite the face diamond rather than tucked below the stick.
 * `0.13` is `0.870` reflected, so the two clusters sit the same distance in from their edges.
 *
 * Considerably bigger than the shared one, because it is the only control a thumb sweeps across
 * rather than lands on, and it inherits the room the left stick gives up by moving down. The height
 * is what pays for the size: at the diamond's own `0.42` this radius would put the D-pad's touch
 * square into the stick's, so it sits `0.02` higher, which is where a DualSense's D-pad is anyway.
 * The drawn cross clears the stick well by much more than that suggests — the square is the touch
 * area, and the arm tips are the only part of it near the stick.
 */
private val PS5_DPAD = ControlSpec.Shape.Dpad(0.13f, 0.40f, radius = 0.21f)

/**
 * The left stick mirroring the right one exactly — same place, same size — which is what makes the
 * arrangement read as a PlayStation pad rather than an Xbox one with different pictures on it.
 *
 * [DEFAULT_LAYOUT] gives its left stick the larger radius because it sits alone under the thumb up
 * there; down here the two are a symmetric pair and a mismatched one would just look wrong.
 */
private val PS5_LEFT_STICK =
    ControlSpec.Shape.Stick(0.22f, 0.80f, radius = 0.18f, knobRadius = 0.08f)

/**
 * [DEFAULT_LAYOUT] drawn with Kenney's PlayStation input prompts — the DualSense face symbols,
 * L1/L2/R1/R2, and the Create and Options buttons — and laid out the way a DualSense is.
 *
 * Everything but the left cluster is still derived rather than copied, as in [XBOX_LAYOUT], so a
 * position tuned there moves on every built-in. The left cluster is the deliberate exception:
 * PlayStation has never used the offset arrangement the default and Xbox plates share, and a pad
 * that draws PlayStation buttons in Xbox positions is the wrong pad for the muscle memory that
 * comes with those symbols. The D-pad and the left stick swap ends, and both are restated in full
 * rather than nudged, because a swap expressed as deltas is unreadable the first time it changes.
 */
val PS5_LAYOUT: GamepadLayout = DEFAULT_LAYOUT.copy(
    id = "ps5",
    name = "PS5",
    style = LayoutStyle.Images(PLAYSTATION_ART),
    controls = DEFAULT_LAYOUT.controls.map { spec ->
        when (spec.id) {
            ControlId.Dpad -> spec.copy(shape = PS5_DPAD)
            ControlId.Stick(Side.LEFT) -> spec.copy(shape = PS5_LEFT_STICK)
            // The pack has no picture of the PS button, so that one control falls back to its drawn
            // shape and label. The default layout's house means home, which is right but generic;
            // on a PlayStation face plate the button says PS.
            ControlId.Button(GamepadButton.GUIDE) -> spec.copy(label = "PS")
            else -> spec
        }
    },
)
