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
 * The Xbox glyphs are Kenney's Input Prompts 1.5A (CC0), converted by
 * `art/input/convert-input-art.py`.
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
}
