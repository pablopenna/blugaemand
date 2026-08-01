package com.blugaemand.input

import com.blugaemand.hid.GamepadButton
import com.blugaemand.hid.GamepadState
import com.blugaemand.hid.Hat
import com.blugaemand.input.ControlId.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class TouchRouterTest {

    /**
     * A deliberately simple layout with round numbers, resolved against a 1000x500 surface:
     *
     * - left stick   centre (200, 250), radius 100
     * - A button     centre (800, 250), radius 50
     * - B button     centre (900, 250), radius 50
     * - D-pad        centre (200, 450), radius 50, dead zone 12.5
     * - left trigger centre (500, 50),  half-extents 50 x 25
     */
    private val layout = GamepadLayout(
        id = "test",
        name = "Test",
        controls = listOf(
            ControlSpec(
                ControlId.Stick(Side.LEFT),
                ControlSpec.Shape.Stick(0.2f, 0.5f, radius = 0.2f, knobRadius = 0.08f),
            ),
            ControlSpec(
                ControlId.Button(GamepadButton.A),
                ControlSpec.Shape.Circle(0.8f, 0.5f, radius = 0.1f),
            ),
            ControlSpec(
                ControlId.Button(GamepadButton.B),
                ControlSpec.Shape.Circle(0.9f, 0.5f, radius = 0.1f),
            ),
            ControlSpec(ControlId.Dpad, ControlSpec.Shape.Dpad(0.2f, 0.9f, radius = 0.1f)),
            ControlSpec(
                ControlId.Trigger(Side.LEFT),
                ControlSpec.Shape.Rect(0.5f, 0.1f, width = 0.1f, height = 0.1f),
            ),
        ),
    )

    private val resolved = ResolvedLayout(layout, width = 1000f, height = 500f)

    private fun router() = TouchRouter(resolved)

    // -- Binding behaviour ----------------------------------------------------------------

    @Test
    fun `touching a button presses it`() {
        val router = router()
        assertTrue(router.down(1, 800f, 250f))
        assertTrue(router.state().isPressed(GamepadButton.A))
    }

    @Test
    fun `touching empty space binds nothing`() {
        val router = router()
        assertFalse(router.down(1, 500f, 250f))
        assertEquals(GamepadState.NEUTRAL, router.state())
    }

    @Test
    fun `a pointer stays bound after sliding off the control`() {
        // Physical buttons do not release when your thumb rolls off the edge, and neither should
        // these — otherwise a stick gets stolen mid-swing by whatever it passes over.
        val router = router()
        router.down(1, 800f, 250f)
        router.move(1, 0f, 0f)

        val state = router.state()
        assertTrue("A should still be held", state.isPressed(GamepadButton.A))
        assertFalse("sliding onto empty space must not press anything else", state.isPressed(GamepadButton.B))
    }

    @Test
    fun `a pointer sliding onto a neighbour does not switch controls`() {
        val router = router()
        router.down(1, 800f, 250f)
        router.move(1, 900f, 250f) // directly over B

        val state = router.state()
        assertTrue(state.isPressed(GamepadButton.A))
        assertFalse(state.isPressed(GamepadButton.B))
    }

    @Test
    fun `lifting a pointer releases its control`() {
        val router = router()
        router.down(1, 800f, 250f)
        router.up(1)
        assertEquals(GamepadState.NEUTRAL, router.state())
    }

    @Test
    fun `moving an unbound pointer changes nothing`() {
        val router = router()
        router.move(99, 800f, 250f)
        assertEquals(GamepadState.NEUTRAL, router.state())
    }

    @Test
    fun `reset releases everything`() {
        val router = router()
        router.down(1, 800f, 250f)
        router.down(2, 200f, 250f)
        router.reset()
        assertEquals(GamepadState.NEUTRAL, router.state())
        assertTrue(router.activeControls().isEmpty())
    }

    // -- Multitouch -----------------------------------------------------------------------

    @Test
    fun `two pointers drive two controls independently`() {
        val router = router()
        router.down(1, 300f, 250f) // left stick, pushed fully right
        router.down(2, 800f, 250f) // A

        val state = router.state()
        assertEquals(GamepadState.AXIS_MAX, state.leftStickX)
        assertEquals(GamepadState.AXIS_CENTER, state.leftStickY)
        assertTrue(state.isPressed(GamepadButton.A))

        // Releasing one must leave the other untouched.
        router.up(2)
        val after = router.state()
        assertEquals(GamepadState.AXIS_MAX, after.leftStickX)
        assertFalse(after.isPressed(GamepadButton.A))
    }

    @Test
    fun `four simultaneous pointers all register`() {
        val router = router()
        router.down(1, 200f, 250f) // stick
        router.down(2, 800f, 250f) // A
        router.down(3, 900f, 250f) // B
        router.down(4, 500f, 50f) // left trigger

        val state = router.state()
        assertTrue(state.isPressed(GamepadButton.A))
        assertTrue(state.isPressed(GamepadButton.B))
        assertEquals(GamepadState.AXIS_MAX, state.leftTrigger)
        assertEquals(4, router.activeControls().size)
    }

    // -- Sticks ---------------------------------------------------------------------------

    @Test
    fun `a stick at rest reports centre`() {
        val router = router()
        router.down(1, 200f, 250f)
        val state = router.state()
        assertEquals(GamepadState.AXIS_CENTER, state.leftStickX)
        assertEquals(GamepadState.AXIS_CENTER, state.leftStickY)
    }

    @Test
    fun `a stick pushed to its edge reads full deflection`() {
        val router = router()
        router.down(1, 200f, 250f)

        router.move(1, 300f, 250f) // +100px right = exactly the radius
        assertEquals(GamepadState.AXIS_MAX, router.state().leftStickX)

        router.move(1, 200f, 150f) // 100px up
        assertEquals(GamepadState.AXIS_MIN, router.state().leftStickY)
    }

    @Test
    fun `a stick dragged past its edge clamps instead of overflowing`() {
        val router = router()
        router.down(1, 200f, 250f)
        router.move(1, 5000f, 250f)

        val state = router.state()
        assertEquals(GamepadState.AXIS_MAX, state.leftStickX)
        assertEquals(GamepadState.AXIS_CENTER, state.leftStickY)
    }

    @Test
    fun `diagonal deflection is clamped to the circle, not the square`() {
        val router = router()
        router.down(1, 200f, 250f)
        router.move(1, 300f, 350f) // +100, +100: outside the radius-100 circle

        val state = router.state()
        // Both components land on the unit circle at 45 degrees, i.e. ~0.707 of full travel.
        val expected = GamepadState.axisFromUnit(0.7071f)
        assertEquals(expected, state.leftStickX)
        assertEquals(expected, state.leftStickY)
    }

    @Test
    fun `stick offset is reported for rendering and cleared on release`() {
        val router = router()
        assertNull(router.stickOffset(Side.LEFT))

        router.down(1, 200f, 250f)
        router.move(1, 300f, 250f)
        assertEquals(1f, router.stickOffset(Side.LEFT)!!.first, 0.001f)
        assertEquals(0f, router.stickOffset(Side.LEFT)!!.second, 0.001f)

        router.up(1)
        assertNull(router.stickOffset(Side.LEFT))
    }

    @Test
    fun `the right stick is unaffected by the left`() {
        val router = router()
        router.down(1, 200f, 250f)
        router.move(1, 300f, 250f)

        val state = router.state()
        assertEquals(GamepadState.AXIS_CENTER, state.rightStickX)
        assertEquals(GamepadState.AXIS_CENTER, state.rightStickY)
        assertNull(router.stickOffset(Side.RIGHT))
    }

    // -- D-pad ----------------------------------------------------------------------------

    @Test
    fun `the dpad dead zone reports centre`() {
        val router = router()
        router.down(1, 200f, 450f) // dead centre
        assertEquals(Hat.CENTER, router.state().hat)
    }

    @Test
    fun `each dpad sector maps to its compass direction`() {
        // Offsets from the D-pad centre at (200, 450); y grows downwards.
        val cases = listOf(
            Triple(0f, -40f, Hat.NORTH),
            Triple(30f, -30f, Hat.NORTH_EAST),
            Triple(40f, 0f, Hat.EAST),
            Triple(30f, 30f, Hat.SOUTH_EAST),
            Triple(0f, 40f, Hat.SOUTH),
            Triple(-30f, 30f, Hat.SOUTH_WEST),
            Triple(-40f, 0f, Hat.WEST),
            Triple(-30f, -30f, Hat.NORTH_WEST),
        )

        for ((dx, dy, expected) in cases) {
            val router = router()
            router.down(1, 200f + dx, 450f + dy)
            assertEquals("offset ($dx, $dy)", expected, router.state().hat)
        }
    }

    @Test
    fun `the dpad hit area is square so corner presses register`() {
        val router = router()
        // (245, 495) is 45px right and 45px down: inside the bounding square, outside a circle of
        // radius 50. A round hit area would drop diagonal presses here.
        assertTrue(router.down(1, 245f, 495f))
        assertEquals(Hat.SOUTH_EAST, router.state().hat)
    }

    // -- Triggers -------------------------------------------------------------------------

    @Test
    fun `a trigger reads fully pulled while touched`() {
        val router = router()
        router.down(1, 500f, 50f)
        assertEquals(GamepadState.AXIS_MAX, router.state().leftTrigger)
        assertEquals(GamepadState.AXIS_MIN, router.state().rightTrigger)
    }

    @Test
    fun `rectangular controls use their own half-extents for hit testing`() {
        val router = router()
        assertTrue("inside the rectangle", router.down(1, 549f, 50f))
        router.reset()
        assertFalse("just past the right edge", router.down(1, 551f, 50f))
        router.reset()
        assertFalse("just past the bottom edge", router.down(1, 500f, 76f))
    }

    // -- Layout sanity --------------------------------------------------------------------

    // Run over the whole catalog rather than XBOX_DEFAULT alone, so every layout the menu can
    // offer inherits these checks the moment it is added.

    @Test
    fun `the layout catalog has unique ids and includes the default`() {
        // The menu ticks the active layout by id, so a duplicate would tick two rows at once.
        val ids = GamepadLayout.ALL.map { it.id }
        assertEquals("duplicate layout ids in $ids", ids.size, ids.toSet().size)
        assertTrue("the default is offered", GamepadLayout.XBOX_DEFAULT in GamepadLayout.ALL)
    }

    @Test
    fun `every built-in layout exposes every button the profile declares`() {
        for (layout in GamepadLayout.ALL) {
            assertEquals(layout.id, emptySet<GamepadButton>(), layout.missingButtons())
        }
    }

    @Test
    fun `the built-in layout crosses X and Y onto the slots hosts report them as`() {
        // BTN_X aliases BTN_NORTH and BTN_Y aliases BTN_WEST, so the key labelled Y has to drive
        // GamepadButton.X for the host to report a Y. Wiring each key to its same-letter slot is
        // the obvious-looking mistake, and it swaps the two on every host.
        val faces = GamepadLayout.XBOX_DEFAULT.controls.associateBy { it.label }
        val north = faces.getValue("Y")
        val west = faces.getValue("X")

        assertEquals(ControlId.Button(GamepadButton.X), north.id)
        assertEquals(ControlId.Button(GamepadButton.Y), west.id)

        // Guards the premise: Y really is the northern key and X the western one.
        assertTrue("Y sits above X", north.shape.centerY < west.shape.centerY)
        assertTrue("X sits left of Y", west.shape.centerX < north.shape.centerX)
    }

    @Test
    fun `no built-in layout has overlapping controls`() {
        // Overlapping touch areas make hit-testing ambiguous and the pad frustrating to use.
        // Checked at a typical 1080p landscape size and at a squarer tablet ratio, since circular
        // controls size themselves from height while positions are relative to both axes.
        for (layout in GamepadLayout.ALL) {
            for ((w, h) in listOf(2400f to 1080f, 1600f to 1200f)) {
                val pad = ResolvedLayout(layout, width = w, height = h)
                for (i in pad.controls.indices) {
                    for (j in i + 1 until pad.controls.size) {
                        val a = pad.controls[i]
                        val b = pad.controls[j]
                        assertFalse(
                            "${layout.id}: ${a.id} overlaps ${b.id} at ${w.toInt()}x${h.toInt()}",
                            overlaps(a, b),
                        )
                    }
                }
            }
        }
    }

    /**
     * Matches how [ResolvedControl.contains] actually tests each shape: two round controls only
     * collide when their centres are closer than the sum of their radii, while anything involving
     * a rectangle or the square D-pad area is an axis-aligned box test.
     */
    private fun overlaps(a: ResolvedControl, b: ResolvedControl): Boolean =
        if (a.isRound() && b.isRound()) {
            hypot(a.centerX - b.centerX, a.centerY - b.centerY) < a.radius + b.radius
        } else {
            val (aw, ah) = a.halfExtents()
            val (bw, bh) = b.halfExtents()
            abs(a.centerX - b.centerX) < aw + bw && abs(a.centerY - b.centerY) < ah + bh
        }

    private fun ResolvedControl.isRound(): Boolean =
        spec.shape is ControlSpec.Shape.Circle || spec.shape is ControlSpec.Shape.Stick

    private fun ResolvedControl.halfExtents(): Pair<Float, Float> =
        if (radius > 0f) radius to radius else halfWidth to halfHeight
}
