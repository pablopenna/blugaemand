package com.blugaemand.input

import com.blugaemand.hid.GamepadButton
import com.blugaemand.hid.GamepadState
import com.blugaemand.hid.Hat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A stick under a finger: where it is centred on screen, in pixels, and how far it is pushed as a
 * -1..1 pair on each axis.
 *
 * The base is part of the answer because a [StickMode.DYNAMIC] stick's centre is wherever the thumb
 * landed rather than where the control is drawn. A fixed stick's base is simply its own centre, so
 * one type says both and the renderer needs no branch.
 */
data class StickTouch(
    val baseX: Float,
    val baseY: Float,
    val offsetX: Float,
    val offsetY: Float,
)

/**
 * Turns raw multitouch pointers into a [GamepadState].
 *
 * A pointer binds to whichever control it went down on and stays bound until it lifts, even if it
 * strays outside. That is how physical gamepads behave — your thumb rolling off the edge of a
 * button does not release it — and it stops a stick from being stolen mid-swing by a neighbouring
 * control.
 *
 * No Android dependencies, so the whole routing behaviour is testable on the JVM.
 */
class TouchRouter(private val layout: ResolvedLayout) {

    private class Binding(
        val control: ResolvedControl,
        /** Where the finger went down, which is what a trigger's pull is measured against. */
        val startX: Float,
        val startY: Float,
        var x: Float,
        var y: Float,
    ) {
        /**
         * Where the stick this pointer spawned is centred, for a dynamic one — the point the
         * offset is measured about, and where the renderer draws the base.
         *
         * Starts at the touch-down point, which is the whole of what makes a stick dynamic, and
         * then **follows the finger** once it is a full deflection away; see [follow]. Meaningless
         * for anything else, and the only thing that reads it checks the control first.
         */
        var baseX: Float = startX
        var baseY: Float = startY

        /**
         * Moves this pointer, dragging a dynamic stick's base along behind it.
         *
         * Past the radius the base is pulled up to sit exactly one radius back along the line to
         * the finger, rather than pinning where the stick appeared. So the stick stays at full
         * deflection in the direction it is being pushed and the thumb can keep going as far as it
         * likes — off the area it spawned in, off the far side of the screen — and turning the
         * finger around turns the stick with it instead of swinging it through a centre that is
         * now somewhere behind the hand.
         *
         * The area has no say in any of this: it decides where a stick may be *started* and
         * nothing after that, which is the same rule that already lets a thumb roll off a button
         * without releasing it.
         */
        fun follow(toX: Float, toY: Float) {
            x = toX
            y = toY
            if (!control.isDynamicStick) return
            val dx = x - baseX
            val dy = y - baseY
            val distance = hypot(dx, dy)
            if (distance <= control.radius || distance <= 0f) return
            val overshoot = (distance - control.radius) / distance
            baseX += dx * overshoot
            baseY += dy * overshoot
        }

        /**
         * Where this pointer's stick is centred and how far it is pushed, or null if it is not on
         * a stick at all.
         *
         * The one answer to both halves of the question, for the same reason `triggerValue` is the
         * one answer to what a trigger is sending: the renderer places the base and the cap from
         * exactly the numbers the host is being sent, so the picture cannot disagree with the
         * report. A fixed stick answers here too, about its own centre — which is what makes the
         * renderer's two cases one.
         */
        fun stickTouch(): StickTouch? {
            if (control.spec.shape !is ControlSpec.Shape.Stick) return null
            val dynamic = control.isDynamicStick
            val baseX = if (dynamic) baseX else control.centerX
            val baseY = if (dynamic) baseY else control.centerY
            val (dx, dy) = control.offsetAbout(
                baseX, baseY, x, y,
                deadZone = if (dynamic) DYNAMIC_DEAD_ZONE else 0f,
            )
            return StickTouch(baseX, baseY, dx, dy)
        }

        /**
         * The control this pointer is actually driving: the one it bound to, or — if that is a
         * cluster — whichever member it is currently over.
         *
         * Worked out here and nowhere else so that the state sent to the host and the member drawn
         * as held can never disagree about which one it is. Re-read on every event rather than
         * fixed at touch-down, so a thumb rolling across a plate changes button without lifting,
         * the same way it does across a D-pad cross.
         */
        fun target(): ResolvedControl =
            if (control.members.isEmpty()) control else control.memberAt(x, y) ?: control
    }

    /**
     * The axis value [trigger] reads for this pointer, by whichever [TriggerMode] it is set to.
     *
     * Asked of the binding rather than of the control, because a pull is measured from where the
     * finger landed and a control knows nothing about that. Taking the trigger as an argument
     * rather than re-deriving it keeps this out of the business of deciding whether the pointer is
     * on one — both callers have already established that, one from a `when` and one from a filter.
     *
     * A binary trigger reads [GamepadState.AXIS_MAX] and not the touched range's ceiling, which is
     * the same number: it is not a pull run to the top, it is a switch, and the value a switch
     * sends is the axis maximum. The two agreeing is why the distinction costs nothing.
     */
    private fun Binding.valueOf(trigger: ResolvedControl): Int = when (trigger.spec.triggerMode) {
        TriggerMode.BINARY -> GamepadState.AXIS_MAX
        TriggerMode.PROGRESSIVE -> GamepadState.triggerFromUnit(
            trigger.pullAt(startX, startY, x, y, layout.width, layout.height),
        )
    }

    private val bindings = LinkedHashMap<Long, Binding>()

    /**
     * Registers a new pointer. Returns true if it landed on a control.
     *
     * **A dynamic stick's area takes one finger at a time**, which is a new thing for this to
     * refuse: every other control takes as many as land on it, because two thumbs on one face
     * plate are two buttons and two thumbs on one cross are one direction either way. An area with
     * a stick already out of it has nothing to do with a second finger — a second stick from the
     * same area would fight the first for the same axes — so that finger binds to nothing at all,
     * exactly as a touch on bare glass does. If it landed on a control drawn over the area it
     * never got here: [ResolvedLayout.hitTest] gave it that control instead.
     */
    fun down(pointerId: Long, x: Float, y: Float): Boolean {
        val control = layout.hitTest(x, y) ?: return false
        if (control.isDynamicStick && bindings.values.any { it.control.index == control.index }) {
            return false
        }
        bindings[pointerId] = Binding(control, startX = x, startY = y, x = x, y = y)
        return true
    }

    /** Updates a pointer already bound to a control. Unbound pointers are ignored. */
    fun move(pointerId: Long, x: Float, y: Float) {
        bindings[pointerId]?.follow(x, y)
    }

    fun up(pointerId: Long) {
        bindings.remove(pointerId)
    }

    /** Releases everything, e.g. when the window loses focus mid-press. */
    fun reset() {
        bindings.clear()
    }

    /**
     * Controls currently held, by [ResolvedControl.index], for rendering the pressed state.
     *
     * By index and not by [ControlId], because a layout may hold the same id twice: keyed by id,
     * pressing one of two A buttons would light both.
     */
    fun activeControls(): Set<Int> = bindings.values.mapTo(mutableSetOf()) { it.control.index }

    /**
     * Which members of the cluster at [controlIndex] are held, by their ordinal within it.
     *
     * The union across every pointer, not the first one found: two thumbs on one face plate are two
     * bindings onto the same control, and they are meant to light — and send — two buttons.
     */
    fun activeMembers(controlIndex: Int): Set<Int> = bindings.values
        .filter { it.control.index == controlIndex && it.control.members.isNotEmpty() }
        .mapTo(mutableSetOf()) { it.target().index }

    /**
     * Which way the D-pad cross at [controlIndex] is being pushed, or null when nothing is on it.
     *
     * The renderer's counterpart to what [state] already works out for the host, and deliberately
     * the same arithmetic rather than a second opinion about where the sector boundaries are: a
     * cross that lights an arm it is not sending would be worse than one that lights all four.
     *
     * [com.blugaemand.hid.Hat.CENTER] is a real answer here, not a null — a thumb resting in the
     * dead zone is touching the control and sending nothing, and the art pack has a picture for
     * exactly that.
     */
    fun dpadPush(controlIndex: Int): Hat? {
        val binding = bindings.values.firstOrNull { it.control.index == controlIndex } ?: return null
        return binding.control.hatFor(binding.x, binding.y)
    }

    /**
     * Where the stick at [controlIndex] is centred and how far it is pushed, or null when nothing
     * is touching it.
     *
     * Both together rather than the displacement alone, because a dynamic stick has to say where
     * its base *is* as well as where its cap has got to — the base is wherever the thumb landed,
     * and only the binding knows. A fixed stick answers about its own centre, so the renderer has
     * one case and not two.
     *
     * By index for the same reason [dpadPush] is: two sticks on the same side are a strange
     * layout, but they are a representable one, and they should not move in lockstep.
     */
    fun stickTouch(controlIndex: Int): StickTouch? =
        bindings.values.firstOrNull { it.control.index == controlIndex }?.stickTouch()

    /**
     * What the trigger at [controlIndex] is sending right now, in the 1..255 range a touched
     * trigger occupies, or null when nothing is on it. The renderer draws it as the read-out under
     * the control.
     *
     * By index like [stickTouch] and [dpadPush], and it answers for a trigger reached as a
     * cluster member too — the binding is on the plate, and [Binding.target] resolves which member
     * the finger is over. What it cannot do is answer for two triggers on one plate at once, which
     * is the same first-pointer-wins simplification those two already make.
     *
     * A [TriggerMode.BINARY] trigger answers here as well, with the 255 it is sending. The read-out
     * says what is going to the host, and "the host is being told 255" is as true of a switch as of
     * a pull — and seeing it pinned there is how the mode shows on the pad at all.
     */
    fun triggerValue(controlIndex: Int): Int? {
        val binding = bindings.values.firstOrNull {
            it.control.index == controlIndex && it.target().id is ControlId.Trigger
        } ?: return null
        return binding.valueOf(binding.target())
    }

    /** Builds the state to send to the host from every currently held control. */
    fun state(): GamepadState {
        var state = GamepadState.NEUTRAL

        // The hat is settled after the loop rather than inside it, because it can come from either
        // one cross or several direction buttons, and the buttons only mean something together.
        var crossHat: Hat? = null
        val directions = mutableSetOf<ControlId.Direction>()

        // Declared here rather than lifted out of the class so it keeps writing straight into the
        // two accumulators above. A cluster member goes through exactly this, so a member drives a
        // button, an axis or the hat by the same rules a control of its own would.
        fun apply(binding: Binding) {
            val control = binding.target()
            val x = binding.x
            val y = binding.y
            state = when (val id = control.id) {
                is ControlId.Button -> state.withButton(id.button, true)

                is ControlId.Trigger -> {
                    val value = binding.valueOf(control)
                    if (id.side == ControlId.Side.LEFT) {
                        state.copy(leftTrigger = value)
                    } else {
                        state.copy(rightTrigger = value)
                    }
                }

                is ControlId.Stick -> {
                    // Through the binding, not the control: a dynamic stick is measured about
                    // where the finger landed, and the control has no idea where that was. The
                    // same call the renderer makes, so the cap on screen and the axes on the wire
                    // are the one number. A stick id on some other shape has no offset to give and
                    // sends centre, which is what it did before dynamic sticks existed too.
                    val (dx, dy) = binding.stickTouch()?.let { it.offsetX to it.offsetY }
                        ?: (0f to 0f)
                    if (id.side == ControlId.Side.LEFT) {
                        state.copy(
                            leftStickX = GamepadState.axisFromUnit(dx),
                            leftStickY = GamepadState.axisFromUnit(dy),
                        )
                    } else {
                        state.copy(
                            rightStickX = GamepadState.axisFromUnit(dx),
                            rightStickY = GamepadState.axisFromUnit(dy),
                        )
                    }
                }

                ControlId.Dpad -> state.also { crossHat = control.hatFor(x, y) }

                is ControlId.DpadButton -> state.also { directions += id.direction }

                // Unreachable: Binding.target() resolves a plate to one of its members, and a plate
                // always has one. Named rather than caught by an else so that adding a control kind
                // still breaks this `when` and has to be thought about.
                ControlId.Cluster -> state
            }
        }

        for (binding in bindings.values) apply(binding)

        // A held cross wins. A layout carrying both is a layout where the cross is the deliberate
        // one, and letting a stray arm override a thumb already on the cross would be worse than
        // ignoring it.
        val hat = crossHat ?: Hat.of(
            up = ControlId.Direction.UP in directions,
            down = ControlId.Direction.DOWN in directions,
            left = ControlId.Direction.LEFT in directions,
            right = ControlId.Direction.RIGHT in directions,
        )

        return state.copy(hat = hat)
    }

    private companion object {

        /**
         * Displacement from a point, clamped to the control's radius and scaled to -1..1.
         *
         * The point is the control's own centre for a fixed stick and the moving base for a
         * dynamic one, which is the whole of the difference between them: the same arithmetic
         * about a different origin.
         *
         * [deadZone] is a fraction of the radius that reads as dead centre, with the rest of the
         * throw stretched over what is left so the value climbs from zero at its edge rather than
         * jumping to it. Zero for a fixed stick, which has never had one and does not need one —
         * its centre is a place you can feel. A dynamic stick's centre is wherever the thumb
         * happened to land rather than anywhere it aimed, so without a dead zone every spawn would
         * start with a few pixels of drift on both axes.
         */
        fun ResolvedControl.offsetAbout(
            baseX: Float,
            baseY: Float,
            x: Float,
            y: Float,
            deadZone: Float,
        ): Pair<Float, Float> {
            if (radius <= 0f) return 0f to 0f
            val dx = x - baseX
            val dy = y - baseY
            val distance = hypot(dx, dy)
            val dead = radius * deadZone
            if (distance <= dead) return 0f to 0f
            val pushed = ((distance - dead) / (radius - dead)).coerceAtMost(1f)
            return (dx / distance * pushed) to (dy / distance * pushed)
        }

        /**
         * How much of a dynamic stick's throw reads as centre, as a fraction of its radius — the
         * way `ControlSpec.Shape.Dpad.deadZone` is a fraction of its own.
         *
         * A constant and not a field on the shape, unlike the D-pad's: this one is not compensating
         * for a layout's taste in cross sizes, it is compensating for a thumb landing where it
         * likes, which is the same everywhere and on every layout. A third field on
         * `ControlSpec.Shape.Stick` that only some sticks read would be paying in the file format
         * for a number nobody would move.
         */
        const val DYNAMIC_DEAD_ZONE = 0.12f

        /**
         * How far a trigger is pulled, as 0..1 with **0.5 at rest**, given where the finger went
         * down, where it is now, and the surface it is on.
         *
         * A touch rests in the middle and the finger slides both ways. Which way means *more*
         * depends on the axis, and both axes measure from the same place: whichever screen edge
         * the finger is nearer along the axis in play.
         *
         * - **Sideways, in towards the middle of the screen raises it**, back out lowers it.
         * - **Up or down, out towards the nearer edge raises it**, back in lowers it — so a
         *   trigger in a top corner is pulled by sliding up and eased off by sliding down, and one
         *   along the bottom is pulled by sliding down.
         *
         * Neither is a fixed compass direction: a layout may put a trigger anywhere, and the rule
         * has to read the same wherever it lands. That the two senses come out opposite is
         * deliberate and is a decision about feel — the thumb draws in off the side of the glass
         * and pushes out over the top of it — not a symmetry anything else depends on.
         *
         * **One axis at a time.** Whichever component of the drag is the larger is the one that
         * counts, and the other is ignored outright rather than added in. Two axes summed would
         * make a diagonal do something neither of its parts does, and a trigger in a corner —
         * where both an edge below and an edge beside it are close — has two directions that
         * plainly mean "less" and no sensible way to combine them. Re-read on every event, so a
         * drag that turns a corner changes which axis it is answering on.
         *
         * **The travel available is capped by the room there is.** The nominal throw is
         * [TRIGGER_TRAVEL_SPANS] of the control's shorter way across, which keeps a bigger trigger
         * a longer throw and keeps the throw the same length whichever way it is dragged. But a
         * trigger sits against an edge, and that way there may be only a few dozen pixels of glass
         * before the finger runs out of screen — so each direction takes the smaller of the
         * nominal throw and the distance to the edge that way, and the floor and the ceiling are
         * both always reachable. The cost is that whichever of the two runs at an edge is touchier
         * than the other, and that is the right way round: it is reachability that cannot be given
         * up.
         */
        fun ResolvedControl.pullAt(
            startX: Float,
            startY: Float,
            x: Float,
            y: Float,
            screenWidth: Float,
            screenHeight: Float,
        ): Float {
            val dx = x - startX
            val dy = y - startY
            val horizontal = abs(dx) > abs(dy)

            val moved = if (horizontal) dx else dy
            // Room either side of where the finger landed, towards the low and high edge of the
            // axis in play. Measured from the anchor rather than the control's centre so that one
            // point answers both questions below, and so a finger that landed off-centre gets the
            // room it actually has.
            val towardsLow = if (horizontal) startX else startY
            val towardsHigh = (if (horizontal) screenWidth else screenHeight) - towardsLow

            // The nearer edge is the one with less room. A finger exactly on the midline has no
            // nearer edge; the tie goes to the low side, which is arbitrary but at least the same
            // every time.
            val awayFromEdge = if (towardsLow < towardsHigh) moved else -moved
            // Sideways that is the direction meaning more, up and down it is the one meaning less.
            val pull = if (horizontal) awayFromEdge else -awayFromEdge

            val nominal = minOf(extentX, extentY) * 2f * TRIGGER_TRAVEL_SPANS
            // The glass left the way the finger is actually going, which is what caps the throw.
            // Asked of the raw movement rather than of [pull], so it stays right however the two
            // axes assign their signs.
            val room = if (moved >= 0f) towardsHigh else towardsLow
            val travel = minOf(nominal, room)
            if (travel <= 0f) return REST_PULL

            return (REST_PULL + REST_PULL * (pull / travel)).coerceIn(0f, 1f)
        }

        /** Where a touched trigger rests, as a 0..1 pull: the middle, with room to go either way. */
        const val REST_PULL = 0.5f

        /**
         * The nominal throw from rest to either rail, in multiples of the control's shorter way
         * across — the same measure `drawGlyph` sizes a picture by, and for the same reason: it is
         * the one extent a control of any shape has that is not distorted by how wide it is drawn.
         */
        const val TRIGGER_TRAVEL_SPANS = 2f

        /** Which of the eight hat directions a touch inside the D-pad represents. */
        fun ResolvedControl.hatFor(x: Float, y: Float): Hat {
            val shape = spec.shape as? ControlSpec.Shape.Dpad ?: return Hat.CENTER
            val dx = x - centerX
            val dy = y - centerY
            val distance = hypot(dx, dy)
            if (distance < radius * shape.deadZone) return Hat.CENTER

            // Sectors run clockwise from due east in 45-degree steps; y grows downwards, so a
            // positive angle points south.
            val sector = ((atan2(dy, dx) / (PI / 4)).roundToInt() + 8) % 8
            return SECTORS[sector]
        }

        val SECTORS = arrayOf(
            Hat.EAST,
            Hat.SOUTH_EAST,
            Hat.SOUTH,
            Hat.SOUTH_WEST,
            Hat.WEST,
            Hat.NORTH_WEST,
            Hat.NORTH,
            Hat.NORTH_EAST,
        )
    }
}

/**
 * Buttons that have no on-screen control in a layout, useful for validating custom layouts later.
 *
 * Through [ControlSpec.leafIds], so a button reached as a member of a cluster counts as placed —
 * otherwise dropping a face plate would leave the editor still reporting that A, B, X and Y are
 * missing from a pad that plainly has them.
 */
fun GamepadLayout.missingButtons(): Set<GamepadButton> {
    val present = controls.flatMap { it.leafIds() }
        .mapNotNull { (it as? ControlId.Button)?.button }
        .toSet()
    return GamepadButton.entries.toSet() - present - setOf(GamepadButton.L2, GamepadButton.R2)
}
