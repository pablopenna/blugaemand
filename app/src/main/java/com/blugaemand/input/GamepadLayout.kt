package com.blugaemand.input

/**
 * An arrangement of on-screen controls, and how they are drawn.
 *
 * Plain data on purpose. Making layouts user-configurable later means serialising this and adding
 * an editor; nothing else has to change, because [com.blugaemand.ui.GamepadScreen] takes the
 * layout as a parameter and never reaches for a global.
 *
 * The built-in layouts live one per file in [com.blugaemand.input.layouts], which is also where
 * the catalog the menu offers is assembled.
 */
data class GamepadLayout(
    val id: String,
    val name: String,
    val controls: List<ControlSpec>,
    val style: LayoutStyle = LayoutStyle.Colors(),
)
