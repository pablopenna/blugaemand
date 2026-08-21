package com.blugaemand.input.art

import com.blugaemand.hid.GamepadButton
import com.blugaemand.hid.Hat
import com.blugaemand.input.ArtPack
import com.blugaemand.input.ControlIcon
import com.blugaemand.input.ControlId
import com.blugaemand.input.ControlId.Side
import com.blugaemand.input.Glyph

/**
 * Kenney's Xbox input prompts. The pack pairs an outline with a solid counterpart, so a press reads
 * as the button filling in; the face buttons additionally take on their real Xbox colours.
 *
 * **The face buttons follow the label, not the slot.** `DEFAULT_LAYOUT` puts its Y key on
 * [GamepadButton.WEST], so that is the control that has to show the *Y* glyph. Guarded by
 * `LayoutArtTest`.
 *
 * Sticks are absent deliberately: the pack has only static pictures of a stick, and nothing in it
 * can show a knob displaced from centre, so they stay drawn in image mode.
 */
val XBOX_ART: ArtPack = ArtPack(
    id = "xbox",
    name = "Xbox",
    glyphs = mapOf(
        // Face buttons — crossed, see above.
        ControlId.Button(GamepadButton.WEST) to
            Glyph(ControlIcon.XBOX_Y, ControlIcon.XBOX_Y_PRESSED),
        ControlId.Button(GamepadButton.NORTH) to
            Glyph(ControlIcon.XBOX_X, ControlIcon.XBOX_X_PRESSED),
        ControlId.Button(GamepadButton.SOUTH) to
            Glyph(ControlIcon.XBOX_A, ControlIcon.XBOX_A_PRESSED),
        ControlId.Button(GamepadButton.EAST) to
            Glyph(ControlIcon.XBOX_B, ControlIcon.XBOX_B_PRESSED),

        // Shoulders and triggers.
        ControlId.Trigger(Side.LEFT) to Glyph(ControlIcon.XBOX_LT, ControlIcon.XBOX_LT_PRESSED),
        ControlId.Button(GamepadButton.L1) to
            Glyph(ControlIcon.XBOX_LB, ControlIcon.XBOX_LB_PRESSED),
        ControlId.Button(GamepadButton.R1) to
            Glyph(ControlIcon.XBOX_RB, ControlIcon.XBOX_RB_PRESSED),
        ControlId.Trigger(Side.RIGHT) to Glyph(ControlIcon.XBOX_RT, ControlIcon.XBOX_RT_PRESSED),

        // Centre cluster. Back is the modern View button and Start the Menu button; the pack names
        // them the way the hardware does, while the HID buttons keep their older names.
        ControlId.Button(GamepadButton.BACK) to
            Glyph(ControlIcon.XBOX_VIEW, ControlIcon.XBOX_VIEW_PRESSED),
        ControlId.Button(GamepadButton.GUIDE) to
            Glyph(ControlIcon.XBOX_GUIDE, ControlIcon.XBOX_GUIDE_PRESSED),
        ControlId.Button(GamepadButton.START) to
            Glyph(ControlIcon.XBOX_MENU, ControlIcon.XBOX_MENU_PRESSED),

        // Stick clicks.
        ControlId.Button(GamepadButton.L3) to
            Glyph(ControlIcon.XBOX_LS, ControlIcon.XBOX_LS_PRESSED),
        ControlId.Button(GamepadButton.R3) to
            Glyph(ControlIcon.XBOX_RS, ControlIcon.XBOX_RS_PRESSED),

        // The whole cross lights up on a press rather than the arm being pushed: the pack has
        // directional glyphs, but the renderer is told only whether the D-pad is held, not which
        // way.
        ControlId.Dpad to Glyph(ControlIcon.XBOX_DPAD, ControlIcon.XBOX_DPAD_PRESSED),
    ),
    // Each of these is the whole cross with one arm lit -- see ArtPack.dpadArms. The four
    // cardinals are all Kenney draws, so a diagonal falls back to the cross lit whole.
    dpadArms = mapOf(
        Hat.NORTH to ControlIcon.XBOX_DPAD_UP,
        Hat.SOUTH to ControlIcon.XBOX_DPAD_DOWN,
        Hat.WEST to ControlIcon.XBOX_DPAD_LEFT,
        Hat.EAST to ControlIcon.XBOX_DPAD_RIGHT,
    ),
)
