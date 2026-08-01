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
 */
class LayoutEditsTest {

    private val width = 1000f
    private val height = 500f

    /** unit = min(500, 1000 * 9/16) = 500, so the grid is 25 px. */
    private val step = 25f

    private val south = ControlId.Button(GamepadButton.SOUTH)

    /**
     * - A button  centre (800, 250), radius 50
     * - B button  centre (900, 250), radius 50
     * - left stick centre (200, 250), radius 100, knob 40
     * - D-pad     centre (200, 450), radius 50, dead zone 0.25
     * - trigger   centre (500, 50), 100 x 50
     */
    private val layout = GamepadLayout(
        id = "test",
        name = "Test",
        controls = listOf(
            ControlSpec(south, ControlSpec.Shape.Circle(0.8f, 0.5f, radius = 0.1f), "A"),
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
            layout.controls.filterNot { it.id == south },
            moved.controls.filterNot { it.id == south },
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
        val moved = resolve().movedControl(ControlId.Stick(Side.LEFT), -9999f, -9999f, snap = false)
        assertEquals(100f, moved.pixelCenterX(ControlId.Stick(Side.LEFT)), TOLERANCE)
        assertEquals(100f, moved.pixelCenterY(ControlId.Stick(Side.LEFT)), TOLERANCE)
    }

    @Test
    fun `a rectangle is held in by its own half-extents, which differ per axis`() {
        val trigger = ControlId.Trigger(Side.LEFT)
        val moved = resolve().movedControl(trigger, 9999f, 9999f, snap = false)
        // Half-width 50 of a 1000-wide screen, half-height 25 of a 500-tall one.
        assertEquals(950f, moved.pixelCenterX(trigger), TOLERANCE)
        assertEquals(475f, moved.pixelCenterY(trigger), TOLERANCE)
    }

    @Test
    fun `moving something the layout does not have changes nothing`() {
        val absent = ControlId.Button(GamepadButton.GUIDE)
        assertEquals(layout, resolve().movedControl(absent, 50f, 50f, snap = false))
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
        val stick = ControlId.Stick(Side.LEFT)
        val before = layout.stick(stick)
        val after = resolve().resizedControl(stick, factor = 1.5f, snap = false).stick(stick)
        assertEquals(
            before.knobRadius / before.radius,
            after.knobRadius / after.radius,
            TOLERANCE,
        )
        assertNotEquals(before.radius, after.radius)
    }

    @Test
    fun `resizing a D-pad leaves its dead zone alone`() {
        // deadZone is already a fraction *of* the radius, so scaling it too would compound and the
        // dead zone would end up swallowing the cross.
        val resized = resolve().resizedControl(ControlId.Dpad, factor = 2f, snap = false)
        val before = layout.controls.first { it.id == ControlId.Dpad }.shape
        val after = resized.controls.first { it.id == ControlId.Dpad }.shape
        before as ControlSpec.Shape.Dpad
        after as ControlSpec.Shape.Dpad
        assertEquals(before.deadZone, after.deadZone, TOLERANCE)
        assertEquals(0.2f, after.radius, TOLERANCE)
    }

    @Test
    fun `a rectangle scales on both axes`() {
        val trigger = ControlId.Trigger(Side.LEFT)
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
        val trigger = ControlId.Trigger(Side.LEFT)
        val after = resolve().resizedControl(trigger, factor = 1.3f, snap = true).rect(trigger)
        assertOnGrid(after.width * width)
        assertOnGrid(after.height * height)
    }

    @Test
    fun `resizing something the layout does not have changes nothing`() {
        val absent = ControlId.Button(GamepadButton.GUIDE)
        assertEquals(layout, resolve().resizedControl(absent, 2f, snap = false))
    }

    // -- Adding and removing --------------------------------------------------------------

    @Test
    fun `adding then removing gets back exactly what was there`() {
        val guide = ControlId.Button(GamepadButton.GUIDE)
        assertEquals(layout, layout.withControlAdded(guide).withControlRemoved(guide))
    }

    @Test
    fun `a control is added where the default layout has it`() {
        // Which is what makes building an empty layout up one control at a time reconstruct the
        // default pad, rather than pile everything in the middle of the screen.
        val empty = GamepadLayout(id = "e", name = "e", controls = emptyList())
        val built = ControlId.ALL.fold(empty) { acc, id -> acc.withControlAdded(id) }
        for (spec in DEFAULT_LAYOUT.controls) {
            assertEquals(spec.id.toString(), spec, built.controls.first { it.id == spec.id })
        }
    }

    @Test
    fun `a control added back returns to where it was, not to the default`() {
        val moved = resolve().movedControl(south, 50f, 0f, snap = false)
        // Removing loses the position -- there is nowhere to keep it -- so it comes back from the
        // default. Pinned because the alternative (remembering) is a feature, not an accident.
        val roundTrip = moved.withControlRemoved(south).withControlAdded(south)
        assertEquals(
            DEFAULT_LAYOUT.controls.first { it.id == south },
            roundTrip.controls.first { it.id == south },
        )
    }

    @Test
    fun `adding something already there is not a way to get two of it`() {
        assertEquals(layout, layout.withControlAdded(south))
    }

    @Test
    fun `removing something that is not there is not an error`() {
        assertEquals(layout, layout.withControlRemoved(ControlId.Button(GamepadButton.GUIDE)))
    }

    @Test
    fun `the default layout is missing only the two digital trigger buttons`() {
        // It reaches the triggers through ControlId.Trigger instead, so L2 and R2 are the only two
        // of ControlId.ALL it does not place -- and so the only two that need a fallback spec.
        assertEquals(
            setOf(
                ControlId.Button(GamepadButton.L2),
                ControlId.Button(GamepadButton.R2),
            ),
            DEFAULT_LAYOUT.addableControls().toSet(),
        )
    }

    @Test
    fun `the two controls with no home in the default layout still get a sensible one`() {
        val l2 = ControlId.Button(GamepadButton.L2)
        val added = DEFAULT_LAYOUT.withControlAdded(l2).controls.first { it.id == l2 }
        assertEquals("L2", added.label)
        assertTrue(added.shape.centerX in 0f..1f && added.shape.centerY in 0f..1f)
    }

    @Test
    fun `a full layout has nothing left to add`() {
        val full = ControlId.ALL.fold(layout) { acc, id -> acc.withControlAdded(id) }
        assertTrue(full.addableControls().isEmpty())
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
        assertEquals(ControlId.ALL, empty.layout.addableControls())
    }

    // -- Naming ---------------------------------------------------------------------------

    @Test
    fun `controls are named for a list, not for a button face`() {
        // The default layout labels its sticks L and R, which is right on the pad and useless in a
        // menu; buttons keep their label, because that is the letter the host reports.
        assertEquals("A", south.describe())
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

    private fun GamepadLayout.shapeOf(id: ControlId) = controls.first { it.id == id }.shape
    private fun GamepadLayout.circle(id: ControlId) = shapeOf(id) as ControlSpec.Shape.Circle
    private fun GamepadLayout.stick(id: ControlId) = shapeOf(id) as ControlSpec.Shape.Stick
    private fun GamepadLayout.rect(id: ControlId) = shapeOf(id) as ControlSpec.Shape.Rect
    private fun GamepadLayout.pixelCenterX(id: ControlId) = shapeOf(id).centerX * width
    private fun GamepadLayout.pixelCenterY(id: ControlId) = shapeOf(id).centerY * height

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}
