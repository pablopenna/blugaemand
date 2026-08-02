package com.blugaemand.input

import com.blugaemand.hid.GamepadButton
import com.blugaemand.hid.GamepadState
import com.blugaemand.hid.Hat
import com.blugaemand.input.ControlId.Side
import com.blugaemand.input.layouts.DEFAULT_LAYOUT
import com.blugaemand.input.layouts.Layouts
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
                ControlId.Button(GamepadButton.SOUTH),
                ControlSpec.Shape.Circle(0.8f, 0.5f, radius = 0.1f),
            ),
            ControlSpec(
                ControlId.Button(GamepadButton.EAST),
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

    // Indices into the layout above. Controls are addressed this way rather than by ControlId
    // because a layout may hold the same id more than once.
    private companion object {
        const val STICK = 0
        const val SOUTH_BUTTON = 1

        /** One member of a cluster: offsets and radius are fractions of the layout unit. */
        fun member(button: GamepadButton, dx: Float, dy: Float, radius: Float = 0.1f) =
            ControlSpec(
                ControlId.Button(button),
                ControlSpec.Shape.Circle(dx, dy, radius = radius),
            )
    }

    // -- Binding behaviour ----------------------------------------------------------------

    @Test
    fun `touching a button presses it`() {
        val router = router()
        assertTrue(router.down(1, 800f, 250f))
        assertTrue(router.state().isPressed(GamepadButton.SOUTH))
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
        assertTrue("SOUTH should still be held", state.isPressed(GamepadButton.SOUTH))
        assertFalse("sliding onto empty space must not press anything else", state.isPressed(GamepadButton.EAST))
    }

    @Test
    fun `a pointer sliding onto a neighbour does not switch controls`() {
        val router = router()
        router.down(1, 800f, 250f)
        router.move(1, 900f, 250f) // directly over EAST

        val state = router.state()
        assertTrue(state.isPressed(GamepadButton.SOUTH))
        assertFalse(state.isPressed(GamepadButton.EAST))
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
        assertTrue(state.isPressed(GamepadButton.SOUTH))

        // Releasing one must leave the other untouched.
        router.up(2)
        val after = router.state()
        assertEquals(GamepadState.AXIS_MAX, after.leftStickX)
        assertFalse(after.isPressed(GamepadButton.SOUTH))
    }

    @Test
    fun `four simultaneous pointers all register`() {
        val router = router()
        router.down(1, 200f, 250f) // stick
        router.down(2, 800f, 250f) // A
        router.down(3, 900f, 250f) // B
        router.down(4, 500f, 50f) // left trigger

        val state = router.state()
        assertTrue(state.isPressed(GamepadButton.SOUTH))
        assertTrue(state.isPressed(GamepadButton.EAST))
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
        // By index, not by side: a layout may carry the same control twice, so the renderer asks
        // about the one it is drawing rather than about a side.
        val router = router()
        assertNull(router.stickOffset(STICK))

        router.down(1, 200f, 250f)
        router.move(1, 300f, 250f)
        assertEquals(1f, router.stickOffset(STICK)!!.first, 0.001f)
        assertEquals(0f, router.stickOffset(STICK)!!.second, 0.001f)

        router.up(1)
        assertNull(router.stickOffset(STICK))
    }

    @Test
    fun `the right stick is unaffected by the left`() {
        val router = router()
        router.down(1, 200f, 250f)
        router.move(1, 300f, 250f)

        val state = router.state()
        assertEquals(GamepadState.AXIS_CENTER, state.rightStickX)
        assertEquals(GamepadState.AXIS_CENTER, state.rightStickY)
    }

    @Test
    fun `two copies of one control light independently but both send the button`() {
        // The reason press state is keyed by index. Keyed by ControlId, pressing either of two A
        // buttons would light both -- while the host, correctly, sees one A either way.
        val twin = layout.copy(
            controls = layout.controls + ControlSpec(
                ControlId.Button(GamepadButton.SOUTH),
                ControlSpec.Shape.Circle(0.8f, 0.9f, radius = 0.1f),
                "A",
            ),
        )
        val router = TouchRouter(ResolvedLayout(twin, 1000f, 500f))
        val copyAt = twin.controls.lastIndex

        router.down(1, 800f, 450f) // the second A
        assertEquals(setOf(copyAt), router.activeControls())
        assertTrue(router.state().isPressed(GamepadButton.SOUTH))

        router.down(2, 800f, 250f) // the first A as well
        assertEquals(setOf(copyAt, SOUTH_BUTTON), router.activeControls())
        assertTrue(router.state().isPressed(GamepadButton.SOUTH))

        router.up(1)
        assertEquals(setOf(SOUTH_BUTTON), router.activeControls())
        assertTrue("still held by the other", router.state().isPressed(GamepadButton.SOUTH))
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

    // -- A D-pad made of four buttons -----------------------------------------------------

    /**
     * Four arms on a 1000x500 surface: up (500, 100), down (500, 300), left (400, 200),
     * right (600, 200) — each a circle of radius 50, with no one-piece cross anywhere.
     */
    private val armed = GamepadLayout(
        id = "arms",
        name = "Arms",
        controls = ControlId.Direction.entries.map { direction ->
            val x = when (direction) {
                ControlId.Direction.LEFT -> 0.4f
                ControlId.Direction.RIGHT -> 0.6f
                else -> 0.5f
            }
            val y = when (direction) {
                ControlId.Direction.UP -> 0.2f
                ControlId.Direction.DOWN -> 0.6f
                else -> 0.4f
            }
            ControlSpec(ControlId.DpadButton(direction), ControlSpec.Shape.Circle(x, y, 0.1f))
        },
    )

    private fun armedRouter() = TouchRouter(ResolvedLayout(armed, 1000f, 500f))

    @Test
    fun `one arm of a four-button D-pad reads as that direction`() {
        val router = armedRouter()
        router.down(1, 500f, 100f)
        assertEquals(Hat.NORTH, router.state().hat)
    }

    @Test
    fun `two adjacent arms read as the diagonal between them`() {
        // The thing four separate buttons have to get right, and the reason the hat is settled
        // after the whole binding loop rather than inside it: one arm alone says nothing about it.
        val router = armedRouter()
        router.down(1, 500f, 100f) // up
        router.down(2, 600f, 200f) // right
        assertEquals(Hat.NORTH_EAST, router.state().hat)
    }

    @Test
    fun `opposing arms cancel rather than fighting`() {
        val router = armedRouter()
        router.down(1, 500f, 100f) // up
        router.down(2, 500f, 300f) // down
        assertEquals(Hat.CENTER, router.state().hat)
    }

    @Test
    fun `a four-button D-pad rests centred`() {
        // A hat stuck pointing north at rest is the classic symptom of getting this wrong.
        assertEquals(Hat.CENTER, armedRouter().state().hat)
    }

    @Test
    fun `releasing one of two arms leaves the other pointing`() {
        val router = armedRouter()
        router.down(1, 500f, 100f) // up
        router.down(2, 600f, 200f) // right
        router.up(2)
        assertEquals(Hat.NORTH, router.state().hat)
    }

    @Test
    fun `a held cross beats the arms around it`() {
        // A layout carrying both is one where the cross is the deliberate control; a stray arm
        // should not override the thumb already on it.
        val both = armed.copy(
            controls = armed.controls + ControlSpec(
                ControlId.Dpad,
                ControlSpec.Shape.Dpad(0.2f, 0.9f, radius = 0.1f),
            ),
        )
        val router = TouchRouter(ResolvedLayout(both, 1000f, 500f))
        router.down(1, 500f, 100f) // the up arm -- north
        router.down(2, 200f, 400f) // the cross, pushed north of its centre at (200, 450)
        assertEquals(Hat.NORTH, router.state().hat)

        // And with the cross pushed elsewhere, it is the cross that is heard.
        router.move(2, 250f, 450f) // due east of its centre
        assertEquals(Hat.EAST, router.state().hat)
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

    // -- Clusters -------------------------------------------------------------------------

    /**
     * A face plate at (500, 250) on the same 1000x500 surface, where the unit is 500.
     *
     * Everything inside a plate is a fraction of that unit, so the members land 100 px out with a
     * radius of 50: Y at (400, 250), X at (500, 150), B at (600, 250), A at (500, 350). The plate's
     * own touch area is their bounding box, x 350..650 by y 100..400.
     */
    private val plated = GamepadLayout(
        id = "plated",
        name = "Plated",
        controls = listOf(
            ControlSpec(
                ControlId.Cluster,
                ControlSpec.Shape.Cluster(
                    0.5f,
                    0.5f,
                    members = listOf(
                        member(GamepadButton.WEST, -0.2f, 0f),
                        member(GamepadButton.NORTH, 0f, -0.2f),
                        member(GamepadButton.EAST, 0.2f, 0f),
                        member(GamepadButton.SOUTH, 0f, 0.2f),
                    ),
                ),
            ),
        ),
    )

    private fun platedRouter() = TouchRouter(ResolvedLayout(plated, 1000f, 500f))

    @Test
    fun `a touch on one member of a plate sends that member's button`() {
        val router = platedRouter()
        assertTrue(router.down(1, 600f, 250f))
        assertTrue(router.state().isPressed(GamepadButton.EAST))
        assertFalse(router.state().isPressed(GamepadButton.WEST))
    }

    @Test
    fun `a plate has no dead spots in it`() {
        // The whole bounding box is live, corners included -- the same choice the D-pad cross
        // makes, and the reason a plate is one control rather than four with gaps between them.
        val router = platedRouter()
        assertTrue("dead centre", router.down(1, 500f, 250f))
        assertFalse("dead centre sends nothing", router.state() == GamepadState.NEUTRAL)

        router.reset()
        // Up and left of everything, inside no member's own circle, but nearest X's centre.
        assertTrue("the top-left corner", router.down(1, 380f, 115f))
        assertTrue(router.state().isPressed(GamepadButton.NORTH))
    }

    @Test
    fun `a thumb rolled across a plate changes button without lifting`() {
        // What the plate buys over four separate buttons, and why the member is re-read on every
        // event rather than fixed when the pointer went down.
        val router = platedRouter()
        router.down(1, 600f, 250f)
        assertTrue(router.state().isPressed(GamepadButton.EAST))

        router.move(1, 500f, 350f)
        assertTrue(router.state().isPressed(GamepadButton.SOUTH))
        assertFalse("the one rolled off is released", router.state().isPressed(GamepadButton.EAST))
    }

    @Test
    fun `two thumbs on one plate hold two buttons`() {
        val router = platedRouter()
        router.down(1, 600f, 250f)
        router.down(2, 500f, 350f)

        val state = router.state()
        assertTrue(state.isPressed(GamepadButton.EAST))
        assertTrue(state.isPressed(GamepadButton.SOUTH))
        assertEquals(setOf(2, 3), router.activeMembers(0))
    }

    @Test
    fun `a member's own area beats the midpoint between centres`() {
        // The centre cluster's shape: a large Home between two small buttons. Nearest centre alone
        // would split at 62 px from the middle, which is inside Home's own 75 px circle -- so
        // touching the edge of the picture of Home would have sent Back.
        val uneven = GamepadLayout(
            id = "uneven",
            name = "Uneven",
            controls = listOf(
                ControlSpec(
                    ControlId.Cluster,
                    ControlSpec.Shape.Cluster(
                        0.5f,
                        0.5f,
                        members = listOf(
                            member(GamepadButton.BACK, -0.25f, 0f, radius = 0.05f),
                            member(GamepadButton.GUIDE, 0f, 0f, radius = 0.15f),
                            member(GamepadButton.START, 0.25f, 0f, radius = 0.05f),
                        ),
                    ),
                ),
            ),
        )
        val router = TouchRouter(ResolvedLayout(uneven, 1000f, 500f))

        // 70 px left of centre: still inside Home, but Back's centre is 55 px away and Home's 70.
        router.down(1, 430f, 250f)
        assertTrue(router.state().isPressed(GamepadButton.GUIDE))
        assertFalse(router.state().isPressed(GamepadButton.BACK))

        // Past Home's edge and in the gap, where nearest centre is all there is to go on.
        router.move(1, 415f, 250f)
        assertTrue(router.state().isPressed(GamepadButton.BACK))
    }

    @Test
    fun `a trigger on a plate drives its axis`() {
        // Members are not all buttons, which is what the shoulder pairs need: a plate resolves to
        // whatever the member is and then routes it exactly as a control of its own would.
        val shoulders = GamepadLayout(
            id = "shoulders",
            name = "Shoulders",
            controls = listOf(
                ControlSpec(
                    ControlId.Cluster,
                    ControlSpec.Shape.Cluster(
                        0.5f,
                        0.5f,
                        members = listOf(
                            ControlSpec(
                                ControlId.Trigger(Side.RIGHT),
                                ControlSpec.Shape.Rect(0f, -0.15f, width = 0.4f, height = 0.2f),
                            ),
                            ControlSpec(
                                ControlId.Button(GamepadButton.R1),
                                ControlSpec.Shape.Rect(0f, 0.15f, width = 0.4f, height = 0.2f),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val router = TouchRouter(ResolvedLayout(shoulders, 1000f, 500f))

        router.down(1, 500f, 175f) // the upper half, which is the trigger
        assertEquals(GamepadState.AXIS_MAX, router.state().rightTrigger)

        router.move(1, 500f, 325f) // the lower half, which is the bumper
        assertEquals(GamepadState.AXIS_MIN, router.state().rightTrigger)
        assertTrue(router.state().isPressed(GamepadButton.R1))
    }

    @Test
    fun `D-pad arms on a plate still fold into one hat`() {
        val arms = GamepadLayout(
            id = "arms",
            name = "Arms",
            controls = listOf(
                ControlSpec(
                    ControlId.Cluster,
                    ControlSpec.Shape.Cluster(
                        0.5f,
                        0.5f,
                        members = ControlId.Direction.entries.map { direction ->
                            val x = when (direction) {
                                ControlId.Direction.LEFT -> -0.2f
                                ControlId.Direction.RIGHT -> 0.2f
                                else -> 0f
                            }
                            val y = when (direction) {
                                ControlId.Direction.UP -> -0.2f
                                ControlId.Direction.DOWN -> 0.2f
                                else -> 0f
                            }
                            ControlSpec(
                                ControlId.DpadButton(direction),
                                ControlSpec.Shape.Circle(x, y, radius = 0.1f),
                            )
                        },
                    ),
                ),
            ),
        )
        val router = TouchRouter(ResolvedLayout(arms, 1000f, 500f))

        router.down(1, 500f, 150f) // up
        assertEquals(Hat.NORTH, router.state().hat)

        router.down(2, 600f, 250f) // and right, on the same plate
        assertEquals(Hat.NORTH_EAST, router.state().hat)
    }

    @Test
    fun `a plate counts as placing the buttons on it`() {
        // Otherwise dropping a face plate would leave the editor still reporting that A, B, X and
        // Y are missing from a pad that plainly has them.
        val missing = plated.missingButtons()
        assertFalse("$missing", GamepadButton.SOUTH in missing)
        assertFalse("$missing", GamepadButton.WEST in missing)
        assertTrue("nothing else is placed", GamepadButton.START in missing)
    }

    @Test
    fun `nothing on a plate lights until it is touched`() {
        assertEquals(emptySet<Int>(), platedRouter().activeMembers(0))
    }

    // -- Layout sanity --------------------------------------------------------------------

    // Run over the whole catalog rather than XBOX_DEFAULT alone, so every layout the menu can
    // offer inherits these checks the moment it is added.

    @Test
    fun `the layout catalog has unique ids and includes the default`() {
        // The menu ticks the active layout by id, so a duplicate would tick two rows at once.
        val ids = Layouts.ALL.map { it.id }
        assertEquals("duplicate layout ids in $ids", ids.size, ids.toSet().size)
        assertTrue("the default is offered", DEFAULT_LAYOUT in Layouts.ALL)
    }

    @Test
    fun `every built-in layout exposes every button the profile declares`() {
        for (layout in Layouts.ALL) {
            assertEquals(layout.id, emptySet<GamepadButton>(), layout.missingButtons())
        }
    }

    @Test
    fun `the built-in layout crosses X and Y onto the slots hosts report them as`() {
        // BTN_X aliases BTN_NORTH and BTN_Y aliases BTN_WEST, so the key labelled Y has to drive
        // GamepadButton.WEST for the host to report a Y.
        val faces = DEFAULT_LAYOUT.controls.associateBy { it.label }
        val north = faces.getValue("Y")
        val west = faces.getValue("X")

        assertEquals(ControlId.Button(GamepadButton.WEST), north.id)
        assertEquals(ControlId.Button(GamepadButton.NORTH), west.id)

        // Guards the premise: Y really is the northern key and X the western one.
        assertTrue("Y sits above X", north.shape.centerY < west.shape.centerY)
        assertTrue("X sits left of Y", west.shape.centerX < north.shape.centerX)
    }

    @Test
    fun `no built-in layout has overlapping controls`() {
        // Overlapping touch areas make hit-testing ambiguous and the pad frustrating to use.
        // Checked at a typical 1080p landscape size and at a squarer tablet ratio, since circular
        // controls size themselves from height while positions are relative to both axes.
        for (layout in Layouts.ALL) {
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
