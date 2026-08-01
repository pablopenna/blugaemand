package com.blugaemand.input

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.layouts.Layouts
import com.blugaemand.input.layouts.XBOX_LAYOUT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Invariants for layouts drawn with art rather than shapes. Kept apart from the router's own tests
 * because they are about what a layout looks like, not about where a touch goes.
 *
 * Nothing here checks that a [ControlIcon] resolves to a real drawable: the mapping in
 * `com.blugaemand.ui.drawableFor` is an exhaustive `when` over the enum, and every branch names an
 * `R.drawable` constant, so a missing glyph or an unmapped icon is already a compile error.
 */
class LayoutArtTest {

    private val imageLayouts = Layouts.ALL.filter { it.style is LayoutStyle.Images }

    @Test
    fun `the face buttons show the letter they are labelled with, not the one they drive`() {
        // The layout crosses X and Y so that hosts report the letter the player pressed: the key
        // labelled Y drives GamepadButton.X. Anything keyed off these controls has to follow the
        // label, so the control driving GamepadButton.X is the one that must show the Y glyph.
        // Pairing each id with its same-letter glyph is the obvious-looking mistake, and it puts
        // the wrong letter under the player's thumb.
        val faces = XBOX_LAYOUT.controls.associateBy { it.label }

        val north = faces.getValue("Y")
        assertEquals(ControlId.Button(GamepadButton.X), north.id)
        assertEquals(ControlIcon.XBOX_Y, north.icon)
        assertEquals(ControlIcon.XBOX_Y_PRESSED, north.iconPressed)

        val west = faces.getValue("X")
        assertEquals(ControlId.Button(GamepadButton.Y), west.id)
        assertEquals(ControlIcon.XBOX_X, west.icon)
        assertEquals(ControlIcon.XBOX_X_PRESSED, west.iconPressed)

        // The other two are not crossed, and are here so the test fails if the crossing is ever
        // applied to the whole diamond rather than to one pair.
        assertEquals(ControlIcon.XBOX_A, faces.getValue("A").icon)
        assertEquals(ControlIcon.XBOX_B, faces.getValue("B").icon)
    }

    @Test
    fun `every control in an image layout has a glyph, except the sticks`() {
        for (layout in imageLayouts) {
            for (spec in layout.controls) {
                if (spec.id is ControlId.Stick) {
                    // Sticks stay drawn even here: no static glyph can show a displaced knob, so
                    // one would be a picture of a control that no longer moves.
                    assertNull("${layout.id}: ${spec.id} should have no glyph", spec.icon)
                } else {
                    assertNotNull("${layout.id}: ${spec.id} has no glyph", spec.icon)
                }
            }
        }
    }

    @Test
    fun `no control has a pressed glyph without an idle one`() {
        // The renderer falls back to the idle glyph when a control is not held, so a pressed-only
        // control would flicker into existence on touch and vanish again.
        for (layout in Layouts.ALL) {
            for (spec in layout.controls) {
                if (spec.iconPressed != null) {
                    assertNotNull("${layout.id}: ${spec.id} is pressed-only", spec.icon)
                }
            }
        }
    }

    @Test
    fun `layouts drawn with colours name no glyphs`() {
        // A layout in colours mode carrying icons would be dead data that the renderer ignores,
        // and a sign someone expected the two modes to blend.
        for (layout in Layouts.ALL.filter { it.style is LayoutStyle.Colors }) {
            for (spec in layout.controls) {
                assertNull("${layout.id}: ${spec.id} names a glyph it cannot draw", spec.icon)
            }
        }
    }

    @Test
    fun `the catalog offers both presentations`() {
        // Guards the point of the feature: if either mode falls out of ALL, the tests above start
        // passing vacuously.
        assertEquals(1, imageLayouts.size)
        assertEquals(1, Layouts.ALL.count { it.style is LayoutStyle.Colors })
    }
}
