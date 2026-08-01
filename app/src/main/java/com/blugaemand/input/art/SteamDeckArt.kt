package com.blugaemand.input.art

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.ArtPack
import com.blugaemand.input.ControlIcon
import com.blugaemand.input.ControlId
import com.blugaemand.input.ControlId.Side
import com.blugaemand.input.Glyph

/**
 * Kenney's Steam Deck input prompts.
 *
 * **The face plate is the Xbox one**: Valve kept ABXY where Microsoft has them, so the crossing here
 * is the same as [XBOX_ART]'s — the key drawn Y sits on [GamepadButton.WEST], the top of the
 * diamond, because that is the slot a host reports as Y. What differs from Xbox is everything
 * around it: the shoulders are numbered rather than lettered, the centre buttons are View and
 * Options, and the guide button is Steam's.
 *
 * The Deck's four back paddles and two trackpads have no glyph here, because they have no control
 * to draw on — the pad exposes a standard HID gamepad, and neither has a place in it.
 */
val STEAM_DECK_ART: ArtPack = ArtPack(
    id = "steamdeck",
    glyphs = mapOf(
        // Face buttons — same arrangement as Xbox, so the same slots.
        ControlId.Button(GamepadButton.WEST) to
            Glyph(ControlIcon.DECK_Y, ControlIcon.DECK_Y_PRESSED),
        ControlId.Button(GamepadButton.NORTH) to
            Glyph(ControlIcon.DECK_X, ControlIcon.DECK_X_PRESSED),
        ControlId.Button(GamepadButton.EAST) to
            Glyph(ControlIcon.DECK_B, ControlIcon.DECK_B_PRESSED),
        ControlId.Button(GamepadButton.SOUTH) to
            Glyph(ControlIcon.DECK_A, ControlIcon.DECK_A_PRESSED),

        // Shoulders and triggers, numbered as PlayStation does rather than lettered as Xbox does.
        ControlId.Trigger(Side.LEFT) to Glyph(ControlIcon.DECK_L2, ControlIcon.DECK_L2_PRESSED),
        ControlId.Button(GamepadButton.L1) to Glyph(ControlIcon.DECK_L1, ControlIcon.DECK_L1_PRESSED),
        ControlId.Button(GamepadButton.R1) to Glyph(ControlIcon.DECK_R1, ControlIcon.DECK_R1_PRESSED),
        ControlId.Trigger(Side.RIGHT) to Glyph(ControlIcon.DECK_R2, ControlIcon.DECK_R2_PRESSED),

        // Centre cluster. View and Options are the Deck's Back and Start; the Steam button is its
        // guide, and Kenney draws it, so nothing here falls back to a shape.
        ControlId.Button(GamepadButton.BACK) to
            Glyph(ControlIcon.DECK_VIEW, ControlIcon.DECK_VIEW_PRESSED),
        ControlId.Button(GamepadButton.START) to
            Glyph(ControlIcon.DECK_OPTIONS, ControlIcon.DECK_OPTIONS_PRESSED),
        ControlId.Button(GamepadButton.GUIDE) to
            Glyph(ControlIcon.DECK_STEAM, ControlIcon.DECK_STEAM_PRESSED),

        // Stick clicks, as the stick and the stick pressed; the pack has no button for them.
        ControlId.Button(GamepadButton.L3) to Glyph(ControlIcon.DECK_LS, ControlIcon.DECK_LS_PRESSED),
        ControlId.Button(GamepadButton.R3) to Glyph(ControlIcon.DECK_RS, ControlIcon.DECK_RS_PRESSED),

        ControlId.Dpad to Glyph(ControlIcon.DECK_DPAD, ControlIcon.DECK_DPAD_PRESSED),
    ),
)
