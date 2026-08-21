package com.blugaemand.input

/**
 * A handful of ready-made colour pairs for a layout drawn as shapes.
 *
 * The picker underneath can make any colour at all, which is exactly the problem: two colours that
 * work together — a resting fill dark enough to read a label on and a pressed fill that is
 * obviously not it — take a few goes to find, and most people want a pad that looks deliberate
 * rather than a colour wheel. A theme is one tap for that, and the picker is still there for
 * anyone who wants their own.
 *
 * Every pressed colour here is the loud one and every resting colour is dark, which is what keeps
 * the pad's white labels legible in all of them; see [LayoutStyle.Colors] for what the two are.
 */
data class PadTheme(val name: String, val colors: LayoutStyle.Colors)

object PadThemes {

    /** The greys the pad has always come in, and what a layout naming no colours is drawn as. */
    val SLATE = PadTheme("Slate", LayoutStyle.Colors())

    val CARBON = PadTheme(
        "Carbon",
        LayoutStyle.Colors(resting = 0xFF1A1A1D.toInt(), pressed = 0xFFE7E9EE.toInt()),
    )

    val EMBER = PadTheme(
        "Ember",
        LayoutStyle.Colors(resting = 0xFF2C1E1B.toInt(), pressed = 0xFFF2762E.toInt()),
    )

    val MOSS = PadTheme(
        "Moss",
        LayoutStyle.Colors(resting = 0xFF1B2A22.toInt(), pressed = 0xFF54C98A.toInt()),
    )

    val ORCHID = PadTheme(
        "Orchid",
        LayoutStyle.Colors(resting = 0xFF241C33.toInt(), pressed = 0xFFB06BF0.toInt()),
    )

    val LAGOON = PadTheme(
        "Lagoon",
        LayoutStyle.Colors(resting = 0xFF13272E.toInt(), pressed = 0xFF2FC2D6.toInt()),
    )

    /** In the order the editor offers them, with the pad's own colours first. */
    val ALL = listOf(SLATE, CARBON, EMBER, MOSS, ORCHID, LAGOON)
}
