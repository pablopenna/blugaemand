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
    /**
     * How solid the controls are drawn, from [MIN_OPACITY] to 1.
     *
     * On the layout rather than on [LayoutStyle], because it applies to a pad drawn from an art
     * pack exactly as it does to one drawn as shapes — and a copy on each of the two styles would
     * be a setting silently lost every time someone tried a pack and came back.
     *
     * What it is for is seeing through the pad: a phone is a screen before it is a controller, and
     * a translucent pad over a streamed or emulated game is the whole reason to want it. Which is
     * also why the floor is not zero — a pad at nothing is a blank screen that still takes touches,
     * with no way to find the control that would put it back.
     *
     * Defaulted, so a layout saved before it existed reads as fully solid and no format version
     * moves.
     */
    val opacity: Float = 1f,
) {
    companion object {
        /** As faint as a pad may be made and still be found by the thumb using it. */
        const val MIN_OPACITY = 0.25f
    }
}
