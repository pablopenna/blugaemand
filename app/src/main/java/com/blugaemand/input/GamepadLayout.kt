package com.blugaemand.input

import kotlinx.serialization.Serializable

/**
 * An arrangement of on-screen controls, and how they are drawn.
 *
 * Plain data on purpose, which is what let it become the saved format unchanged — a user layout is
 * a variable-length list of controls under a name, and that is all this ever was.
 * [com.blugaemand.ui.GamepadScreen] takes the layout as a parameter and never reaches for a global,
 * so an edited layout reaches the pad by being handed to it.
 *
 * The built-in layouts live one per file in [com.blugaemand.input.layouts], which is also where
 * the catalog the menu offers is assembled. See [LayoutJson] for how one is written out.
 */
@Serializable
data class GamepadLayout(
    val id: String,
    val name: String,
    val controls: List<ControlSpec>,
    val style: LayoutStyle = LayoutStyle.Colors(),
)
