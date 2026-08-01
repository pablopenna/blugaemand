package com.blugaemand.input.layouts

import com.blugaemand.input.GamepadLayout

/** The catalog of built-in layouts. */
object Layouts {

    /**
     * Every layout the menu offers, in the order it lists them. Adding a built-in means adding a
     * file beside this one and a line here; the layout-sanity tests run over this list, so a new
     * layout inherits them for free. Saved user layouts will be appended once they can be
     * serialised.
     */
    val ALL: List<GamepadLayout> = listOf(DEFAULT_LAYOUT, XBOX_LAYOUT)
}
