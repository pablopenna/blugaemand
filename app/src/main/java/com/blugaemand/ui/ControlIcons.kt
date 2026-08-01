package com.blugaemand.ui

import androidx.annotation.DrawableRes
import com.blugaemand.R
import com.blugaemand.input.ControlIcon

/**
 * The drawable behind each [ControlIcon]. The only place in the app that mentions `R.drawable`,
 * which is what keeps resource IDs — reassigned on every build — out of the serialisable layout
 * model.
 *
 * Nothing here or downstream cares whether a drawable is a vector or a bitmap: `painterResource`
 * returns a painter either way and the renderer draws both identically. Vectors are used for the
 * bundled art because they stay sharp at any size, not because anything requires them.
 *
 * The Xbox, PlayStation, Switch and Steam Deck glyphs are all Kenney's Input Prompts 1.5A (CC0),
 * converted by `art/input/convert-input-art.py`.
 */
@DrawableRes
fun drawableFor(icon: ControlIcon): Int = when (icon) {
    ControlIcon.XBOX_A -> R.drawable.xbox_button_a_outline
    ControlIcon.XBOX_A_PRESSED -> R.drawable.xbox_button_color_a
    ControlIcon.XBOX_B -> R.drawable.xbox_button_b_outline
    ControlIcon.XBOX_B_PRESSED -> R.drawable.xbox_button_color_b
    ControlIcon.XBOX_X -> R.drawable.xbox_button_x_outline
    ControlIcon.XBOX_X_PRESSED -> R.drawable.xbox_button_color_x
    ControlIcon.XBOX_Y -> R.drawable.xbox_button_y_outline
    ControlIcon.XBOX_Y_PRESSED -> R.drawable.xbox_button_color_y

    ControlIcon.XBOX_LB -> R.drawable.xbox_lb_outline
    ControlIcon.XBOX_LB_PRESSED -> R.drawable.xbox_lb
    ControlIcon.XBOX_RB -> R.drawable.xbox_rb_outline
    ControlIcon.XBOX_RB_PRESSED -> R.drawable.xbox_rb
    ControlIcon.XBOX_LT -> R.drawable.xbox_lt_outline
    ControlIcon.XBOX_LT_PRESSED -> R.drawable.xbox_lt
    ControlIcon.XBOX_RT -> R.drawable.xbox_rt_outline
    ControlIcon.XBOX_RT_PRESSED -> R.drawable.xbox_rt

    ControlIcon.XBOX_VIEW -> R.drawable.xbox_button_view_outline
    ControlIcon.XBOX_VIEW_PRESSED -> R.drawable.xbox_button_view
    ControlIcon.XBOX_MENU -> R.drawable.xbox_button_menu_outline
    ControlIcon.XBOX_MENU_PRESSED -> R.drawable.xbox_button_menu
    ControlIcon.XBOX_GUIDE -> R.drawable.xbox_guide_outline
    ControlIcon.XBOX_GUIDE_PRESSED -> R.drawable.xbox_guide

    ControlIcon.XBOX_LS -> R.drawable.xbox_ls_outline
    ControlIcon.XBOX_LS_PRESSED -> R.drawable.xbox_ls
    ControlIcon.XBOX_RS -> R.drawable.xbox_rs_outline
    ControlIcon.XBOX_RS_PRESSED -> R.drawable.xbox_rs

    ControlIcon.XBOX_DPAD -> R.drawable.xbox_dpad
    ControlIcon.XBOX_DPAD_PRESSED -> R.drawable.xbox_dpad_all

    ControlIcon.PS_CROSS -> R.drawable.playstation_button_cross_outline
    ControlIcon.PS_CROSS_PRESSED -> R.drawable.playstation_button_color_cross
    ControlIcon.PS_CIRCLE -> R.drawable.playstation_button_circle_outline
    ControlIcon.PS_CIRCLE_PRESSED -> R.drawable.playstation_button_color_circle
    ControlIcon.PS_SQUARE -> R.drawable.playstation_button_square_outline
    ControlIcon.PS_SQUARE_PRESSED -> R.drawable.playstation_button_color_square
    ControlIcon.PS_TRIANGLE -> R.drawable.playstation_button_triangle_outline
    ControlIcon.PS_TRIANGLE_PRESSED -> R.drawable.playstation_button_color_triangle

    ControlIcon.PS_L1 -> R.drawable.playstation_trigger_l1_outline
    ControlIcon.PS_L1_PRESSED -> R.drawable.playstation_trigger_l1
    ControlIcon.PS_R1 -> R.drawable.playstation_trigger_r1_outline
    ControlIcon.PS_R1_PRESSED -> R.drawable.playstation_trigger_r1
    ControlIcon.PS_L2 -> R.drawable.playstation_trigger_l2_outline
    ControlIcon.PS_L2_PRESSED -> R.drawable.playstation_trigger_l2
    ControlIcon.PS_R2 -> R.drawable.playstation_trigger_r2_outline
    ControlIcon.PS_R2_PRESSED -> R.drawable.playstation_trigger_r2

    ControlIcon.PS5_CREATE -> R.drawable.playstation5_button_create_outline
    ControlIcon.PS5_CREATE_PRESSED -> R.drawable.playstation5_button_create
    ControlIcon.PS5_OPTIONS -> R.drawable.playstation5_button_options_outline
    ControlIcon.PS5_OPTIONS_PRESSED -> R.drawable.playstation5_button_options

    ControlIcon.PS_L3 -> R.drawable.playstation_button_l3_outline
    ControlIcon.PS_L3_PRESSED -> R.drawable.playstation_button_l3
    ControlIcon.PS_R3 -> R.drawable.playstation_button_r3_outline
    ControlIcon.PS_R3_PRESSED -> R.drawable.playstation_button_r3

    ControlIcon.PS_DPAD -> R.drawable.playstation_dpad
    ControlIcon.PS_DPAD_PRESSED -> R.drawable.playstation_dpad_all

    // Neither Nintendo's pack nor Valve's has coloured face buttons -- both draw them in one
    // colour, as the real pads are -- so the pressed picture is the solid fill rather than a
    // second hue.
    ControlIcon.SWITCH_A -> R.drawable.switch_button_a_outline
    ControlIcon.SWITCH_A_PRESSED -> R.drawable.switch_button_a
    ControlIcon.SWITCH_B -> R.drawable.switch_button_b_outline
    ControlIcon.SWITCH_B_PRESSED -> R.drawable.switch_button_b
    ControlIcon.SWITCH_X -> R.drawable.switch_button_x_outline
    ControlIcon.SWITCH_X_PRESSED -> R.drawable.switch_button_x
    ControlIcon.SWITCH_Y -> R.drawable.switch_button_y_outline
    ControlIcon.SWITCH_Y_PRESSED -> R.drawable.switch_button_y

    ControlIcon.SWITCH_L -> R.drawable.switch_button_l_outline
    ControlIcon.SWITCH_L_PRESSED -> R.drawable.switch_button_l
    ControlIcon.SWITCH_R -> R.drawable.switch_button_r_outline
    ControlIcon.SWITCH_R_PRESSED -> R.drawable.switch_button_r
    ControlIcon.SWITCH_ZL -> R.drawable.switch_button_zl_outline
    ControlIcon.SWITCH_ZL_PRESSED -> R.drawable.switch_button_zl
    ControlIcon.SWITCH_ZR -> R.drawable.switch_button_zr_outline
    ControlIcon.SWITCH_ZR_PRESSED -> R.drawable.switch_button_zr

    ControlIcon.SWITCH_MINUS -> R.drawable.switch_button_minus_outline
    ControlIcon.SWITCH_MINUS_PRESSED -> R.drawable.switch_button_minus
    ControlIcon.SWITCH_PLUS -> R.drawable.switch_button_plus_outline
    ControlIcon.SWITCH_PLUS_PRESSED -> R.drawable.switch_button_plus
    ControlIcon.SWITCH_HOME -> R.drawable.switch_button_home_outline
    ControlIcon.SWITCH_HOME_PRESSED -> R.drawable.switch_button_home

    // No stick-click button in either pack, so the pair is the stick and the stick pressed.
    ControlIcon.SWITCH_LS -> R.drawable.switch_stick_l
    ControlIcon.SWITCH_LS_PRESSED -> R.drawable.switch_stick_l_press
    ControlIcon.SWITCH_RS -> R.drawable.switch_stick_r
    ControlIcon.SWITCH_RS_PRESSED -> R.drawable.switch_stick_r_press

    ControlIcon.SWITCH_DPAD -> R.drawable.switch_dpad
    ControlIcon.SWITCH_DPAD_PRESSED -> R.drawable.switch_dpad_all

    ControlIcon.DECK_A -> R.drawable.steamdeck_button_a_outline
    ControlIcon.DECK_A_PRESSED -> R.drawable.steamdeck_button_a
    ControlIcon.DECK_B -> R.drawable.steamdeck_button_b_outline
    ControlIcon.DECK_B_PRESSED -> R.drawable.steamdeck_button_b
    ControlIcon.DECK_X -> R.drawable.steamdeck_button_x_outline
    ControlIcon.DECK_X_PRESSED -> R.drawable.steamdeck_button_x
    ControlIcon.DECK_Y -> R.drawable.steamdeck_button_y_outline
    ControlIcon.DECK_Y_PRESSED -> R.drawable.steamdeck_button_y

    ControlIcon.DECK_L1 -> R.drawable.steamdeck_button_l1_outline
    ControlIcon.DECK_L1_PRESSED -> R.drawable.steamdeck_button_l1
    ControlIcon.DECK_R1 -> R.drawable.steamdeck_button_r1_outline
    ControlIcon.DECK_R1_PRESSED -> R.drawable.steamdeck_button_r1
    ControlIcon.DECK_L2 -> R.drawable.steamdeck_button_l2_outline
    ControlIcon.DECK_L2_PRESSED -> R.drawable.steamdeck_button_l2
    ControlIcon.DECK_R2 -> R.drawable.steamdeck_button_r2_outline
    ControlIcon.DECK_R2_PRESSED -> R.drawable.steamdeck_button_r2

    ControlIcon.DECK_VIEW -> R.drawable.steamdeck_button_view_outline
    ControlIcon.DECK_VIEW_PRESSED -> R.drawable.steamdeck_button_view
    ControlIcon.DECK_OPTIONS -> R.drawable.steamdeck_button_options_outline
    ControlIcon.DECK_OPTIONS_PRESSED -> R.drawable.steamdeck_button_options
    ControlIcon.DECK_STEAM -> R.drawable.steamdeck_button_guide_outline
    ControlIcon.DECK_STEAM_PRESSED -> R.drawable.steamdeck_button_guide

    ControlIcon.DECK_LS -> R.drawable.steamdeck_stick_l
    ControlIcon.DECK_LS_PRESSED -> R.drawable.steamdeck_stick_l_press
    ControlIcon.DECK_RS -> R.drawable.steamdeck_stick_r
    ControlIcon.DECK_RS_PRESSED -> R.drawable.steamdeck_stick_r_press

    ControlIcon.DECK_DPAD -> R.drawable.steamdeck_dpad
    ControlIcon.DECK_DPAD_PRESSED -> R.drawable.steamdeck_dpad_all
}
