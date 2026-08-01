package com.blugaemand.input

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How a layout presents its controls. A layout is in exactly one mode — the two are alternatives,
 * not layers, so nothing has to decide what a glyph on a coloured plate would mean.
 *
 * Colours are plain ARGB [Int]s rather than Compose `Color`s to keep this package free of
 * `androidx` imports: that is what lets the layout tests run as plain JVM tests. The conversion
 * happens at the `ui` boundary, in [com.blugaemand.ui.PadStyle].
 */
@Serializable
sealed interface LayoutStyle {

    /**
     * Drawn shapes with text labels, in the layout's own two colours.
     *
     * Only the fills that change with press state are the layout's to choose. Strokes, labels and
     * the canvas behind them stay in [com.blugaemand.ui.theme.PadColors] — they are the pad's
     * chrome rather than the layout's identity, and a layout free to recolour its strokes is a
     * layout free to make itself invisible.
     *
     * Both colours are written as `#AARRGGBB`. An ARGB [Int] with a full alpha byte is negative, so
     * left as a number every saved colour would read as a large negative integer — unreadable in a
     * file people are meant to be able to share and hand-edit.
     */
    @Serializable
    @SerialName("colors")
    data class Colors(
        @Serializable(with = ArgbColorSerializer::class)
        val resting: Int = DEFAULT_RESTING,
        @Serializable(with = ArgbColorSerializer::class)
        val pressed: Int = DEFAULT_PRESSED,
    ) : LayoutStyle

    /**
     * A picture per control, from [pack].
     *
     * The pack is named once here rather than a glyph being written onto every [ControlSpec]: what
     * distinguishes two layouts over the same geometry is which art they draw with, and stating it
     * once is what keeps a layout to the thing that is genuinely per-control — where the control
     * is. A pack need not cover everything; see [ArtPack].
     *
     * Serialised as the pack's id alone, resolved back through
     * [com.blugaemand.input.art.ArtPacks]. A layout that wrote out a picture per control would be
     * a layout that could disagree with its own pack.
     */
    @Serializable
    @SerialName("images")
    data class Images(
        @Serializable(with = ArtPackSerializer::class)
        val pack: ArtPack,
    ) : LayoutStyle

    companion object {
        /** The greys the pad has always used, so a layout that names no colours looks unchanged. */
        val DEFAULT_RESTING: Int = 0xFF262B36.toInt()
        val DEFAULT_PRESSED: Int = 0xFF4C82F7.toInt()
    }
}
