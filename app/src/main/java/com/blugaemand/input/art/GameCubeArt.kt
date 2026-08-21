package com.blugaemand.input.art

import com.blugaemand.hid.GamepadButton
import com.blugaemand.hid.Hat
import com.blugaemand.input.ArtPack
import com.blugaemand.input.ControlIcon
import com.blugaemand.input.ControlId
import com.blugaemand.input.ControlId.Side
import com.blugaemand.input.Glyph

/**
 * Kenney's Nintendo GameCube prompts.
 *
 * **The one Nintendo plate where the letters are not crossed.** [SWITCH_ART] swaps both face pairs
 * relative to Xbox; the GameCube's letters land on the slots that report them, the way
 * [STEAM_DECK_ART]'s do — A is the bottom key a host calls A, and X and Y sit on the slots that
 * report X and Y. What the GameCube moves is not the letters but the *shapes*: A is a large disc in
 * the middle with B beside it and X and Y as kidneys curled around the outside, which is
 * [com.blugaemand.input.layouts.GAMECUBE_LAYOUT]'s business rather than this file's.
 *
 * **A and B are the only coloured keys**, green and red, so those two take a colour-fill pressed
 * picture the way [XBOX_ART]'s do, while the grey X and Y take a solid white one like every
 * Nintendo pack since. That is Kenney's split, not one imposed here.
 *
 * **Five controls have no picture, and none of them existed in 2001.** The GameCube has no Select,
 * no Home, no clickable sticks, and only three shoulder buttons — L, R and Z — for the four slots
 * the profile declares. Each of those falls back to its drawn shape and label, which is the
 * treatment [PLAYSTATION_ART] already gives the PS button; the layout is what gives them labels
 * worth reading. Lending them another button's picture would put the wrong prompt under the thumb,
 * which is the whole reason a pack is allowed to be incomplete.
 */
val GAMECUBE_ART: ArtPack = ArtPack(
    id = "gamecube",
    name = "GameCube",
    glyphs = mapOf(
        // Face keys by position, uncrossed: the printed letter and the reported letter agree.
        ControlId.Button(GamepadButton.WEST) to
            Glyph(ControlIcon.GAMECUBE_Y, ControlIcon.GAMECUBE_Y_PRESSED),
        ControlId.Button(GamepadButton.NORTH) to
            Glyph(ControlIcon.GAMECUBE_X, ControlIcon.GAMECUBE_X_PRESSED),
        ControlId.Button(GamepadButton.EAST) to
            Glyph(ControlIcon.GAMECUBE_B, ControlIcon.GAMECUBE_B_PRESSED),
        ControlId.Button(GamepadButton.SOUTH) to
            Glyph(ControlIcon.GAMECUBE_A, ControlIcon.GAMECUBE_A_PRESSED),

        // L and R are the analog pulls, so they take the trigger slots. Z is the extra shoulder and
        // sits above R on the pad, so it takes the right bumper; nothing is left for the left one.
        ControlId.Trigger(Side.LEFT) to
            Glyph(ControlIcon.GAMECUBE_L, ControlIcon.GAMECUBE_L_PRESSED),
        ControlId.Trigger(Side.RIGHT) to
            Glyph(ControlIcon.GAMECUBE_R, ControlIcon.GAMECUBE_R_PRESSED),
        ControlId.Button(GamepadButton.R1) to
            Glyph(ControlIcon.GAMECUBE_Z, ControlIcon.GAMECUBE_Z_PRESSED),

        // Start is the whole centre cluster on this pad. Back and Home have no picture because the
        // controller has no such button.
        ControlId.Button(GamepadButton.START) to
            Glyph(ControlIcon.GAMECUBE_START, ControlIcon.GAMECUBE_START_PRESSED),

        // The cross at rest and the cross lit whole; the arm being pushed is dpadArms below.
        ControlId.Dpad to Glyph(ControlIcon.GAMECUBE_DPAD, ControlIcon.GAMECUBE_DPAD_PRESSED),
    ),
    // Each of these is the whole cross with one arm lit -- see ArtPack.dpadArms. The four
    // cardinals are all Kenney draws, so a diagonal falls back to the cross lit whole.
    dpadArms = mapOf(
        Hat.NORTH to ControlIcon.GAMECUBE_DPAD_UP,
        Hat.SOUTH to ControlIcon.GAMECUBE_DPAD_DOWN,
        Hat.WEST to ControlIcon.GAMECUBE_DPAD_LEFT,
        Hat.EAST to ControlIcon.GAMECUBE_DPAD_RIGHT,
    ),
)
