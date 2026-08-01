package com.blugaemand.input.art

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.ArtPack
import com.blugaemand.input.ControlIcon
import com.blugaemand.input.ControlId
import com.blugaemand.input.ControlId.Side
import com.blugaemand.input.Glyph

/**
 * Kenney's PlayStation input prompts, in their DualSense arrangement: the shared face symbols and
 * L1/L2/R1/R2, plus the Create and Options buttons the PS5 pad has where earlier ones had Share and
 * Select. As in [XBOX_ART], an outline pairs with a solid counterpart and the face symbols take on
 * their real colours.
 *
 * **The face symbols follow the position on the diamond, not the slot's name.** `DEFAULT_LAYOUT`
 * lays its face buttons out the way an Xbox plate is — the Y key on top, the X key to the left —
 * and puts them on [GamepadButton.WEST] and [GamepadButton.NORTH] respectively, so that hosts
 * reading HID's legacy aliases report the letter printed on the key. A PlayStation plate has
 * triangle on top and square to the left, so triangle lands on `WEST` and square on `NORTH`,
 * exactly where Y and X sit. Pairing square with `WEST` because both are called *west* would put
 * the wrong symbol under the thumb and send the wrong button with it; `LayoutArtTest` guards the
 * whole diamond.
 *
 * **There is no PS button glyph**, because the pack has no picture of one — Kenney draws the Xbox
 * logo but not Sony's. [GamepadButton.GUIDE] is left out rather than lent the mute or touchpad
 * glyph, which would put a different button's picture on the one that sends `GUIDE`; it falls back
 * to its drawn shape, which is what that fallback is for.
 *
 * Sticks are absent for the same reason as in [XBOX_ART].
 */
val PLAYSTATION_ART: ArtPack = ArtPack(
    id = "playstation",
    glyphs = mapOf(
        // Face buttons — by position, see above.
        ControlId.Button(GamepadButton.WEST) to
            Glyph(ControlIcon.PS_TRIANGLE, ControlIcon.PS_TRIANGLE_PRESSED),
        ControlId.Button(GamepadButton.NORTH) to
            Glyph(ControlIcon.PS_SQUARE, ControlIcon.PS_SQUARE_PRESSED),
        ControlId.Button(GamepadButton.SOUTH) to
            Glyph(ControlIcon.PS_CROSS, ControlIcon.PS_CROSS_PRESSED),
        ControlId.Button(GamepadButton.EAST) to
            Glyph(ControlIcon.PS_CIRCLE, ControlIcon.PS_CIRCLE_PRESSED),

        // Shoulders and triggers. PlayStation numbers rather than letters them: the bumpers are L1
        // and R1, the analog triggers L2 and R2.
        ControlId.Trigger(Side.LEFT) to Glyph(ControlIcon.PS_L2, ControlIcon.PS_L2_PRESSED),
        ControlId.Button(GamepadButton.L1) to Glyph(ControlIcon.PS_L1, ControlIcon.PS_L1_PRESSED),
        ControlId.Button(GamepadButton.R1) to Glyph(ControlIcon.PS_R1, ControlIcon.PS_R1_PRESSED),
        ControlId.Trigger(Side.RIGHT) to Glyph(ControlIcon.PS_R2, ControlIcon.PS_R2_PRESSED),

        // Centre cluster, less the PS button. The DualSense renamed Share to Create and kept
        // Options, while the HID buttons keep their older names.
        ControlId.Button(GamepadButton.BACK) to
            Glyph(ControlIcon.PS5_CREATE, ControlIcon.PS5_CREATE_PRESSED),
        ControlId.Button(GamepadButton.START) to
            Glyph(ControlIcon.PS5_OPTIONS, ControlIcon.PS5_OPTIONS_PRESSED),

        // Stick clicks.
        ControlId.Button(GamepadButton.L3) to Glyph(ControlIcon.PS_L3, ControlIcon.PS_L3_PRESSED),
        ControlId.Button(GamepadButton.R3) to Glyph(ControlIcon.PS_R3, ControlIcon.PS_R3_PRESSED),

        // As on the Xbox plate, the whole cross lights up rather than the arm being pushed.
        ControlId.Dpad to Glyph(ControlIcon.PS_DPAD, ControlIcon.PS_DPAD_PRESSED),
    ),
)
