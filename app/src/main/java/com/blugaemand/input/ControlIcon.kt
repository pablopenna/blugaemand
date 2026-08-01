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
}
