package com.blugaemand.input

/**
 * A glyph a control can draw in [LayoutStyle.Images] mode.
 *
 * An enum of names rather than drawable resource IDs on purpose: resource IDs are assigned at build
 * time and are not stable between builds, so a layout that stored one would deserialise into a
 * different picture — or none. The mapping onto actual drawables lives in
 * [com.blugaemand.ui.drawableFor], the one place in the app that knows about `R`.
 *
 * A name identifies a picture, not a role — which control draws it is [ArtPack]'s business. Naming
 * roles instead, a `FACE_TOP` resolved against whichever pack is in play, would drop the
 * per-platform prefixes and cost the ability to name one specific picture, which a layout mixing
 * packs and the planned editor both need.
 *
 * Both sets pair an outline with its solid counterpart, which is the art pack's own idiom.
 *
 * The PlayStation names are split on purpose: `PS_` is the art every PlayStation pad shares, and
 * `PS5_` the two glyphs that are the DualSense's own. A PS4 face plate would reuse the first group
 * and pair it with Share and Options rather than inheriting a controller it does not have.
 *
 * `SWITCH_` and `DECK_` name their own buttons rather than a shared idea of one, which is why
 * `SWITCH_ZL` sits beside `DECK_L2` and `XBOX_LT`: they are three pictures of the same slot, and a
 * layout is free to want a specific one. `SWITCH_LS` and `DECK_LS` are the exception to the
 * outline-and-solid pairing — neither pack draws a stick-click button, so both pair a picture of
 * the stick with a picture of it being pressed, which says the same thing.
 *
 * `SWITCH2_` is the same split as `PS_`/`PS5_`, and a much sharper one: of the 112 pictures Kenney
 * ships for the two Switch generations, 105 are byte-identical files. Only the triggers were
 * redrawn, so the Switch 2 owns exactly four names and
 * [com.blugaemand.input.art.SWITCH2_ART] reuses `SWITCH_` for everything else rather than
 * duplicating a hundred drawables to say the same thing twice.
 */
enum class ControlIcon {
    XBOX_A,
    XBOX_A_PRESSED,
    XBOX_B,
    XBOX_B_PRESSED,
    XBOX_X,
    XBOX_X_PRESSED,
    XBOX_Y,
    XBOX_Y_PRESSED,

    XBOX_LB,
    XBOX_LB_PRESSED,
    XBOX_RB,
    XBOX_RB_PRESSED,
    XBOX_LT,
    XBOX_LT_PRESSED,
    XBOX_RT,
    XBOX_RT_PRESSED,

    XBOX_VIEW,
    XBOX_VIEW_PRESSED,
    XBOX_MENU,
    XBOX_MENU_PRESSED,
    XBOX_GUIDE,
    XBOX_GUIDE_PRESSED,

    XBOX_LS,
    XBOX_LS_PRESSED,
    XBOX_RS,
    XBOX_RS_PRESSED,

    XBOX_DPAD,
    XBOX_DPAD_PRESSED,

    PS_CROSS,
    PS_CROSS_PRESSED,
    PS_CIRCLE,
    PS_CIRCLE_PRESSED,
    PS_SQUARE,
    PS_SQUARE_PRESSED,
    PS_TRIANGLE,
    PS_TRIANGLE_PRESSED,

    PS_L1,
    PS_L1_PRESSED,
    PS_R1,
    PS_R1_PRESSED,
    PS_L2,
    PS_L2_PRESSED,
    PS_R2,
    PS_R2_PRESSED,

    PS5_CREATE,
    PS5_CREATE_PRESSED,
    PS5_OPTIONS,
    PS5_OPTIONS_PRESSED,

    PS_L3,
    PS_L3_PRESSED,
    PS_R3,
    PS_R3_PRESSED,

    PS_DPAD,
    PS_DPAD_PRESSED,

    SWITCH_A,
    SWITCH_A_PRESSED,
    SWITCH_B,
    SWITCH_B_PRESSED,
    SWITCH_X,
    SWITCH_X_PRESSED,
    SWITCH_Y,
    SWITCH_Y_PRESSED,

    SWITCH_L,
    SWITCH_L_PRESSED,
    SWITCH_R,
    SWITCH_R_PRESSED,
    SWITCH_ZL,
    SWITCH_ZL_PRESSED,
    SWITCH_ZR,
    SWITCH_ZR_PRESSED,

    SWITCH_MINUS,
    SWITCH_MINUS_PRESSED,
    SWITCH_PLUS,
    SWITCH_PLUS_PRESSED,
    SWITCH_HOME,
    SWITCH_HOME_PRESSED,

    SWITCH_LS,
    SWITCH_LS_PRESSED,
    SWITCH_RS,
    SWITCH_RS_PRESSED,

    SWITCH_DPAD,
    SWITCH_DPAD_PRESSED,

    DECK_A,
    DECK_A_PRESSED,
    DECK_B,
    DECK_B_PRESSED,
    DECK_X,
    DECK_X_PRESSED,
    DECK_Y,
    DECK_Y_PRESSED,

    DECK_L1,
    DECK_L1_PRESSED,
    DECK_R1,
    DECK_R1_PRESSED,
    DECK_L2,
    DECK_L2_PRESSED,
    DECK_R2,
    DECK_R2_PRESSED,

    DECK_VIEW,
    DECK_VIEW_PRESSED,
    DECK_OPTIONS,
    DECK_OPTIONS_PRESSED,
    DECK_STEAM,
    DECK_STEAM_PRESSED,

    DECK_LS,
    DECK_LS_PRESSED,
    DECK_RS,
    DECK_RS_PRESSED,

    DECK_DPAD,
    DECK_DPAD_PRESSED,

    SWITCH2_ZL,
    SWITCH2_ZL_PRESSED,
    SWITCH2_ZR,
    SWITCH2_ZR_PRESSED,

    GAMECUBE_A,
    GAMECUBE_A_PRESSED,
    GAMECUBE_B,
    GAMECUBE_B_PRESSED,
    GAMECUBE_X,
    GAMECUBE_X_PRESSED,
    GAMECUBE_Y,
    GAMECUBE_Y_PRESSED,

    GAMECUBE_L,
    GAMECUBE_L_PRESSED,
    GAMECUBE_R,
    GAMECUBE_R_PRESSED,
    GAMECUBE_Z,
    GAMECUBE_Z_PRESSED,

    GAMECUBE_START,
    GAMECUBE_START_PRESSED,

    GAMECUBE_DPAD,
    GAMECUBE_DPAD_PRESSED,

    WIIU_A,
    WIIU_A_PRESSED,
    WIIU_B,
    WIIU_B_PRESSED,
    WIIU_X,
    WIIU_X_PRESSED,
    WIIU_Y,
    WIIU_Y_PRESSED,

    WIIU_L,
    WIIU_L_PRESSED,
    WIIU_R,
    WIIU_R_PRESSED,
    WIIU_ZL,
    WIIU_ZL_PRESSED,
    WIIU_ZR,
    WIIU_ZR_PRESSED,

    WIIU_MINUS,
    WIIU_MINUS_PRESSED,
    WIIU_PLUS,
    WIIU_PLUS_PRESSED,
    WIIU_HOME,
    WIIU_HOME_PRESSED,

    WIIU_DPAD,
    WIIU_DPAD_PRESSED,
}
