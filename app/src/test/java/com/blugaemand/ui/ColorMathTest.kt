package com.blugaemand.ui

import com.blugaemand.input.LayoutStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The colour picker's arithmetic, which is the whole of what it does that can be got wrong quietly.
 *
 * A picker that draws the wrong square looks wrong; a picker that loses a byte on the way to the
 * layout produces a pad that is very slightly the wrong colour and no one notices until the file is
 * read. These run on the JVM like the rest of the editor's arithmetic — see `ColorMath`.
 */
class ColorMathTest {

    @Test
    fun `a colour survives the round trip through hue, saturation and value`() {
        // Every corner of the cube plus the two the layout ships with, because the six sectors of
        // the hue wheel meet at the corners and an off-by-one sector shows up there first.
        val colors = listOf(
            0xFFFF0000, 0xFF00FF00, 0xFF0000FF,
            0xFFFFFF00, 0xFF00FFFF, 0xFFFF00FF,
            0xFFFFFFFF, 0xFF000000, 0xFF808080,
            0xFF262B36, 0xFF4C82F7,
        ).map { it.toInt() }

        for (color in colors) {
            assertEquals(
                "round trip of ${color.toUInt().toString(16)}",
                color,
                color.toHsv().toArgb(color.alphaByte()),
            )
        }
    }

    @Test
    fun `the layout's own two colours round trip`() {
        // Not merely a nice property: the picker opens showing whatever the layout holds, and a
        // colour that shifted on being read would move the marker the first time it was touched.
        val resting = LayoutStyle.DEFAULT_RESTING
        val pressed = LayoutStyle.DEFAULT_PRESSED
        assertEquals(resting, resting.toHsv().toArgb(resting.alphaByte()))
        assertEquals(pressed, pressed.toHsv().toArgb(pressed.alphaByte()))
    }

    @Test
    fun `alpha is carried through rather than picked`() {
        val translucent = 0x80336699.toInt()
        assertEquals(0x80, translucent.alphaByte())
        assertEquals(translucent, translucent.toHsv().toArgb(translucent.alphaByte()))

        // And the picker can be handed a different one without touching the colour underneath.
        assertEquals(0xFF336699.toInt(), translucent.toHsv().toArgb(0xFF))
    }

    @Test
    fun `hue is read as degrees around the wheel`() {
        assertEquals(0f, 0xFFFF0000.toInt().toHsv().hue, TOLERANCE)
        assertEquals(120f, 0xFF00FF00.toInt().toHsv().hue, TOLERANCE)
        assertEquals(240f, 0xFF0000FF.toInt().toHsv().hue, TOLERANCE)
        // Magenta sits in the last sector, which is the one the wrap-around is computed in.
        assertEquals(300f, 0xFFFF00FF.toInt().toHsv().hue, TOLERANCE)
    }

    @Test
    fun `a grey has no saturation and black has no value`() {
        val grey = 0xFF808080.toInt().toHsv()
        assertEquals(0f, grey.saturation, TOLERANCE)
        assertEquals(0.5f, grey.value, TOLERANCE)

        val black = 0xFF000000.toInt().toHsv()
        assertEquals(0f, black.saturation, TOLERANCE)
        assertEquals(0f, black.value, TOLERANCE)
    }

    @Test
    fun `hue is lost on the way to a grey, which is why the picker holds one`() {
        // The reason ColorPicker keeps a hue of its own rather than reading one back out of the
        // layout on every frame: dragging the value to the bottom would otherwise quietly turn a
        // blue pad red on the way back up. This test is the statement of the problem, not a bug.
        val blue = 0xFF4C82F7.toInt()
        val blackened = blue.toHsv().copy(value = 0f).toArgb(0xFF)

        assertEquals(0xFF000000.toInt(), blackened)
        assertNotEquals(blue.toHsv().hue, blackened.toHsv().hue)
    }

    @Test
    fun `hue wraps rather than clamping at either end`() {
        // The bar is dragged to its ends constantly, and 360 is the same red as 0. Both have to
        // produce a colour rather than one of them landing outside the six sectors.
        val red = Hsv(0f, 1f, 1f).toArgb(0xFF)
        assertEquals(red, Hsv(360f, 1f, 1f).toArgb(0xFF))
        assertEquals(red, Hsv(720f, 1f, 1f).toArgb(0xFF))
        assertEquals(red, Hsv(-360f, 1f, 1f).toArgb(0xFF))
    }

    @Test
    fun `components outside their range are clamped rather than wrapped`() {
        // Unlike hue, where wrapping is the right answer. Saturation and value are fractions of a
        // travel that has two ends, and a finger dragged off the square reports past them.
        assertEquals(0xFFFFFFFF.toInt(), Hsv(0f, -1f, 2f).toArgb(0xFF))
        assertEquals(0xFF000000.toInt(), Hsv(0f, 2f, -1f).toArgb(0xFF))
    }

    private companion object {
        const val TOLERANCE = 0.01f
    }
}
