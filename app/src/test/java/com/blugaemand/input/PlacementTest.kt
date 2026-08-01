package com.blugaemand.input

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.layouts.DEFAULT_LAYOUT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Choosing where something goes before it exists.
 *
 * A control's default position is almost never the wanted one, so adding is pick-then-drop and a
 * group of controls arrives arranged, in one gesture, where the thumb asked for it. The arithmetic
 * is here; `EditorScreen` only turns a finger into a point.
 *
 * Same 1000x500 surface as the other editor tests: layout unit 500, grid step 25.
 */
class PlacementTest {

    private val width = 1000f
    private val height = 500f
    private val step = 25f

    private val empty = GamepadLayout(id = "e", name = "e", controls = emptyList())
    private fun resolve(of: GamepadLayout = empty) = ResolvedLayout(of, width, height)

    // -- A single control -----------------------------------------------------------------

    @Test
    fun `a control lands centred on the point it was dropped on`() {
        val placed = resolve()
            .withPlacement(Placement.of(ControlId.Button(GamepadButton.SOUTH)), 400f, 300f, false)
        val spec = placed.controls.single()
        assertEquals(400f, spec.shape.centerX * width, TOLERANCE)
        assertEquals(300f, spec.shape.centerY * height, TOLERANCE)
    }

    @Test
    fun `a control keeps the size and label the default layout gives it`() {
        // Only the position is chosen; taking the size too is what stops a fresh control arriving
        // as an unlabelled speck.
        val id = ControlId.Button(GamepadButton.SOUTH)
        val fromDefault = DEFAULT_LAYOUT.controls.first { it.id == id }
        val placed = resolve().withPlacement(Placement.of(id), 400f, 300f, false).controls.single()

        assertEquals(fromDefault.label, placed.label)
        assertEquals(
            (fromDefault.shape as ControlSpec.Shape.Circle).radius,
            (placed.shape as ControlSpec.Shape.Circle).radius,
            TOLERANCE,
        )
    }

    @Test
    fun `placing appends rather than replacing`() {
        val one = resolve().withPlacement(Placement.of(ControlId.Dpad), 200f, 200f, false)
        val two = resolve(one).withPlacement(Placement.of(ControlId.Dpad), 700f, 200f, false)
        assertEquals(2, two.controls.size)
        assertEquals(one.controls[0], two.controls[0])
    }

    @Test
    fun `the same control can be placed twice, in different spots`() {
        val id = ControlId.Button(GamepadButton.SOUTH)
        val one = resolve().withPlacement(Placement.of(id), 200f, 200f, false)
        val two = resolve(one).withPlacement(Placement.of(id), 700f, 400f, false)
        assertEquals(2, two.controls.count { it.id == id })
        assertNotEquals(two.controls[0].shape.centerX, two.controls[1].shape.centerX)
    }

    // -- Groups ---------------------------------------------------------------------------

    @Test
    fun `a group lands centred on the point, not beside it`() {
        // Centred on the bounding box rather than the average position: with an odd member out the
        // average drifts to the crowded side and the group lands off the thumb.
        for (group in ControlGroups.ALL) {
            val placed = resolve().withPlacement(group, 500f, 250f, false).controls
            val midX = (placed.minOf { it.shape.centerX } + placed.maxOf { it.shape.centerX }) / 2f
            val midY = (placed.minOf { it.shape.centerY } + placed.maxOf { it.shape.centerY }) / 2f
            assertEquals(group.name, 500f, midX * width, 1f)
            assertEquals(group.name, 250f, midY * height, 1f)
        }
    }

    @Test
    fun `a group keeps its arrangement wherever it is dropped`() {
        // Moving the drop point must translate the whole thing, not reshape it.
        val group = ControlGroups.ALL.first { it.name == "Face buttons" }
        val here = resolve().withPlacement(group, 300f, 200f, false).controls
        val there = resolve().withPlacement(group, 700f, 300f, false).controls

        val hereOffsets = here.map { it.shape.centerX - here[0].shape.centerX }
        val thereOffsets = there.map { it.shape.centerX - there[0].shape.centerX }
        for (i in hereOffsets.indices) {
            assertEquals(hereOffsets[i], thereOffsets[i], TOLERANCE)
        }
    }

    @Test
    fun `a group snaps as a unit rather than member by member`() {
        // Snapping each member on its own would pull the arrangement out of shape; the drop point
        // is snapped once and everything keeps its offset from it.
        val group = ControlGroups.ALL.first { it.name == "Face buttons" }
        val loose = resolve().withPlacement(group, 507f, 263f, false).controls
        val snapped = resolve().withPlacement(group, 507f, 263f, true).controls

        // Every member shifted by the same amount, so the shape is untouched.
        val dx = snapped[0].shape.centerX - loose[0].shape.centerX
        val dy = snapped[0].shape.centerY - loose[0].shape.centerY
        for (i in loose.indices) {
            assertEquals(loose[i].shape.centerX + dx, snapped[i].shape.centerX, TOLERANCE)
            assertEquals(loose[i].shape.centerY + dy, snapped[i].shape.centerY, TOLERANCE)
        }

        // And what it shifted onto is the grid: 507 rounds to 500, 263 to 275.
        val midX = (snapped.minOf { it.shape.centerX } + snapped.maxOf { it.shape.centerX }) / 2f
        val midY = (snapped.minOf { it.shape.centerY } + snapped.maxOf { it.shape.centerY }) / 2f
        assertOnGrid(midX * width)
        assertOnGrid(midY * height)
    }

    @Test
    fun `every group can be placed and brings all of its controls`() {
        for (group in ControlGroups.ALL) {
            val placed = resolve().withPlacement(group, 500f, 250f, true)
            assertEquals(group.name, group.controls.size, placed.controls.size)
        }
    }

    // -- Edges ----------------------------------------------------------------------------

    @Test
    fun `something dropped off the edge is pulled back fully on screen`() {
        val placed = resolve()
            .withPlacement(Placement.of(ControlId.Stick(ControlId.Side.LEFT)), -500f, -500f, false)
        val resolved = resolve(placed).controls.single()
        assertTrue("left edge", resolved.centerX - resolved.radius >= -TOLERANCE)
        assertTrue("top edge", resolved.centerY - resolved.radius >= -TOLERANCE)
    }

    @Test
    fun `a group dropped in the corner keeps every member on screen`() {
        for (group in ControlGroups.ALL) {
            val placed = resolve().withPlacement(group, 9999f, 9999f, false)
            for (control in resolve(placed).controls) {
                val insetX = if (control.radius > 0f) control.radius else control.halfWidth
                val insetY = if (control.radius > 0f) control.radius else control.halfHeight
                assertTrue(
                    "${group.name}: ${control.id} runs off the right",
                    control.centerX + insetX <= width + TOLERANCE,
                )
                assertTrue(
                    "${group.name}: ${control.id} runs off the bottom",
                    control.centerY + insetY <= height + TOLERANCE,
                )
            }
        }
    }

    // -- The preview ----------------------------------------------------------------------

    @Test
    fun `the preview shows exactly what placing would add`() {
        // The preview is what a finger is aiming with, so a preview that disagreed with the result
        // would be worse than none.
        val group = ControlGroups.ALL.first { it.name == "Face buttons" }
        val preview = resolve().previewOf(group, 480f, 260f, true)
        val placed = resolve(resolve().withPlacement(group, 480f, 260f, true))
            .controls.takeLast(group.controls.size)

        assertEquals(placed.map { it.spec }, preview.map { it.spec })
    }

    @Test
    fun `the preview does not add anything`() {
        val before = empty
        resolve(before).previewOf(Placement.of(ControlId.Dpad), 400f, 300f, true)
        assertEquals(before, empty)
    }

    // -- Helpers --------------------------------------------------------------------------

    private fun assertOnGrid(pixels: Float) {
        assertTrue("$pixels is not a multiple of $step", abs(pixels - snapToGrid(pixels, step)) < 1f)
    }

    private companion object {
        const val TOLERANCE = 1e-3f
    }
}
