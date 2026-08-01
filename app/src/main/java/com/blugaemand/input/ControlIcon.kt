package com.blugaemand.input

/**
 * A glyph a control can draw in [LayoutStyle.Images] mode.
 *
 * An enum of names rather than drawable resource IDs on purpose: resource IDs are assigned at build
 * time and are not stable between builds, so a layout that stored one would deserialise into a
 * different picture — or none. The mapping onto actual drawables lives in
 * [com.blugaemand.ui.drawableFor], the one place in the app that knows about `R`.
 *
 * Each control names an idle glyph and, optionally, a pressed one; see [ControlSpec.iconPressed].
 * The Xbox set pairs an outline with its solid counterpart, which is the art pack's own idiom.
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
}
