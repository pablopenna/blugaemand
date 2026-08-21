package com.blugaemand.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The ready-made colour pairs.
 *
 * A theme has one job — to look deliberate without anyone having to tune it — and the ways of
 * failing at that are mechanical: a pair too close together to tell pressed from resting, a colour
 * that is accidentally transparent, two rows in the menu with the same name.
 */
class PadThemesTest {

    @Test
    fun `the first theme is the pad's own colours`() {
        // So that the row ticked on a fresh layout is the one the pad is actually drawn in, rather
        // than none of them.
        assertEquals(LayoutStyle.Colors(), PadThemes.ALL.first().colors)
    }

    @Test
    fun `every theme has a name of its own`() {
        val names = PadThemes.ALL.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `every colour is fully opaque`() {
        // Transparency is the layout's own setting and applies to the whole pad at once; a theme
        // carrying some of its own would multiply with it into something nobody chose.
        for (theme in PadThemes.ALL) {
            assertEquals(theme.name, 0xFF, theme.colors.resting ushr 24)
            assertEquals(theme.name, 0xFF, theme.colors.pressed ushr 24)
        }
    }

    @Test
    fun `held is obviously not resting in every theme`() {
        // The one thing a pad has to show. Measured as relative luminance rather than by eye,
        // because "these two look different on my phone" is how a pair that reads as one colour on
        // someone else's gets shipped.
        for (theme in PadThemes.ALL) {
            val gap = abs(luminance(theme.colors.pressed) - luminance(theme.colors.resting))
            assertTrue("${theme.name}: $gap", gap > 0.2f)
        }
    }

    /** Relative luminance, the sRGB weighting, close enough for a "these are far apart" test. */
    private fun luminance(argb: Int): Float {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
}
