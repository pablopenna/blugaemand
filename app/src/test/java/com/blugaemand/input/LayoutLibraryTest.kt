package com.blugaemand.input

import com.blugaemand.input.layouts.DEFAULT_LAYOUT
import com.blugaemand.input.layouts.Layouts
import com.blugaemand.input.layouts.PS5_LAYOUT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalog of built-in and user layouts, and the line between them.
 *
 * The built-ins being read-only is enforced in exactly one place — [LayoutLibrary.isEditable] — so
 * these are the tests standing between that decision and a screen quietly editing one.
 */
class LayoutLibraryTest {

    private val mine = emptyUserLayout("Mine")

    // -- Built-ins are read-only ----------------------------------------------------------

    @Test
    fun `no built-in layout is editable`() {
        val library = LayoutLibrary().with(mine)
        for (layout in Layouts.ALL) {
            assertFalse(layout.id, library.isEditable(layout.id))
        }
    }

    @Test
    fun `a user layout is editable`() {
        assertTrue(LayoutLibrary().with(mine).isEditable(mine.id))
    }

    @Test
    fun `removing cannot reach a built-in`() {
        // `without` filters the user list, and the built-ins are not in it. Worth pinning: the day
        // that list becomes "everything" is the day Delete starts eating the shipped layouts.
        val library = LayoutLibrary().with(mine).without(DEFAULT_LAYOUT.id)
        assertTrue(DEFAULT_LAYOUT in library.all)
        assertEquals(listOf(mine), library.user)
    }

    // -- The catalog ----------------------------------------------------------------------

    @Test
    fun `the catalog is the built-ins followed by the user's own`() {
        val library = LayoutLibrary().with(mine)
        assertEquals(Layouts.ALL + mine, library.all)
    }

    @Test
    fun `an empty library is still the built-in catalog`() {
        assertEquals(Layouts.ALL, LayoutLibrary().all)
    }

    @Test
    fun `the catalog is exactly its two halves, and they do not overlap`() {
        // The menu draws a rule between them and marks each half with its own icon, so the halves
        // have to account for the whole list -- a layout in neither would simply not be listed.
        val library = LayoutLibrary().with(mine)
        assertEquals(library.builtIn + library.user, library.all)
        assertEquals(Layouts.ALL, library.builtIn)
        assertTrue((library.builtIn.map { it.id } intersect library.user.map { it.id }).isEmpty())
    }

    @Test
    fun `the two halves are exactly the read-only one and the editable one`() {
        // Which is what makes the icons honest: the plain joystick means built-in and the red one
        // means yours, and "yours" is the same thing as "editable" or the marks say the wrong thing.
        val library = LayoutLibrary().with(mine)
        for (layout in library.builtIn) assertFalse(layout.id, library.isEditable(layout.id))
        for (layout in library.user) assertTrue(layout.id, library.isEditable(layout.id))
    }

    @Test
    fun `layouts are found by id across both halves`() {
        val library = LayoutLibrary().with(mine)
        assertEquals(PS5_LAYOUT, library.byId("ps5"))
        assertEquals(mine, library.byId(mine.id))
        assertNull(library.byId("nothing"))
    }

    // -- Saving ---------------------------------------------------------------------------

    @Test
    fun `saving an existing layout replaces it in place rather than appending`() {
        // The editor saves on every drag, so an appending `with` would grow the library by one
        // row per frame.
        val edited = mine.copy(name = "Renamed")
        val library = LayoutLibrary().with(mine).with(edited)
        assertEquals(listOf(edited), library.user)
    }

    @Test
    fun `saving keeps the layout where it was in the list`() {
        // Otherwise editing the first of three layouts would shuffle it to the bottom of the menu
        // under the user's thumb.
        val second = emptyUserLayout("Second")
        val third = emptyUserLayout("Third")
        val library = LayoutLibrary().with(mine).with(second).with(third)
            .with(second.copy(name = "Edited"))
        assertEquals(listOf("Mine", "Edited", "Third"), library.user.map { it.name })
    }

    @Test
    fun `removing a layout leaves the others alone`() {
        val second = emptyUserLayout("Second")
        val library = LayoutLibrary().with(mine).with(second).without(mine.id)
        assertEquals(listOf(second), library.user)
    }

    // -- New layouts ----------------------------------------------------------------------

    @Test
    fun `a copy keeps everything but its identity`() {
        val copy = PS5_LAYOUT.copyAsUser("My PS5")
        assertEquals("My PS5", copy.name)
        assertNotEquals(PS5_LAYOUT.id, copy.id)
        assertEquals(PS5_LAYOUT.controls, copy.controls)
        // Including the art pack -- a copy of the PS5 pad is a PlayStation-looking pad you can then
        // rearrange, not a grey one.
        assertEquals(PS5_LAYOUT.style, copy.style)
    }

    @Test
    fun `two copies of the same layout are different layouts`() {
        // Ids are what the store and the menu key off, so a derived id would make the second copy
        // silently overwrite the first.
        assertNotEquals(DEFAULT_LAYOUT.copyAsUser("A").id, DEFAULT_LAYOUT.copyAsUser("A").id)
    }

    @Test
    fun `an empty layout has no controls and its own colours`() {
        val empty = emptyUserLayout("Scratch")
        assertTrue(empty.controls.isEmpty())
        assertTrue(empty.style is LayoutStyle.Colors)
    }

    @Test
    fun `names are made unique against the whole catalog, built-ins included`() {
        val library = LayoutLibrary()
        assertEquals("Default 2", library.uniqueName("Default"))
        assertEquals("Mine", library.uniqueName("Mine"))
    }

    @Test
    fun `a name is suffixed as many times as it takes`() {
        val library = LayoutLibrary()
            .with(emptyUserLayout("Pad"))
            .with(emptyUserLayout("Pad 2"))
            .with(emptyUserLayout("Pad 3"))
        assertEquals("Pad 4", library.uniqueName("Pad"))
    }
}
