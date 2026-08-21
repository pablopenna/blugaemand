package com.blugaemand.input.art

import com.blugaemand.hid.GamepadButton
import com.blugaemand.hid.Hat
import com.blugaemand.input.ArtPack
import com.blugaemand.input.ControlIcon
import com.blugaemand.input.ControlId
import com.blugaemand.input.ControlId.Side
import com.blugaemand.input.Glyph

/**
 * Kenney's Nintendo Switch input prompts, arranged the way a Pro Controller is.
 *
 * **This is the pack where the face-button crossing bites hardest**, because Nintendo swaps *both*
 * pairs rather than one. Every pack keys its glyph to the position on the diamond, not to the name
 * of the slot — see [XBOX_ART] — and the positions are fixed by what a host reports: HID's legacy
 * aliases make [GamepadButton.WEST] the top of the diamond and [GamepadButton.NORTH] its left.
 *
 * So on top of that, Nintendo puts **X** where Xbox puts Y, and **Y** where Xbox puts X; **A** on
 * the right where Xbox has B, and **B** at the bottom where Xbox has A:
 *
 * | Position | Slot | Xbox | Nintendo |
 * |---|---|---|---|
 * | top | `WEST` | Y | **X** |
 * | left | `NORTH` | X | **Y** |
 * | right | `EAST` | B | **A** |
 * | bottom | `SOUTH` | A | **B** |
 *
 * The consequence is worth stating plainly: **the key drawn A sends the same button an Xbox pad's B
 * sends.** That is not a bug to be fixed here — it is the swap every Switch owner already lives
 * with, and matching the printed letter instead would move the button under the thumb rather than
 * the picture on it. `LayoutArtTest` walks the whole diamond for every pack, so this cannot drift.
 *
 * Unlike [PLAYSTATION_ART] there is a Home glyph, so nothing falls back to a drawn shape but the
 * sticks — and those stay drawn in every pack, because no static picture shows a knob off centre.
 */
val SWITCH_ART: ArtPack = ArtPack(
    id = "switch",
    name = "Switch",
    glyphs = mapOf(
        // Face buttons — by position, and both pairs swapped relative to Xbox. See above.
        ControlId.Button(GamepadButton.WEST) to
            Glyph(ControlIcon.SWITCH_X, ControlIcon.SWITCH_X_PRESSED),
        ControlId.Button(GamepadButton.NORTH) to
            Glyph(ControlIcon.SWITCH_Y, ControlIcon.SWITCH_Y_PRESSED),
        ControlId.Button(GamepadButton.EAST) to
            Glyph(ControlIcon.SWITCH_A, ControlIcon.SWITCH_A_PRESSED),
        ControlId.Button(GamepadButton.SOUTH) to
            Glyph(ControlIcon.SWITCH_B, ControlIcon.SWITCH_B_PRESSED),

        // Shoulders and triggers. Nintendo calls the bumpers L and R and the triggers ZL and ZR.
        ControlId.Trigger(Side.LEFT) to Glyph(ControlIcon.SWITCH_ZL, ControlIcon.SWITCH_ZL_PRESSED),
        ControlId.Button(GamepadButton.L1) to
            Glyph(ControlIcon.SWITCH_L, ControlIcon.SWITCH_L_PRESSED),
        ControlId.Button(GamepadButton.R1) to
            Glyph(ControlIcon.SWITCH_R, ControlIcon.SWITCH_R_PRESSED),
        ControlId.Trigger(Side.RIGHT) to Glyph(ControlIcon.SWITCH_ZR, ControlIcon.SWITCH_ZR_PRESSED),

        // Centre cluster. Minus and Plus are Nintendo's Back and Start; Home is its guide button,
        // and unlike Sony's, Kenney draws it.
        ControlId.Button(GamepadButton.BACK) to
            Glyph(ControlIcon.SWITCH_MINUS, ControlIcon.SWITCH_MINUS_PRESSED),
        ControlId.Button(GamepadButton.START) to
            Glyph(ControlIcon.SWITCH_PLUS, ControlIcon.SWITCH_PLUS_PRESSED),
        ControlId.Button(GamepadButton.GUIDE) to
            Glyph(ControlIcon.SWITCH_HOME, ControlIcon.SWITCH_HOME_PRESSED),

        // Stick clicks. The pack has no button for these, so it is the stick and the stick pressed.
        ControlId.Button(GamepadButton.L3) to
            Glyph(ControlIcon.SWITCH_LS, ControlIcon.SWITCH_LS_PRESSED),
        ControlId.Button(GamepadButton.R3) to
            Glyph(ControlIcon.SWITCH_RS, ControlIcon.SWITCH_RS_PRESSED),

        // The cross at rest and the cross lit whole; the arm being pushed is dpadArms below.
        ControlId.Dpad to Glyph(ControlIcon.SWITCH_DPAD, ControlIcon.SWITCH_DPAD_PRESSED),
    ),
    // Each of these is the whole cross with one arm lit -- see ArtPack.dpadArms. The four
    // cardinals are all Kenney draws, so a diagonal falls back to the cross lit whole.
    dpadArms = mapOf(
        Hat.NORTH to ControlIcon.SWITCH_DPAD_UP,
        Hat.SOUTH to ControlIcon.SWITCH_DPAD_DOWN,
        Hat.WEST to ControlIcon.SWITCH_DPAD_LEFT,
        Hat.EAST to ControlIcon.SWITCH_DPAD_RIGHT,
    ),
)
