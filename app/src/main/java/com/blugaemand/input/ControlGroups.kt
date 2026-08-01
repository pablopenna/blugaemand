package com.blugaemand.input

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.layouts.DEFAULT_LAYOUT

/**
 * Arrangements of several controls that are placed in one go.
 *
 * A group is **a placement shortcut and nothing more**. Once dropped, its members are ordinary
 * controls that move and resize on their own — nothing records that they arrived together, and
 * nothing in the saved layout says so. That is deliberate: a layout stays a flat list of controls,
 * which is what it serialises as, and the editor keeps one selection model rather than two.
 *
 * Most groups take their geometry from [DEFAULT_LAYOUT], so a position tuned there moves them too.
 * The exceptions are the ones the default has no arrangement for — the stacked shoulder pairs, and
 * the cross of four separate D-pad arms — which are authored here.
 */
object ControlGroups {

    /**
     * Every group the editor offers. Adding one is a line here, the same shape as `Layouts.ALL`.
     */
    val ALL: List<Placement> = listOf(
        Placement.of("Face buttons", DEFAULT_LAYOUT.specsFor(FACE_BUTTONS)),
        Placement.of("D-pad (four buttons)", DPAD_ARMS),
        Placement.of("Start / Select / Home", DEFAULT_LAYOUT.specsFor(CENTRE_CLUSTER)),
        Placement.of("Left shoulders, side by side", DEFAULT_LAYOUT.specsFor(LEFT_SHOULDERS)),
        Placement.of("Left shoulders, stacked", stacked(LEFT_SHOULDERS)),
        Placement.of("Right shoulders, side by side", DEFAULT_LAYOUT.specsFor(RIGHT_SHOULDERS)),
        Placement.of("Right shoulders, stacked", stacked(RIGHT_SHOULDERS)),
    )
}

private val FACE_BUTTONS = listOf(
    ControlId.Button(GamepadButton.WEST),
    ControlId.Button(GamepadButton.NORTH),
    ControlId.Button(GamepadButton.EAST),
    ControlId.Button(GamepadButton.SOUTH),
)

private val CENTRE_CLUSTER = listOf(
    ControlId.Button(GamepadButton.BACK),
    ControlId.Button(GamepadButton.GUIDE),
    ControlId.Button(GamepadButton.START),
)

/** Trigger outermost, bumper inboard — the order the default layout puts them in along the edge. */
private val LEFT_SHOULDERS = listOf(
    ControlId.Trigger(ControlId.Side.LEFT),
    ControlId.Button(GamepadButton.L1),
)

private val RIGHT_SHOULDERS = listOf(
    ControlId.Button(GamepadButton.R1),
    ControlId.Trigger(ControlId.Side.RIGHT),
)

/** The four arms in a cross, which is what [defaultSpecFor] already lays them out as. */
private val DPAD_ARMS: List<ControlSpec> =
    ControlId.Direction.entries.map { defaultSpecFor(ControlId.DpadButton(it)) }

private fun GamepadLayout.specsFor(ids: List<ControlId>): List<ControlSpec> =
    ids.map { id -> controls.first { it.id == id } }

/**
 * The same pair one above the other rather than side by side.
 *
 * Both keep their width; only the row changes, and by the height of one plus a gap, so the two sit
 * clear of each other. The trigger stays on the outside — above, here — because that is where the
 * finger that reaches furthest expects it either way round.
 */
private fun stacked(ids: List<ControlId>): List<ControlSpec> {
    val specs = DEFAULT_LAYOUT.specsFor(ids)
    val column = specs.map { it.shape.centerX }.average().toFloat()
    return specs.mapIndexed { index, spec ->
        val rect = spec.shape as ControlSpec.Shape.Rect
        spec.copy(shape = rect.copy(centerX = column, centerY = rect.centerY + index * STACK_STEP))
    }
}

/**
 * Row spacing for a stacked pair, as a fraction of screen height.
 *
 * The default shoulder rectangles are `0.11` of the layout unit tall, and on the 16:9 the layouts
 * are authored for that is `0.11` of the height too — so this is one full height plus a little over
 * a third of one as a gap.
 */
private const val STACK_STEP = 0.15f
