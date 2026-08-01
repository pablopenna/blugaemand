package com.blugaemand.input

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.ControlId.Side
import com.blugaemand.input.layouts.DEFAULT_LAYOUT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The editor's arithmetic — everything a drag, a pinch, an add and a remove actually do.
 *
 * `EditorScreen` turns fingers into calls on these and nothing else, so this is where the editor is
 * tested. Numbers are round on purpose: a 1000x500 surface makes the layout unit exactly 500 and
 * the grid step exactly 25, which is what lets the expectations below be written down rather than
 * derived.
 *
 * **Controls are addressed by index, not by [ControlId]**, because a layout may hold the same id
 * more than once. Several of the tests below exist only to hold that line.
 */
class LayoutEditsTest {

    private val width = 1000f
    private val height = 500f

    /** unit = min(500, 1000 * 9/16) = 500, so the grid is 25 px. */
    private val step = 25f

    // Indices into the layout below.
    private val south = 0
    private val east = 1
    private val stick = 2
    private val dpad = 3
    private val trigger = 4

    /**
     * - A button   centre (800, 250), radius 50
     * - B button   centre (900, 250), radius 50
     * - left stick centre (200, 250), radius 100, knob 40
     * - D-pad      centre (200, 450), radius 50, dead zone 0.25
     * - trigger    centre (500, 50), 100 x 50
     */
    private val layout = GamepadLayout(
        id = "test",
        name = "Test",
        controls = listOf(
            ControlSpec(
                ControlId.Button(GamepadButton.SOUTH),
                ControlSpec.Shape.Circle(0.8f, 0.5f, radius = 0.1f),
                "A",
            ),
            ControlSpec(
                ControlId.Button(GamepadButton.EAST),
                ControlSpec.Shape.Circle(0.9f, 0.5f, radius = 0.1f),
                "B",
            ),
            ControlSpec(
                ControlId.Stick(Side.LEFT),
                ControlSpec.Shape.Stick(0.2f, 0.5f, radius = 0.2f, knobRadius = 0.08f),
            ),
            ControlSpec(ControlId.Dpad, ControlSpec.Shape.Dpad(0.2f, 0.9f, radius = 0.1f)),
            ControlSpec(
                ControlId.Trigger(Side.LEFT),
                ControlSpec.Shape.Rect(0.5f, 0.1f, width = 0.1f, height = 0.1f),
            ),
        ),
    )

    private fun resolve(of: GamepadLayout = layout) = ResolvedLayout(of, width, height)

    // -- Moving ---------------------------------------------------------------------------

    @Test
    fun `a move takes the control with it and leaves the rest alone`() {
        val moved = resolve().movedControl(south, dxPixels = 50f, dyPixels = -100f, snap = false)
        assertEquals(850f, moved.pixelCenterX(south), TOLERANCE)
        assertEquals(150f, moved.pixelCenterY(south), TOLERANCE)
        assertEquals(
            layout.controls.filterIndexed { i, _ -> i != south },
            moved.controls.filterIndexed { i, _ -> i != south },
        )
    }

    @Test
    fun `moving back where it came from lands exactly where it started`() {
        // Catches the classic slip of dividing both axes by the same dimension: on a 1000x500
        // surface an x delta scaled by the height comes back twice as far as it went.
        val there = resolve().movedControl(south, 137f, -63f, snap = false)
        val back = resolve(there).movedControl(south, -137f, 63f, snap = false)
        assertEquals(layout.controls, back.controls)
    }

    @Test
    fun `a control cannot be dragged so far that part of it leaves the screen`() {
        // Clamping the centre alone would let half a button hang over the edge, where it cannot be
        // touched. The stick's radius is 100, so 100 is as far left as its centre may go.
        val moved = resolve().movedControl(stick, -9999f, -9999f, snap = false)
        assertEquals(100f, moved.pixelCenterX(stick), TOLERANCE)
        assertEquals(100f, moved.pixelCenterY(stick), TOLERANCE)
    }

    @Test
    fun `a rectangle is held in by its own half-extents, which differ per axis`() {
        val moved = resolve().movedControl(trigger, 9999f, 9999f, snap = false)
        // Half-width 50 of a 1000-wide screen, half-height 25 of a 500-tall one.
        assertEquals(950f, moved.pixelCenterX(trigger), TOLERANCE)
        assertEquals(475f, moved.pixelCenterY(trigger), TOLERANCE)
    }

    @Test
    fun `moving an index the layout does not have changes nothing`() {
        assertEquals(layout, resolve().movedControl(99, 50f, 50f, snap = false))
        assertEquals(layout, resolve().movedControl(-1, 50f, 50f, snap = false))
    }

    // -- Snapping -------------------------------------------------------------------------

    @Test
    fun `a snapped move lands on the grid in both axes`() {
        val moved = resolve().movedControl(south, 13f, 7f, snap = true)
        assertOnGrid(moved.pixelCenterX(south))
        assertOnGrid(moved.pixelCenterY(south))
    }

    @Test
    fun `the grid is square in pixels, not in normalised coordinates`() {
        // The whole point of deriving the step from the layout unit. A grid defined in normalised
        // space would step 1/20th of the width horizontally and 1/20th of the height vertically --
        // 50 px against 25 px here -- and two controls both "on the grid" would not line up.
        val resolved = resolve()
        assertEquals(step, resolved.gridStep, TOLERANCE)

        val moved = resolved.movedControl(south, 60f, 60f, snap = true)
        val dx = moved.pixelCenterX(south) - 800f
        val dy = moved.pixelCenterY(south) - 250f
        assertEquals("the same push should move it the same distance either way", dx, dy, TOLERANCE)
    }

    @Test
    fun `an unsnapped move is not quantised at all`() {
        val moved = resolve().movedControl(south, 13f, 0f, snap = false)
        assertEquals(813f, moved.pixelCenterX(south), TOLERANCE)
    }

    // -- Resizing -------------------------------------------------------------------------

    @Test
    fun `a resize scales the control it names`() {
        val bigger = resolve().resizedControl(south, factor = 1.5f, snap = false)
        assertEquals(0.15f, bigger.circle(south).radius, TOLERANCE)
    }

    @Test
    fun `a control cannot be shrunk past the point of being touchable`() {
        val tiny = resolve().resizedControl(south, factor = 0.001f, snap = false)
        assertEquals(MIN_CONTROL_EXTENT, tiny.circle(south).radius, TOLERANCE)
    }

    @Test
    fun `a control cannot be grown past the screen`() {
        val huge = resolve().resizedControl(south, factor = 1000f, snap = false)
        assertEquals(MAX_CONTROL_EXTENT, huge.circle(south).radius, TOLERANCE)
    }

    @Test
    fun `a snapped resize still respects the limits`() {
        // Snapping after clamping can round back outside them, and at the bottom end that is how a
        // control becomes too small to grab hold of again.
        val tiny = resolve().resizedControl(south, factor = 0.001f, snap = true)
        assertTrue(tiny.circle(south).radius >= MIN_CONTROL_EXTENT)
        val huge = resolve().resizedControl(south, factor = 1000f, snap = true)
        assertTrue(huge.circle(south).radius <= MAX_CONTROL_EXTENT)
    }

    @Test
    fun `a stick's cap keeps its proportion to the base`() {
        // Otherwise the knob grows into the well, or disappears inside it.
        val before = layout.stick(stick)
        val after = resolve().resizedControl(stick, factor = 1.5f, snap = false).stick(stick)
        assertEquals(before.knobRadius / before.radius, after.knobRadius / after.radius, TOLERANCE)
        assertNotEquals(before.radius, after.radius)
    }

    @Test
    fun `resizing a D-pad leaves its dead zone alone`() {
        // deadZone is already a fraction *of* the radius, so scaling it too would compound and the
        // dead zone would end up swallowing the cross.
        val before = layout.shapeOf(dpad) as ControlSpec.Shape.Dpad
        val after = resolve().resizedControl(dpad, factor = 2f, snap = false)
            .shapeOf(dpad) as ControlSpec.Shape.Dpad
        assertEquals(before.deadZone, after.deadZone, TOLERANCE)
        assertEquals(0.2f, after.radius, TOLERANCE)
    }

    @Test
    fun `a rectangle scales on both axes`() {
        val before = layout.rect(trigger)
        val after = resolve().resizedControl(trigger, factor = 1.5f, snap = false).rect(trigger)
        assertEquals(before.width * 1.5f, after.width, TOLERANCE)
        assertEquals(before.height * 1.5f, after.height, TOLERANCE)
    }

    @Test
    fun `a rectangle's two extents snap against the same pixel grid`() {
        // width is a fraction of screen width and height a fraction of the layout unit, so snapping
        // them in their own units would put them on two different grids -- 1000-relative against
        // 500-relative -- and a rectangle nudged bigger would change shape as well as size.
        val after = resolve().resizedControl(trigger, factor = 1.3f, snap = true).rect(trigger)
        assertOnGrid(after.width * width)
        assertOnGrid(after.height * height)
    }

    @Test
    fun `resizing an index the layout does not have changes nothing`() {
        assertEquals(layout, resolve().resizedControl(99, 2f, snap = false))
    }

    // -- The same control more than once --------------------------------------------------

    @Test
    fun `a control can be added twice`() {
        // Two A buttons, one under each thumb, is a reasonable pad. Adding used to refuse this.
        val id = ControlId.Button(GamepadButton.SOUTH)
        val twice = layout.withControlAdded(id)
        assertEquals(2, twice.controls.count { it.id == id })
        assertEquals(layout.controls.size + 1, twice.controls.size)
    }

    @Test
    fun `a second copy does not land exactly under the first`() {
        // Perfectly stacked, the new one is invisible and the old one is what a drag would grab.
        val id = ControlId.Button(GamepadButton.SOUTH)
        val added = layout.withControlAdded(id).controls.last()
        val original = layout.controls[south]
        assertNotEquals(original.shape.centerX, added.shape.centerX)
        assertNotEquals(original.shape.centerY, added.shape.centerY)
    }

    @Test
    fun `each further copy steps further away`() {
        val id = ControlId.Button(GamepadButton.SOUTH)
        val three = layout.withControlAdded(id).withControlAdded(id)
        val centres = three.controls.filter { it.id == id }.map { it.shape.centerX }
        assertEquals(3, centres.size)
        assertEquals("no two copies share a position", centres.size, centres.toSet().size)
    }

    @Test
    fun `moving one copy leaves the other where it is`() {
        // The point of addressing controls by index. Keyed by id, this would move both.
        val id = ControlId.Button(GamepadButton.SOUTH)
        val twice = layout.withControlAdded(id)
        val copyAt = twice.controls.lastIndex
        val moved = resolve(twice).movedControl(copyAt, 100f, 0f, snap = false)
        assertEquals(layout.controls[south], moved.controls[south])
    }

    @Test
    fun `removing one copy leaves the other`() {
        val id = ControlId.Button(GamepadButton.SOUTH)
        val twice = layout.withControlAdded(id)
        val left = twice.withControlRemovedAt(twice.controls.lastIndex)
        assertEquals(layout.controls, left.controls)
    }

    // -- Adding and removing --------------------------------------------------------------

    @Test
    fun `adding then removing gets back exactly what was there`() {
        val guide = ControlId.Button(GamepadButton.GUIDE)
        val added = layout.withControlAdded(guide)
        assertEquals(layout, added.withControlRemovedAt(added.controls.lastIndex))
    }

    @Test
    fun `a control arrives at the size and label the default layout gives it`() {
        // The position is chosen when it is placed; the size and label are not, and coming from the
        // default pad is what stops a fresh control arriving as an unlabelled speck.
        val empty = GamepadLayout(id = "e", name = "e", controls = emptyList())
        for (spec in DEFAULT_LAYOUT.controls) {
            val added = empty.withControlAdded(spec.id).controls.single()
            assertEquals(spec.id.toString(), spec.label, added.label)
            assertEquals(spec.id.toString(), spec.shape, added.shape)
        }
    }

    @Test
    fun `every control the editor offers can actually be added`() {
        // ControlId.ALL is what the add page lists, and defaultSpecFor throws on anything with no
        // spec and no fallback -- so this is the guard on adding an id and forgetting to give it a
        // home.
        val empty = GamepadLayout(id = "e", name = "e", controls = emptyList())
        val built = ControlId.ALL.fold(empty) { acc, id -> acc.withControlAdded(id) }
        assertEquals(ControlId.ALL.size, built.controls.size)
    }

    @Test
    fun `removing an index that is not there is not an error`() {
        assertEquals(layout, layout.withControlRemovedAt(99))
        assertEquals(layout, layout.withControlRemovedAt(-1))
    }

    // -- Empty layouts --------------------------------------------------------------------

    @Test
    fun `a layout with nothing on it resolves and hit-tests without complaint`() {
        // The editor can create one, and it is the state a new layout spends its first moments in.
        val empty = ResolvedLayout(
            GamepadLayout(id = "e", name = "e", controls = emptyList()),
            width,
            height,
        )
        assertTrue(empty.controls.isEmpty())
        assertNull(empty.hitTest(500f, 250f))
    }

    @Test
    fun `resolved controls know where they sit in the layout`() {
        // The index is the whole identity story; if resolving ever reorders, everything above lies.
        assertEquals(layout.controls.indices.toList(), resolve().controls.map { it.index })
    }

    // -- Naming ---------------------------------------------------------------------------

    @Test
    fun `controls are named for a list, not for a button face`() {
        // The default layout labels its sticks L and R, which is right on the pad and useless in a
        // menu; buttons keep their label, because that is the letter the host reports.
        assertEquals("A", ControlId.Button(GamepadButton.SOUTH).describe())
        assertEquals("Left stick", ControlId.Stick(Side.LEFT).describe())
        assertEquals("Right trigger", ControlId.Trigger(Side.RIGHT).describe())
        assertEquals("D-pad", ControlId.Dpad.describe())
    }

    @Test
    fun `every control can be named`() {
        for (id in ControlId.ALL) assertTrue(id.toString(), id.describe().isNotEmpty())
    }

    // -- Helpers --------------------------------------------------------------------------

    private fun assertOnGrid(pixels: Float) {
        val offGrid = abs(pixels - snapToGrid(pixels, step))
        assertTrue("$pixels is not a multiple of $step", offGrid < TOLERANCE)
    }

    private fun GamepadLayout.shapeOf(index: Int) = controls[index].shape
    private fun GamepadLayout.circle(index: Int) = shapeOf(index) as ControlSpec.Shape.Circle
    private fun GamepadLayout.stick(index: Int) = shapeOf(index) as ControlSpec.Shape.Stick
    private fun GamepadLayout.rect(index: Int) = shapeOf(index) as ControlSpec.Shape.Rect
    private fun GamepadLayout.pixelCenterX(index: Int) = shapeOf(index).centerX * width
    private fun GamepadLayout.pixelCenterY(index: Int) = shapeOf(index).centerY * height

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}
