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

    // -- Clustered ------------------------------------------------------------------------

    @Test
    fun `every group can go down as one control carrying the same members`() {
        // The catalog is one list either way round. If the two ever diverge it is because a group
        // was added somewhere that only one of the paths reads.
        for (group in ControlGroups.ALL) {
            val plate = ControlGroups.clustered(group).controls.single()
            val members = (plate.shape as ControlSpec.Shape.Cluster).members
            assertEquals(group.name, ControlId.Cluster, plate.id)
            assertEquals(group.name, group.controls.map { it.id }, members.map { it.id })
        }
    }

    @Test
    fun `a clustered group is centred on the origin like a loose one`() {
        // Same reason as for a loose group: the offset is from the drop point, so a plate whose
        // own centre is not zero lands beside the thumb rather than under it.
        for (group in ControlGroups.ALL) {
            val plate = ControlGroups.clustered(group).controls.single()
            assertTrue("${group.name} x", abs(plate.shape.centerX) < TOLERANCE)
            assertTrue("${group.name} y", abs(plate.shape.centerY) < TOLERANCE)
        }
    }

    @Test
    fun `clustering restates the arrangement in units without changing it`() {
        // Going in, a group's screen-relative numbers are stretched by 16/9 -- the aspect it was
        // authored against -- so that measured against the unit they describe the same arrangement.
        val loose = ControlGroups.ALL.first { it.name == "Face buttons" }
        val members =
            (ControlGroups.clustered(loose).controls.single().shape
                as ControlSpec.Shape.Cluster).members

        for ((was, now) in loose.controls.zip(members)) {
            assertEquals(was.shape.centerX * 16f / 9f, now.shape.centerX, TOLERANCE)
            assertEquals(was.shape.centerY, now.shape.centerY, TOLERANCE)
        }
    }

    @Test
    fun `every group drawn as a plate looks the same as the loose one on 16 by 9`() {
        // The conversion has to be a change of units and nothing else: on the screen the geometry
        // was authored for, a plate must put its members in exactly the pixels the loose group
        // would have. Off 16:9 the two deliberately part company -- the plate stays in shape and
        // the loose group stretches -- which is the whole reason the plate measures in units.
        for (group in ControlGroups.ALL) {
            val loose = ResolvedLayout(
                GamepadLayout("loose", "Loose", group.at(0.5f, 0.5f)),
                1600f,
                900f,
            ).controls

            val plated = ResolvedLayout(
                GamepadLayout("plated", "Plated", ControlGroups.clustered(group).at(0.5f, 0.5f)),
                1600f,
                900f,
            ).controls.single().members

            for ((was, now) in loose.zip(plated)) {
                assertEquals(group.name, was.centerX, now.centerX, PIXEL_TOLERANCE)
                assertEquals(group.name, was.centerY, now.centerY, PIXEL_TOLERANCE)
                assertEquals(group.name, was.extentX, now.extentX, PIXEL_TOLERANCE)
                assertEquals(group.name, was.extentY, now.extentY, PIXEL_TOLERANCE)
            }
        }
    }

    @Test
    fun `a clustered shoulder pair keeps both controls and its arrangement`() {
        // The pair that made a plate have to be more than a diamond of circles: these are
        // rectangles, and a rectangle's width changes what it is measured against on the way in.
        for (side in listOf("Left", "Right")) {
            val loose = ControlGroups.ALL.first { it.name == "$side shoulders, stacked" }
            val members =
                (ControlGroups.clustered(loose).controls.single().shape
                    as ControlSpec.Shape.Cluster).members

            assertEquals(2, members.size)
            assertEquals("$side shares a column", 1, members.map { it.shape.centerX }.toSet().size)
            assertEquals("$side uses two rows", 2, members.map { it.shape.centerY }.toSet().size)
            for ((was, now) in loose.controls.zip(members)) {
                val old = was.shape as ControlSpec.Shape.Rect
                val new = now.shape as ControlSpec.Shape.Rect
                assertEquals(old.width * 16f / 9f, new.width, TOLERANCE)
                assertEquals(old.height, new.height, TOLERANCE)
            }
        }
    }

    private companion object {
        const val TOLERANCE = 1e-5f

        /** A twentieth of a pixel — the float error in dividing by 16/9 and multiplying back. */
        const val PIXEL_TOLERANCE = 0.05f
    }
}
