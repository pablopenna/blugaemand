package com.blugaemand.input

import com.blugaemand.hid.GamepadButton
import com.blugaemand.hid.GamepadState
import com.blugaemand.hid.Hat
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

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
        var x: Float,
        var y: Float,
    )

    private val bindings = LinkedHashMap<Long, Binding>()

    /** Registers a new pointer. Returns true if it landed on a control. */
    fun down(pointerId: Long, x: Float, y: Float): Boolean {
        val control = layout.hitTest(x, y) ?: return false
        bindings[pointerId] = Binding(control, x, y)
        return true
    }

    /** Updates a pointer already bound to a control. Unbound pointers are ignored. */
    fun move(pointerId: Long, x: Float, y: Float) {
        bindings[pointerId]?.let {
            it.x = x
            it.y = y
        }
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
     * Displacement of a stick's knob as a -1..1 pair, or null when nothing is touching it. The
     * renderer scales this by the stick radius to place the cap.
     *
     * By index for the same reason: two sticks on the same side are a strange layout, but they are
     * a representable one, and they should not move in lockstep.
     */
    fun stickOffset(controlIndex: Int): Pair<Float, Float>? {
        val binding = bindings.values.firstOrNull { it.control.index == controlIndex } ?: return null
        return binding.control.normalisedOffset(binding.x, binding.y)
    }

    /** Builds the state to send to the host from every currently held control. */
    fun state(): GamepadState {
        var state = GamepadState.NEUTRAL

        // The hat is settled after the loop rather than inside it, because it can come from either
        // one cross or several direction buttons, and the buttons only mean something together.
        var crossHat: Hat? = null
        val directions = mutableSetOf<ControlId.Direction>()

        for (binding in bindings.values) {
            val control = binding.control
            state = when (val id = control.id) {
                is ControlId.Button -> state.withButton(id.button, true)

                is ControlId.Trigger -> {
                    // Digital for now: full pull on touch. The descriptor already carries the full
                    // 0..255 range, so making these analog later is a change here and nowhere else.
                    val value = GamepadState.AXIS_MAX
                    if (id.side == ControlId.Side.LEFT) {
                        state.copy(leftTrigger = value)
                    } else {
                        state.copy(rightTrigger = value)
                    }
                }

                is ControlId.Stick -> {
                    val (dx, dy) = control.normalisedOffset(binding.x, binding.y)
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

                ControlId.Dpad -> state.also { crossHat = control.hatFor(binding.x, binding.y) }

                is ControlId.DpadButton -> state.also { directions += id.direction }
            }
        }

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
         * Displacement from the control's centre, clamped to its radius and scaled to -1..1.
         */
        fun ResolvedControl.normalisedOffset(x: Float, y: Float): Pair<Float, Float> {
            if (radius <= 0f) return 0f to 0f
            val dx = x - centerX
            val dy = y - centerY
            val distance = hypot(dx, dy)
            if (distance <= 0f) return 0f to 0f
            val scale = if (distance > radius) radius / distance else 1f
            return (dx * scale / radius) to (dy * scale / radius)
        }

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

/** Buttons that have no on-screen control in a layout, useful for validating custom layouts later. */
fun GamepadLayout.missingButtons(): Set<GamepadButton> {
    val present = controls.mapNotNull { (it.id as? ControlId.Button)?.button }.toSet()
    return GamepadButton.entries.toSet() - present - setOf(GamepadButton.L2, GamepadButton.R2)
}
