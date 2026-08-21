package com.blugaemand.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.blugaemand.hid.Hat
import com.blugaemand.input.ArtPack
import com.blugaemand.input.ControlIcon
import com.blugaemand.input.ControlId
import com.blugaemand.input.GamepadLayout
import com.blugaemand.input.LayoutStyle
import com.blugaemand.ui.theme.PadColors

/**
 * A [LayoutStyle] turned into the things the canvas actually draws with: Compose colours, and
 * painters for the glyphs a layout names.
 *
 * This exists because `painterResource` is a composable while the pad draws inside a `Canvas`
 * lambda, which is not composition — the painters have to be resolved up front and handed down. It
 * is also where ARGB [Int]s become [Color]s, which is what keeps Compose types out of the `input`
 * package.
 */
class PadStyle(
    /** Fill for a control at rest. */
    val resting: Color,
    /** Fill for a control being held, and for a thumbstick's cap while in use. */
    val pressed: Color,
    private val pack: ArtPack?,
    private val painters: Map<ControlIcon, Painter>,
) {
    /**
     * The picture [control] draws while [held], or null if it has none and falls back to its
     * shape — which is every control in colours mode, where there is no pack at all.
     */
    fun glyph(control: ControlId, held: Boolean): Painter? =
        pack?.glyph(control, held)?.let { painters[it] }

    /**
     * The picture a one-piece D-pad draws while a thumb on it is sending [direction], or null when
     * there is no pack. [ArtPack.dpadGlyph] is where the fallbacks live — a diagonal has no picture
     * of its own and the dead zone draws the resting cross.
     */
    fun dpadGlyph(direction: Hat): Painter? =
        pack?.dpadGlyph(direction)?.let { painters[it] }
}

/**
 * Resolves [layout]'s style during composition.
 *
 * Images mode declares no colours of its own, and falls back to the pad's neutrals. That only
 * shows up on the thumbsticks: they stay drawn in both modes, because no static glyph can show a
 * knob displaced from centre.
 */
@Composable
fun rememberPadStyle(layout: GamepadLayout): PadStyle {
    // Every glyph is resolved in images mode, not just the ones this layout uses. Compose
    // identifies calls by position, so an unconditional walk of a fixed enum keeps them stable
    // across recompositions in a way that filtering to the layout's own icons would not. There are
    // a couple of dozen, all cached by the resource loader.
    val painters = mutableMapOf<ControlIcon, Painter>()
    if (layout.style is LayoutStyle.Images) {
        for (icon in ControlIcon.entries) {
            painters[icon] = painterResource(drawableFor(icon))
        }
    }

    return when (val style = layout.style) {
        is LayoutStyle.Colors ->
            PadStyle(Color(style.resting), Color(style.pressed), pack = null, painters = painters)
        is LayoutStyle.Images ->
            PadStyle(PadColors.ControlFill, PadColors.ControlFillPressed, style.pack, painters)
    }
}
