package com.blugaemand.ui

import androidx.annotation.DrawableRes
import com.blugaemand.R
import com.blugaemand.input.ControlIcon

/**
 * The drawable behind each [ControlIcon]. The only place in the app that maps one to a drawable,
 * which is what keeps resource IDs — reassigned on every build — out of the serialisable layout
 * model.
 *
 * Nothing here or downstream cares whether a drawable is a vector or a bitmap: `painterResource`
 * returns a painter either way and the renderer draws both identically. Vectors are used for the
 * bundled art because they stay sharp at any size, not because anything requires them.
 *
 * Every glyph is from Kenney's Input Prompts 1.5A (CC0), converted by
 * `art/input/convert-input-art.py`.
 *
 * The `switch2_` sources are the one place a file was renamed on the way in. Kenney keys its art on
 * the console folder, so `Nintendo Switch 2` ships a `switch_button_zl.svg` of its own that is a
 * *different picture* from the `Nintendo Switch` file of that name — and `art/input/` is flat, so
 * one would have overwritten the other. Only the four redrawn triggers are carried across, under
 * names that say which console they came from; see [ControlIcon].
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
    ControlIcon.XBOX_DPAD_UP -> R.drawable.xbox_dpad_up
    ControlIcon.XBOX_DPAD_DOWN -> R.drawable.xbox_dpad_down
    ControlIcon.XBOX_DPAD_LEFT -> R.drawable.xbox_dpad_left
    ControlIcon.XBOX_DPAD_RIGHT -> R.drawable.xbox_dpad_right

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
    ControlIcon.PS_DPAD_UP -> R.drawable.playstation_dpad_up
    ControlIcon.PS_DPAD_DOWN -> R.drawable.playstation_dpad_down
    ControlIcon.PS_DPAD_LEFT -> R.drawable.playstation_dpad_left
    ControlIcon.PS_DPAD_RIGHT -> R.drawable.playstation_dpad_right

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
    ControlIcon.SWITCH_DPAD_UP -> R.drawable.switch_dpad_up
    ControlIcon.SWITCH_DPAD_DOWN -> R.drawable.switch_dpad_down
    ControlIcon.SWITCH_DPAD_LEFT -> R.drawable.switch_dpad_left
    ControlIcon.SWITCH_DPAD_RIGHT -> R.drawable.switch_dpad_right

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
    ControlIcon.DECK_DPAD_UP -> R.drawable.steamdeck_dpad_up
    ControlIcon.DECK_DPAD_DOWN -> R.drawable.steamdeck_dpad_down
    ControlIcon.DECK_DPAD_LEFT -> R.drawable.steamdeck_dpad_left
    ControlIcon.DECK_DPAD_RIGHT -> R.drawable.steamdeck_dpad_right

    // Only the triggers were redrawn for the Switch 2 -- everything else SWITCH2_ART draws is the
    // Switch 1 file, byte for byte. See the note above about the rename.
    ControlIcon.SWITCH2_ZL -> R.drawable.switch2_button_zl_outline
    ControlIcon.SWITCH2_ZL_PRESSED -> R.drawable.switch2_button_zl
    ControlIcon.SWITCH2_ZR -> R.drawable.switch2_button_zr_outline
    ControlIcon.SWITCH2_ZR_PRESSED -> R.drawable.switch2_button_zr

    // A and B are the only coloured keys on a GameCube -- green and red -- so those two take the
    // colour-fill pressed picture the Xbox face buttons do, and the grey X and Y take a solid white
    // one like every Nintendo pack since.
    ControlIcon.GAMECUBE_A -> R.drawable.gamecube_button_a_outline
    ControlIcon.GAMECUBE_A_PRESSED -> R.drawable.gamecube_button_color_a
    ControlIcon.GAMECUBE_B -> R.drawable.gamecube_button_b_outline
    ControlIcon.GAMECUBE_B_PRESSED -> R.drawable.gamecube_button_color_b
    ControlIcon.GAMECUBE_X -> R.drawable.gamecube_button_x_outline
    ControlIcon.GAMECUBE_X_PRESSED -> R.drawable.gamecube_button_x
    ControlIcon.GAMECUBE_Y -> R.drawable.gamecube_button_y_outline
    ControlIcon.GAMECUBE_Y_PRESSED -> R.drawable.gamecube_button_y

    ControlIcon.GAMECUBE_L -> R.drawable.gamecube_trigger_l_outline
    ControlIcon.GAMECUBE_L_PRESSED -> R.drawable.gamecube_trigger_l
    ControlIcon.GAMECUBE_R -> R.drawable.gamecube_trigger_r_outline
    ControlIcon.GAMECUBE_R_PRESSED -> R.drawable.gamecube_trigger_r
    ControlIcon.GAMECUBE_Z -> R.drawable.gamecube_button_z_outline
    ControlIcon.GAMECUBE_Z_PRESSED -> R.drawable.gamecube_button_z

    ControlIcon.GAMECUBE_START -> R.drawable.gamecube_button_start_outline
    ControlIcon.GAMECUBE_START_PRESSED -> R.drawable.gamecube_button_start

    ControlIcon.GAMECUBE_DPAD -> R.drawable.gamecube_dpad
    ControlIcon.GAMECUBE_DPAD_PRESSED -> R.drawable.gamecube_dpad_all
    ControlIcon.GAMECUBE_DPAD_UP -> R.drawable.gamecube_dpad_up
    ControlIcon.GAMECUBE_DPAD_DOWN -> R.drawable.gamecube_dpad_down
    ControlIcon.GAMECUBE_DPAD_LEFT -> R.drawable.gamecube_dpad_left
    ControlIcon.GAMECUBE_DPAD_RIGHT -> R.drawable.gamecube_dpad_right

    ControlIcon.WIIU_A -> R.drawable.wiiu_button_a_outline
    ControlIcon.WIIU_A_PRESSED -> R.drawable.wiiu_button_a
    ControlIcon.WIIU_B -> R.drawable.wiiu_button_b_outline
    ControlIcon.WIIU_B_PRESSED -> R.drawable.wiiu_button_b
    ControlIcon.WIIU_X -> R.drawable.wiiu_button_x_outline
    ControlIcon.WIIU_X_PRESSED -> R.drawable.wiiu_button_x
    ControlIcon.WIIU_Y -> R.drawable.wiiu_button_y_outline
    ControlIcon.WIIU_Y_PRESSED -> R.drawable.wiiu_button_y

    ControlIcon.WIIU_L -> R.drawable.wiiu_button_l_outline
    ControlIcon.WIIU_L_PRESSED -> R.drawable.wiiu_button_l
    ControlIcon.WIIU_R -> R.drawable.wiiu_button_r_outline
    ControlIcon.WIIU_R_PRESSED -> R.drawable.wiiu_button_r
    ControlIcon.WIIU_ZL -> R.drawable.wiiu_button_zl_outline
    ControlIcon.WIIU_ZL_PRESSED -> R.drawable.wiiu_button_zl
    ControlIcon.WIIU_ZR -> R.drawable.wiiu_button_zr_outline
    ControlIcon.WIIU_ZR_PRESSED -> R.drawable.wiiu_button_zr

    ControlIcon.WIIU_MINUS -> R.drawable.wiiu_button_minus_outline
    ControlIcon.WIIU_MINUS_PRESSED -> R.drawable.wiiu_button_minus
    ControlIcon.WIIU_PLUS -> R.drawable.wiiu_button_plus_outline
    ControlIcon.WIIU_PLUS_PRESSED -> R.drawable.wiiu_button_plus
    ControlIcon.WIIU_HOME -> R.drawable.wiiu_button_home_outline
    ControlIcon.WIIU_HOME_PRESSED -> R.drawable.wiiu_button_home

    ControlIcon.WIIU_DPAD -> R.drawable.wiiu_dpad
    ControlIcon.WIIU_DPAD_PRESSED -> R.drawable.wiiu_dpad_all
    ControlIcon.WIIU_DPAD_UP -> R.drawable.wiiu_dpad_up
    ControlIcon.WIIU_DPAD_DOWN -> R.drawable.wiiu_dpad_down
    ControlIcon.WIIU_DPAD_LEFT -> R.drawable.wiiu_dpad_left
    ControlIcon.WIIU_DPAD_RIGHT -> R.drawable.wiiu_dpad_right
}
