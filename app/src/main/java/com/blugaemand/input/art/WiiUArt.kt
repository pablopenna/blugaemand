package com.blugaemand.input.art

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.ArtPack
import com.blugaemand.input.ControlIcon
import com.blugaemand.input.ControlId
import com.blugaemand.input.ControlId.Side
import com.blugaemand.input.Glyph

/**
 * Kenney's Nintendo Wii U prompts, arranged the way a Wii U Pro Controller is.
 *
 * **The face crossing is [SWITCH_ART]'s exactly**, because this is the pad the Pro Controller
 * inherited it from: X on top where Xbox puts Y, Y on the left where Xbox puts X, A on the right
 * and B at the bottom. The table in [SWITCH_ART] reads the same for this pack, and
 * `LayoutArtTest` walks both.
 *
 * Nintendo's shoulder naming is the same too — L and R for the bumpers, ZL and ZR for the
 * triggers — so the four slots fill exactly as they do on a Switch plate, with the trigger slots
 * taking Z.
 *
 * **The stick clicks have no picture**, which is the only gap. Kenney draws a Wii U stick but no
 * pressed one, where the Switch and Steam Deck packs both ship the pair that
 * [com.blugaemand.input.ControlIcon] describes; rather than lend them a picture of an unpressed
 * stick, which would say nothing about being clicked, both fall back to their drawn shape and
 * label. The sticks themselves are drawn in every pack anyway — no static picture shows a knob off
 * centre.
 */
val WII_U_ART: ArtPack = ArtPack(
    id = "wiiu",
    name = "Wii U",
    glyphs = mapOf(
        // Face keys by position, both pairs crossed relative to Xbox. See SWITCH_ART for the table.
        ControlId.Button(GamepadButton.WEST) to
            Glyph(ControlIcon.WIIU_X, ControlIcon.WIIU_X_PRESSED),
        ControlId.Button(GamepadButton.NORTH) to
            Glyph(ControlIcon.WIIU_Y, ControlIcon.WIIU_Y_PRESSED),
        ControlId.Button(GamepadButton.EAST) to
            Glyph(ControlIcon.WIIU_A, ControlIcon.WIIU_A_PRESSED),
        ControlId.Button(GamepadButton.SOUTH) to
            Glyph(ControlIcon.WIIU_B, ControlIcon.WIIU_B_PRESSED),

        // Shoulders and triggers, named as Nintendo names them.
        ControlId.Trigger(Side.LEFT) to Glyph(ControlIcon.WIIU_ZL, ControlIcon.WIIU_ZL_PRESSED),
        ControlId.Button(GamepadButton.L1) to
            Glyph(ControlIcon.WIIU_L, ControlIcon.WIIU_L_PRESSED),
        ControlId.Button(GamepadButton.R1) to
            Glyph(ControlIcon.WIIU_R, ControlIcon.WIIU_R_PRESSED),
        ControlId.Trigger(Side.RIGHT) to Glyph(ControlIcon.WIIU_ZR, ControlIcon.WIIU_ZR_PRESSED),

        // Centre cluster. Minus and Plus are Nintendo's Back and Start, as on a Switch.
        ControlId.Button(GamepadButton.BACK) to
            Glyph(ControlIcon.WIIU_MINUS, ControlIcon.WIIU_MINUS_PRESSED),
        ControlId.Button(GamepadButton.START) to
            Glyph(ControlIcon.WIIU_PLUS, ControlIcon.WIIU_PLUS_PRESSED),
        ControlId.Button(GamepadButton.GUIDE) to
            Glyph(ControlIcon.WIIU_HOME, ControlIcon.WIIU_HOME_PRESSED),

        // As on the other plates, the whole cross lights rather than the arm being pushed.
        ControlId.Dpad to Glyph(ControlIcon.WIIU_DPAD, ControlIcon.WIIU_DPAD_PRESSED),
    ),
)
