package com.blugaemand.input

/**
 * How a layout presents its controls. A layout is in exactly one mode — the two are alternatives,
 * not layers, so nothing has to decide what a glyph on a coloured plate would mean.
 *
 * Colours are plain ARGB [Int]s rather than Compose `Color`s to keep this package free of
 * `androidx` imports: that is what lets the layout tests run as plain JVM tests, and what will let
 * [GamepadLayout] serialise without a custom serialiser. The conversion happens at the `ui`
 * boundary, in [com.blugaemand.ui.PadStyle].
 */
sealed interface LayoutStyle {

    /**
     * Drawn shapes with text labels, in the layout's own two colours.
     *
     * Only the fills that change with press state are the layout's to choose. Strokes, labels and
     * the canvas behind them stay in [com.blugaemand.ui.theme.PadColors] — they are the pad's
     * chrome rather than the layout's identity, and a layout free to recolour its strokes is a
     * layout free to make itself invisible.
     */
    data class Colors(
        val resting: Int = DEFAULT_RESTING,
        val pressed: Int = DEFAULT_PRESSED,
    ) : LayoutStyle

    /**
     * A glyph per control, from an art pack. Which pack is implicit in the [ControlIcon] values the
     * layout's controls name, so there is nothing to declare here.
     */
    data object Images : LayoutStyle

    companion object {
        /** The greys the pad has always used, so a layout that names no colours looks unchanged. */
        val DEFAULT_RESTING: Int = 0xFF262B36.toInt()
        val DEFAULT_PRESSED: Int = 0xFF4C82F7.toInt()
    }
}
