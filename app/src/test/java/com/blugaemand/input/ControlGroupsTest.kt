package com.blugaemand.input

import com.blugaemand.hid.GamepadButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The catalog of control groups.
 *
 * Most of these take their geometry from `DEFAULT_LAYOUT`, so they are as much a test that the
 * derivation still finds what it is reaching for as of the arrangements themselves — a control
 * renamed or dropped from the default pad fails here rather than at the moment someone taps the
 * group that wanted it.
 */
class ControlGroupsTest {

    @Test
    fun `the catalog is not empty and every group has a distinct name`() {
        // The name is all the menu shows, so two the same would be two rows you cannot choose
        // between.
        val names = ControlGroups.ALL.map { it.name }
        assertTrue(names.isNotEmpty())
        assertEquals("duplicate group names in $names", names.size, names.toSet().size)
    }

    @Test
    fun `a group is more than one control`() {
        // One control is what the plain add page is for; a group of one would be a duplicate entry
        // wearing a different name.
        for (group in ControlGroups.ALL) {
            assertTrue("${group.name} has ${group.controls.size}", group.controls.size > 1)
        }
    }

    @Test
    fun `a group holds each control only once`() {
        // Layouts may repeat a control, but a group that placed two of the same on top of each
        // other would just be hiding one.
        for (group in ControlGroups.ALL) {
            val ids = group.controls.map { it.id }
            assertEquals(group.name, ids.size, ids.toSet().size)
        }
    }

    @Test
    fun `every group is centred on the origin`() {
        // This is what makes a group land where it was dropped. Offsets are relative to the drop
        // point, so a bounding box that is not centred on zero puts the group beside the thumb.
        for (group in ControlGroups.ALL) {
            val midX = (group.controls.minOf { it.shape.centerX } +
                group.controls.maxOf { it.shape.centerX }) / 2f
            val midY = (group.controls.minOf { it.shape.centerY } +
                group.controls.maxOf { it.shape.centerY }) / 2f
            assertTrue("${group.name} x is off centre by $midX", abs(midX) < TOLERANCE)
            assertTrue("${group.name} y is off centre by $midY", abs(midY) < TOLERANCE)
        }
    }

    @Test
    fun `no two controls in a group sit on top of each other`() {
        for (group in ControlGroups.ALL) {
            val centres = group.controls.map { it.shape.centerX to it.shape.centerY }
            assertEquals(group.name, centres.size, centres.toSet().size)
        }
    }

    @Test
    fun `the face group is the whole diamond`() {
        val group = ControlGroups.ALL.first { it.name == "Face buttons" }
        assertEquals(
            setOf(
                ControlId.Button(GamepadButton.WEST),
                ControlId.Button(GamepadButton.NORTH),
                ControlId.Button(GamepadButton.EAST),
                ControlId.Button(GamepadButton.SOUTH),
            ),
            group.controls.map { it.id }.toSet(),
        )
    }

    @Test
    fun `the D-pad group is four arms, one per direction`() {
        val group = ControlGroups.ALL.first { it.name.startsWith("D-pad") }
        assertEquals(
            ControlId.Direction.entries.map { ControlId.DpadButton(it) }.toSet(),
            group.controls.map { it.id }.toSet(),
        )
    }

    @Test
    fun `stacked shoulders share a column and side-by-side ones share a row`() {
        // The whole difference between the two arrangements, and the reason both exist.
        for (side in listOf("Left", "Right")) {
            val stacked = ControlGroups.ALL.first { it.name == "$side shoulders, stacked" }
            val rows = stacked.controls.map { it.shape.centerY }
            val columns = stacked.controls.map { it.shape.centerX }
            assertTrue("$side stacked shares a column", columns.toSet().size == 1)
            assertEquals("$side stacked uses distinct rows", rows.size, rows.toSet().size)

            val beside = ControlGroups.ALL.first { it.name == "$side shoulders, side by side" }
            assertTrue("$side side-by-side shares a row", beside.controls.map { it.shape.centerY }.toSet().size == 1)
            assertEquals(
                "$side side-by-side uses distinct columns",
                beside.controls.size,
                beside.controls.map { it.shape.centerX }.toSet().size,
            )
        }
    }

    @Test
    fun `both shoulder arrangements carry the same two controls`() {
        for (side in listOf("Left", "Right")) {
            val stacked = ControlGroups.ALL.first { it.name == "$side shoulders, stacked" }
            val beside = ControlGroups.ALL.first { it.name == "$side shoulders, side by side" }
            assertEquals(
                beside.controls.map { it.id }.toSet(),
                stacked.controls.map { it.id }.toSet(),
            )
        }
    }

    private companion object {
        const val TOLERANCE = 1e-5f
    }
}
