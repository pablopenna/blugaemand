package com.blugaemand.input

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.layouts.DEFAULT_LAYOUT
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * What the editor does to a layout: move a control, resize one, add one, remove one.
 *
 * All of it is arithmetic on plain data, so all of it is tested on the JVM rather than by dragging
 * things around on a phone. The gestures in [com.blugaemand.ui.EditorScreen] do nothing but turn
 * fingers into calls on these.
 *
 * The pixel work goes through [ResolvedLayout] rather than recomputing anything: it already owns
 * the surface size, the layout unit, and each control's resolved geometry — including the
 * on-screen extent that a move has to keep inside the screen.
 */

/** Smallest a control may be made, as a fraction of [ResolvedLayout.unit]. Below this a thumb misses it. */
const val MIN_CONTROL_EXTENT: Float = 0.02f

/**
 * **There is no matching maximum.** There was one, and it was wrong twice over: a number picked
 * against the biggest thing that shipped is a guess about layouts nobody has made yet, and it made
 * the two limits asymmetric for no reason a user could see — a control that could not be shrunk
 * past being touchable is obvious, a control that stops growing half way across the screen is a
 * bug. What actually bounds a resize is the glass: [resizedControl] holds the edge being dragged
 * inside the surface, which is a limit you can see the reason for.
 */

/**
 * Spacing of the editor's grid, in **pixels**, derived from the layout unit so it is square on
 * screen.
 *
 * A grid defined in normalised coordinates would not be: x divides by width and y by height, so on
 * a 16:9 screen its cells would be nearly twice as wide as they are tall, and two controls both
 * "on the grid" would not line up with each other. Sizes snap against the same pixel grid, for the
 * same reason — see [scaledBy].
 */
val ResolvedLayout.gridStep: Float get() = unit / 20f

/**
 * How far one press of a nudge arrow moves the selection, in **pixels**.
 *
 * A whole grid step while snapping is on, so a nudge moves a control from one line to the next
 * rather than part-way and then onto the same one it started on. A fifth of a step otherwise, which
 * is the fine adjustment the arrows exist for — a drag can already put a control roughly where it
 * goes, and what it cannot do is move it by an amount smaller than a thumb can feel.
 *
 * Derived from [gridStep] rather than stated, so it stays in proportion on any screen for the same
 * reason the grid does.
 */
fun ResolvedLayout.nudgeStep(snap: Boolean): Float = if (snap) gridStep else gridStep / 5f

/**
 * [layout] with one control's centre moved by a pixel delta.
 *
 * Clamped so the control stays wholly on screen. Clamping the centre alone would let half a button
 * hang over the edge, where it cannot be touched — which reads as a bug rather than as a choice.
 */
fun ResolvedLayout.movedControl(
    index: Int,
    dxPixels: Float,
    dyPixels: Float,
    snap: Boolean,
): GamepadLayout {
    val control = controls.getOrNull(index) ?: return layout

    var x = control.centerX + dxPixels
    var y = control.centerY + dyPixels
    if (snap) {
        x = snapToGrid(x, gridStep)
        y = snapToGrid(y, gridStep)
    }

    // Half the control's own on-screen extent, which differs per axis for a rectangle.
    val insetX = control.extentX
    val insetY = control.extentY

    return layout.replacingShape(index) {
        it.withCenter(
            // A control wider than the screen would invert the range, hence the ordered bounds.
            x = (x.coerceIn(insetX, maxOf(insetX, width - insetX)) / width).coerceIn(0f, 1f),
            y = (y.coerceIn(insetY, maxOf(insetY, height - insetY)) / height).coerceIn(0f, 1f),
        )
    }
}

/**
 * [layout] with one control scaled by [factor], where 1 leaves it alone.
 *
 * [snap] rounds the result to the grid as well, so two buttons meant to match can be made to match
 * rather than merely brought close.
 */
fun ResolvedLayout.resizedControl(index: Int, factor: Float, snap: Boolean): GamepadLayout {
    if (controls.getOrNull(index) == null) return layout
    val scale = Scale(factor, factor, if (snap) gridStep else 0f, unit, width)
    // Pulled back on screen afterwards because a plate grows about its centre and its whole
    // bounding box grows with it, so one near an edge would otherwise scale straight off the side.
    // A single control can do that too, but by half a radius rather than half a plate.
    // The whole spec and not just its shape, because what a stick's numbers mean depends on the
    // mode it is in: a pinch on a dynamic one is a pinch on the area it is drawn as.
    val resized = layout.replacingSpec(index) { spec ->
        spec.copy(shape = spec.shape.scaledBy(scale, area = spec.isDynamicStick()))
    }
    return ResolvedLayout(resized, width, height)
        .let { grown -> grown.movedControl(index, 0f, 0f, snap = false) }
}

/**
 * One of the eight indicators drawn around the selection, and the direction dragging it pulls in:
 * `-1`, `0` or `1` per axis, so `dx` and `dy` are the edge each one sits on.
 *
 * The four corners scale both axes together and the four edges scale their own axis alone — the
 * behaviour every editor has, and the reason the aspect ratio is no longer forced on every resize.
 * Which of those a shape can actually honour is [ControlSpec.scalesPerAxis].
 */
enum class ResizeHandle(val dx: Int, val dy: Int) {
    TOP_LEFT(-1, -1),
    TOP(0, -1),
    TOP_RIGHT(1, -1),
    RIGHT(1, 0),
    BOTTOM_RIGHT(1, 1),
    BOTTOM(0, 1),
    BOTTOM_LEFT(-1, 1),
    LEFT(-1, 0);

    val isCorner: Boolean get() = dx != 0 && dy != 0
}

/**
 * Whether this control has a width and a height that can move independently.
 *
 * A rectangle does, and so does a dynamic stick's spawning area — both are boxes with a size per
 * axis. Everything else is measured by a single radius, so an edge handle on one can only scale it
 * whole; dragging the side of a button squarely stretches nothing, it just makes a bigger button.
 */
fun ControlSpec.scalesPerAxis(): Boolean = shape is ControlSpec.Shape.Rect || isDynamicStick()

/** Half the gap between a control and the ring drawn around it, which is where the handles sit. */
val ResolvedControl.selectionInset: Float get() = minOf(extentX, extentY) * SELECTION_INSET_RATIO

/** Centre of one handle, in pixels — where it is drawn, and what a touch is measured against. */
fun ResolvedControl.handleCenterX(handle: ResizeHandle): Float =
    centerX + handle.dx * (extentX + selectionInset)

fun ResolvedControl.handleCenterY(handle: ResizeHandle): Float =
    centerY + handle.dy * (extentY + selectionInset)

/**
 * How big a handle is drawn, and — at [HANDLE_TOUCH_RATIO] times that — how close a finger has to
 * land to grab one.
 *
 * Off the layout unit rather than the control, so the indicators around a shoulder button and the
 * ones around the D-pad are the same size and both are worth aiming at. A control small enough for
 * its own handles to overlap it is a control you can still resize, which is the point of them.
 */
val ResolvedLayout.handleRadius: Float get() = unit * HANDLE_RADIUS_RATIO

/**
 * The handle a touch means, or null if it missed all eight.
 *
 * Nearest centre wins, so the corner and the edge handle beside it — which can overlap on a small
 * control — split the gap between them rather than one silently swallowing the other.
 */
fun ResolvedControl.handleAt(x: Float, y: Float, radius: Float): ResizeHandle? =
    ResizeHandle.entries
        .filter { hypot(x - handleCenterX(it), y - handleCenterY(it)) <= radius }
        .minByOrNull { hypot(x - handleCenterX(it), y - handleCenterY(it)) }

/**
 * [layout] with one control resized by dragging [handle] a pixel delta.
 *
 * **The opposite edge stays exactly where it is.** Dragging the right edge to the right widens the
 * control rightwards rather than growing it about its centre, which is what makes a handle feel
 * like it is holding the edge it is drawn on. Everything below exists to keep that true: the size
 * is worked out from where the dragged edge ends up, snapping lands on that edge rather than on the
 * size, and the growth stops at the screen so the on-screen clamp never has to shove the control
 * back and take the anchored edge with it.
 *
 * A corner scales both axes by one factor and keeps the shape; an edge scales its own axis, for a
 * control whose axes are independent ([ControlSpec.scalesPerAxis]) and both for one whose are not.
 */
fun ResolvedLayout.resizedControl(
    index: Int,
    handle: ResizeHandle,
    dxPixels: Float,
    dyPixels: Float,
    snap: Boolean,
): GamepadLayout {
    val control = controls.getOrNull(index) ?: return layout
    val halfWidth = control.extentX
    val halfHeight = control.extentY
    if (halfWidth <= 0f || halfHeight <= 0f) return layout

    var factorX = if (handle.dx == 0) 1f else {
        draggedHalfExtent(control.centerX, halfWidth, handle.dx, dxPixels, snap, width) / halfWidth
    }
    var factorY = if (handle.dy == 0) 1f else {
        draggedHalfExtent(control.centerY, halfHeight, handle.dy, dyPixels, snap, height) / halfHeight
    }
    if (handle.isCorner || !control.spec.scalesPerAxis()) {
        // A corner averages its two axes rather than picking one, so a diagonal drag answers to
        // both halves of itself; an edge takes the axis it drove and ignores the one it did not.
        val together = when {
            handle.isCorner -> (factorX + factorY) / 2f
            handle.dx != 0 -> factorX
            else -> factorY
        }
        factorX = together
        factorY = together
    }

    // No grid: the snapping already happened, to the edge the finger is holding. Snapping the size
    // as well would round it to a multiple of the step and jump the control the moment a handle was
    // touched -- a 288 px wide trigger became 270 px on the first pixel of the drag.
    val scale = Scale(factorX, factorY, stepPixels = 0f, unit, width)
    val resized = layout.replacingSpec(index) { spec ->
        spec.copy(shape = spec.shape.scaledBy(scale, area = spec.isDynamicStick()))
    }

    // Measured after the fact rather than predicted, because the floor on size can move what the
    // finger asked for, and the anchored edge has to hold against what the control ended up as.
    val grown = ResolvedLayout(resized, width, height)
    val after = grown.controls.getOrNull(index) ?: return resized
    val anchoredX = control.centerX - handle.dx * halfWidth + handle.dx * after.extentX
    val anchoredY = control.centerY - handle.dy * halfHeight + handle.dy * after.extentY
    return grown.movedControl(
        index,
        if (handle.dx != 0) anchoredX - after.centerX else 0f,
        if (handle.dy != 0) anchoredY - after.centerY else 0f,
        snap = false,
    )
}

/**
 * Half the control's new extent along one axis, given where the edge being dragged has got to.
 *
 * The whole of the handle's behaviour on one axis. The edge follows the finger; [snap] rounds
 * **that edge** to the grid, which is what puts one control's side on the same line as another's
 * rather than merely making the two the same size; and it is held inside `0..limit`, so a control
 * grows until it reaches the glass and then stops. The opposite edge is never consulted after it is
 * read, which is what keeps it still.
 *
 * Never negative: dragging an edge past the far side of the control collapses it rather than
 * turning it inside out, and [Scale] puts the floor under it from there.
 */
private fun ResolvedLayout.draggedHalfExtent(
    center: Float,
    half: Float,
    direction: Int,
    delta: Float,
    snap: Boolean,
    limit: Float,
): Float {
    val anchored = center - direction * half
    var edge = center + direction * half + delta
    if (snap) edge = snapToGrid(edge, gridStep)
    edge = edge.coerceIn(0f, limit)
    return (direction * (edge - anchored) / 2f).coerceAtLeast(0f)
}

/**
 * [layout] with the cluster at [index] broken back into ordinary controls, each left exactly where
 * the plate was drawing it.
 *
 * The way out of a plate, and the reason a plate does not need a second selection model inside it:
 * anyone who wants to tune one member ungroups, moves it, and is back to controls that behave the
 * way every other control does. Members are stored against the layout unit and top-level controls
 * against the screen, so this converts as it goes — see [ControlSpec.Shape.Cluster].
 */
fun ResolvedLayout.ungroupedControl(index: Int): GamepadLayout {
    val plate = controls.getOrNull(index) ?: return layout
    val shape = plate.spec.shape as? ControlSpec.Shape.Cluster ?: return layout

    val loosened = shape.members.mapIndexed { ordinal, member ->
        val resolved = plate.members[ordinal]
        member.copy(
            shape = member.shape
                .withScreenWidths(unit / width)
                .withCenter(x = resolved.centerX / width, y = resolved.centerY / height),
        ).clampedOnScreen(this)
    }

    return layout.copy(
        controls = layout.controls.take(index) + loosened + layout.controls.drop(index + 1),
    )
}

/**
 * [this] with another [id] on it.
 *
 * **A control may appear more than once** — two A buttons, one under each thumb, is a reasonable
 * pad. So this appends rather than refusing, and offsets each copy after the first by a step so the
 * new one is visible and grabbable rather than exactly under the one already there.
 *
 * Shape and label come from [DEFAULT_LAYOUT] via [defaultSpecFor], so a control arrives the size the
 * built-in pad uses it at and carrying the label the host will report for it.
 */
fun GamepadLayout.withControlAdded(id: ControlId): GamepadLayout {
    val copies = controls.count { it.id == id }
    val spec = defaultSpecFor(id)
    val offset = copies * DUPLICATE_OFFSET
    return copy(
        controls = controls + spec.copy(
            shape = spec.shape.withCenter(
                x = (spec.shape.centerX + offset).coerceIn(0f, 1f),
                y = (spec.shape.centerY + offset).coerceIn(0f, 1f),
            ),
        ),
    )
}

/** How far each further copy of a control is nudged, in normalised units. */
private const val DUPLICATE_OFFSET = 0.04f

/**
 * [this] with the trigger at [index] switched to [mode] — and every trigger on it, if it is a plate.
 *
 * A plate is one thing to select, so it has to be one thing to set: a shoulder pair holding ZL and
 * the bumper beside it is selected as the pair, and there is no way to reach into it but to
 * ungroup. Setting all of them is the answer that matches what was tapped. A plate with two
 * triggers on it is rare enough that both taking the mode is not a surprise.
 *
 * A control with no trigger anywhere in it comes back unchanged rather than refusing, so the editor
 * does not have to ask twice — [triggerModeOrNull] is what decides whether the row is offered at
 * all.
 */
fun GamepadLayout.withTriggerMode(index: Int, mode: TriggerMode): GamepadLayout =
    if (index !in controls.indices) this
    else copy(
        controls = controls.mapIndexed { i, spec ->
            if (i == index) spec.withTriggerMode(mode) else spec
        },
    )

/**
 * The mode the triggers on this control are in, or null if there are none — which is also the
 * editor's test for whether to offer the setting.
 *
 * The first one found, for a plate carrying more than one. They can only disagree in a hand-edited
 * file, because [withTriggerMode] sets them together, and answering for the first is better than
 * refusing to answer: the row still shows, and tapping it puts them back in step.
 */
fun ControlSpec.triggerModeOrNull(): TriggerMode? = when (val shape = shape) {
    is ControlSpec.Shape.Cluster -> shape.members.firstNotNullOfOrNull { it.triggerModeOrNull() }
    else -> if (id is ControlId.Trigger) triggerMode else null
}

/**
 * [this] with the stick at [index] switched to [mode].
 *
 * A control that is not a stick comes back unchanged rather than refusing, the way
 * [withTriggerMode] does, and for the same reason: [stickModeOrNull] is what decides whether the
 * editor offers the row at all. No recursion into a plate either — a [ControlSpec.Shape.Cluster]
 * cannot hold a stick, so there is nowhere for one to hide.
 *
 * The area a dynamic stick appears with is whatever the shape already carries, which for a stick
 * that has never been dynamic is [ControlSpec.Shape.Stick.DEFAULT_AREA_WIDTH] and its height. So
 * switching back and forth is lossless: the throw keeps the radius it was tuned to and the area
 * keeps the size it was dragged to.
 */
fun GamepadLayout.withStickMode(index: Int, mode: StickMode): GamepadLayout =
    if (index !in controls.indices) this
    else copy(
        controls = controls.mapIndexed { i, spec ->
            if (i == index && spec.id is ControlId.Stick && spec.shape is ControlSpec.Shape.Stick) {
                spec.copy(stickMode = mode)
            } else {
                spec
            }
        },
    )

/**
 * The mode this control's stick is in, or null if it is not a stick — which is also the editor's
 * test for whether to offer the setting.
 *
 * Both halves asked, matching [isDynamicStick]: the setting is only meaningful where there is a
 * stick to spawn and a stick's geometry to spawn it with.
 */
fun ControlSpec.stickModeOrNull(): StickMode? =
    if (id is ControlId.Stick && shape is ControlSpec.Shape.Stick) stickMode else null

/** [this] without the control at [index]. An index that is not there is not an error. */
fun GamepadLayout.withControlRemovedAt(index: Int): GamepadLayout =
    if (index !in controls.indices) this
    else copy(controls = controls.filterIndexed { i, _ -> i != index })

/**
 * How a control is named in the editor.
 *
 * Buttons go by the label the default layout gives them, because that is the letter the host
 * reports and so the thing someone is actually looking for. Everything else is spelled out: the
 * default calls its sticks *L* and *R*, which is right on the pad and cryptic in a list.
 */
fun ControlId.describe(): String = when (this) {
    is ControlId.Button -> defaultSpecFor(this).label.ifEmpty { button.name }
    is ControlId.Stick -> "${side.spelled()} stick"
    is ControlId.Trigger -> "${side.spelled()} trigger"
    ControlId.Dpad -> "D-pad (one cross)"
    is ControlId.DpadButton -> "D-pad ${direction.name.lowercase()}"
    // A plate has no name of its own -- what it is, is what is on it, which only the shape knows.
    // Nothing asks for one: the id names a control being *added*, and a plate is added as a group.
    ControlId.Cluster -> "Control group"
}

/**
 * How a trigger mode is named in the editor — *"progressive"*, *"binary"*.
 *
 * Beside [ControlId.describe] and not in the panel that shows it, so the two names a control is
 * offered under both come from here.
 */
fun TriggerMode.describe(): String = name.lowercase()

/** The other of the two, which is what tapping the editor's row switches to. */
fun TriggerMode.other(): TriggerMode = when (this) {
    TriggerMode.BINARY -> TriggerMode.PROGRESSIVE
    TriggerMode.PROGRESSIVE -> TriggerMode.BINARY
}

/** How a stick mode is named in the editor — *"fixed"*, *"dynamic"*. Beside [TriggerMode.describe]. */
fun StickMode.describe(): String = name.lowercase()

/** The other of the two, which is what tapping the editor's row switches to. */
fun StickMode.other(): StickMode = when (this) {
    StickMode.FIXED -> StickMode.DYNAMIC
    StickMode.DYNAMIC -> StickMode.FIXED
}

/** [value] rounded to the nearest multiple of [step]. */
fun snapToGrid(value: Float, step: Float): Float =
    if (step <= 0f) value else (value / step).roundToInt() * step

// -- Internals ----------------------------------------------------------------------------

/** [this] with [mode] on it if it is a trigger, or on its triggers if it is a plate. */
private fun ControlSpec.withTriggerMode(mode: TriggerMode): ControlSpec = when (val shape = shape) {
    is ControlSpec.Shape.Cluster ->
        copy(shape = shape.copy(members = shape.members.map { it.withTriggerMode(mode) }))

    else -> if (id is ControlId.Trigger) copy(triggerMode = mode) else this
}

private fun ControlId.Side.spelled(): String =
    name.lowercase().replaceFirstChar { it.uppercase() }

private fun GamepadLayout.replacingShape(
    index: Int,
    transform: (ControlSpec.Shape) -> ControlSpec.Shape,
): GamepadLayout = replacingSpec(index) { it.copy(shape = transform(it.shape)) }

private fun GamepadLayout.replacingSpec(
    index: Int,
    transform: (ControlSpec) -> ControlSpec,
): GamepadLayout = copy(
    controls = controls.mapIndexed { i, spec -> if (i == index) transform(spec) else spec },
)

/**
 * The shape and label a control arrives with, taken from [DEFAULT_LAYOUT] so that a control added
 * to a layout is the size the built-in pad uses it at, and carries the label the host will report
 * for it. Only the position is discarded — that is chosen when it is placed.
 */
internal fun defaultSpecFor(id: ControlId): ControlSpec =
    DEFAULT_LAYOUT.controls.firstOrNull { it.id == id } ?: FALLBACK_SPECS.getValue(id)

/**
 * The controls [DEFAULT_LAYOUT] does not place, and what they look like when one is added.
 *
 * Two families end up here. **L2 and R2**, whose analog halves the default reaches through
 * [ControlId.Trigger] instead, sit inboard of the shoulder row, clear of everything the default puts
 * down. **The four D-pad arms** sit in a cross around where the default's one-piece D-pad is, so
 * adding all four builds the shape you would expect rather than a pile.
 *
 * The arm offsets are `0.045` across and `0.08` down for a reason: the first is a fraction of screen
 * width and the second of screen height, and on the 16:9 the layouts are authored for those are the
 * same distance — the cross comes out square rather than squashed.
 *
 * Derived from [ControlId.ALL] rather than listed, so a control added to that list without a home in
 * the default layout fails here at class-load rather than the first time someone tries to add it.
 */
private val FALLBACK_SPECS: Map<ControlId, ControlSpec> = ControlId.ALL
    .filter { id -> DEFAULT_LAYOUT.controls.none { it.id == id } }
    .associateWith { id ->
        when (id) {
            is ControlId.Button -> ControlSpec(
                id = id,
                shape = ControlSpec.Shape.Circle(
                    centerX = if (id.button == GamepadButton.L2) 0.32f else 0.68f,
                    centerY = 0.08f,
                    radius = 0.055f,
                ),
                label = id.button.name,
            )

            is ControlId.DpadButton -> ControlSpec(
                id = id,
                shape = ControlSpec.Shape.Circle(
                    centerX = DPAD_CENTER_X + when (id.direction) {
                        ControlId.Direction.LEFT -> -DPAD_ARM_X
                        ControlId.Direction.RIGHT -> DPAD_ARM_X
                        else -> 0f
                    },
                    centerY = DPAD_CENTER_Y + when (id.direction) {
                        ControlId.Direction.UP -> -DPAD_ARM_Y
                        ControlId.Direction.DOWN -> DPAD_ARM_Y
                        else -> 0f
                    },
                    radius = 0.055f,
                ),
                label = id.direction.arrow(),
            )

            else -> error("no default spec for $id")
        }
    }

private const val DPAD_CENTER_X = 0.13f
private const val DPAD_CENTER_Y = 0.83f
private const val DPAD_ARM_X = 0.045f
private const val DPAD_ARM_Y = 0.08f

/** The glyph an arm wears. No art pack names the arms yet, so this is what gets drawn. */
private fun ControlId.Direction.arrow(): String = when (this) {
    ControlId.Direction.UP -> "▲"
    ControlId.Direction.DOWN -> "▼"
    ControlId.Direction.LEFT -> "◀"
    ControlId.Direction.RIGHT -> "▶"
}

internal fun ControlSpec.Shape.withCenter(x: Float, y: Float): ControlSpec.Shape = when (this) {
    is ControlSpec.Shape.Circle -> copy(centerX = x, centerY = y)
    is ControlSpec.Shape.Rect -> copy(centerX = x, centerY = y)
    is ControlSpec.Shape.Stick -> copy(centerX = x, centerY = y)
    is ControlSpec.Shape.Dpad -> copy(centerX = x, centerY = y)
    // The plate moves; the members keep their offsets from it, which is the whole point of one.
    is ControlSpec.Shape.Cluster -> copy(centerX = x, centerY = y)
}

/**
 * A cluster member's shape with its widths converted out of layout-unit fractions and back into
 * screen fractions, for a member being loosened into an ordinary control by [ungroupedControl].
 *
 * Only a rectangle's width needs it: it is the one size measured against the screen at top level
 * and against the unit inside a plate. [ratio] is `unit / width`.
 */
private fun ControlSpec.Shape.withScreenWidths(ratio: Float): ControlSpec.Shape =
    if (this is ControlSpec.Shape.Rect) copy(width = width * ratio) else this

/**
 * A resize, applied in pixels.
 *
 * The size fields of a [ControlSpec.Shape] are not all measured against the same thing —
 * [ControlSpec.Shape.Rect.width] is a fraction of screen width while every other size is a fraction
 * of [ResolvedLayout.unit]. Clamping or snapping them in their own units would therefore mean
 * different limits and a different grid per field, so each is converted to pixels against its own
 * reference, worked on there, and converted back.
 */
private class Scale(
    private val factorX: Float,
    private val factorY: Float,
    private val stepPixels: Float,
    private val unitPixels: Float,
    val widthPixels: Float,
) {
    val unit: Float get() = unitPixels

    /**
     * The one factor a shape with a single size field scales by.
     *
     * The two axes agree wherever this is read: a pinch scales uniformly, and [resizedControl]
     * only lets them differ for a shape that has a size per axis to put them on.
     */
    private val factor: Float get() = factorX

    /**
     * The one factor a whole cluster scales by, so its arrangement comes out the same shape.
     *
     * Every number inside a plate is a fraction of the unit — offsets and sizes alike — so a single
     * multiplier is all it takes, and the floor works out in those fractions directly without going
     * near pixels: a member of stored size `s` ends up at `s * f`, which has to stay above
     * [MIN_CONTROL_EXTENT] like any other control. The smallest member is the one that decides,
     * since it is the first to reach it.
     *
     * Snapping is applied to the plate's own extent once rather than to each member, for the reason
     * [withPlacement] snaps a drop point once: per-member rounding pulls the arrangement out of
     * shape. Doing it at all is what stops a plate sliding smoothly while every other control on the
     * same grid ratchets.
     */
    fun forCluster(members: List<ControlSpec>): Float {
        val sizes = members.flatMap { it.shape.sizeFields() }.filter { it > 0f }
        if (sizes.isEmpty()) return factor
        val limited = { f: Float -> f.coerceAtLeast(MIN_CONTROL_EXTENT / sizes.min()) }

        val scaled = limited(factor)
        if (stepPixels <= 0f) return scaled

        val extent = members.maxOf { abs(it.shape.centerX) + it.shape.extentX() }
        if (extent <= 0f) return scaled
        val snapped = snapToGrid(extent * unitPixels * scaled, stepPixels) / (extent * unitPixels)
        return limited(snapped)
    }

    /**
     * One size field scaled, in pixels against its own reference, held between the limits.
     *
     * [factor] is a parameter because a rectangle's two extents may be scaled by different amounts
     * — an edge handle drags one of them and leaves the other alone. It defaults to the uniform
     * factor, which is what a pinch and every single-size shape use.
     *
     * There is only a floor to hold it against, and it is the same one for every kind of control:
     * below it a thumb misses.
     */
    fun of(
        value: Float,
        referencePixels: Float,
        factor: Float = this.factor,
    ): Float {
        val min = MIN_CONTROL_EXTENT * unitPixels
        // Clamped before snapping and again after: snapping a clamped value can round it back under
        // the floor, and that is how a control becomes too small to touch and so impossible to grab
        // hold of again.
        var pixels = (value * referencePixels * factor).coerceAtLeast(min)
        if (stepPixels > 0f) pixels = snapToGrid(pixels, stepPixels).coerceAtLeast(min)
        return pixels / referencePixels
    }

    /** [of] against the horizontal factor — the width of the shapes that have one. */
    fun ofX(value: Float, referencePixels: Float): Float = of(value, referencePixels, factorX)

    /** [of] against the vertical factor. */
    fun ofY(value: Float, referencePixels: Float): Float = of(value, referencePixels, factorY)
}

/**
 * This shape scaled, which is per-variant for three reasons worth stating:
 *
 * - **Stick** scales its cap along with its base, so the knob keeps its proportion instead of
 *   growing into the well or vanishing inside it.
 * - **Dpad** scales only its radius. `deadZone` is already a fraction *of* that radius, so scaling
 *   it too would compound and the dead zone would swallow the cross.
 * - **Rect** scales both extents, each against its own reference; see [Scale].
 *
 * [area] says this is a [StickMode.DYNAMIC] stick, in which case the pinch lands on the spawning
 * area and the throw is left alone. That is what the gesture is on: the area is what is drawn and
 * what is touched, and growing the throw with it would mean a bigger region to start a stick in
 * cost you a longer sweep to push it — the opposite of what a bigger area is asked for. The throw
 * is still the radius, still tuned by pinching the stick in fixed mode, and kept across the switch.
 */
private fun ControlSpec.Shape.scaledBy(scale: Scale, area: Boolean): ControlSpec.Shape = when (this) {
    is ControlSpec.Shape.Circle -> copy(radius = scale.of(radius, scale.unit))

    is ControlSpec.Shape.Rect -> copy(
        width = scale.ofX(width, scale.widthPixels),
        height = scale.ofY(height, scale.unit),
    )

    is ControlSpec.Shape.Stick -> if (area) {
        copy(
            areaWidth = scale.ofX(areaWidth, scale.widthPixels),
            areaHeight = scale.ofY(areaHeight, scale.unit),
        )
    } else {
        val scaled = scale.of(radius, scale.unit)
        copy(radius = scaled, knobRadius = knobRadius * (scaled / radius))
    }

    is ControlSpec.Shape.Dpad -> copy(radius = scale.of(radius, scale.unit))

    // One factor, applied to every number every member has, offsets included -- see Scale.forCluster
    // for why that is enough and where the limits come in.
    is ControlSpec.Shape.Cluster -> {
        val factor = scale.forCluster(members)
        copy(members = members.map { it.copy(shape = it.shape.scaledUniformly(factor)) })
    }
}

/**
 * Every size field of a shape, in the units [MIN_CONTROL_EXTENT] is expressed in — which for a
 * cluster member is all of them, since a plate measures everything against the layout unit. Full
 * width and height for a rectangle rather than halves, matching what [Scale.of] clamps for one at
 * top level, so a member is held to the same floor as a control.
 */
private fun ControlSpec.Shape.sizeFields(): List<Float> = when (this) {
    is ControlSpec.Shape.Circle -> listOf(radius)
    is ControlSpec.Shape.Rect -> listOf(width, height)
    is ControlSpec.Shape.Stick -> listOf(radius)
    is ControlSpec.Shape.Dpad -> listOf(radius)
    is ControlSpec.Shape.Cluster -> members.flatMap { it.shape.sizeFields() }
}

/** Half this shape's horizontal extent, in its own units. */
private fun ControlSpec.Shape.extentX(): Float = when (this) {
    is ControlSpec.Shape.Circle -> radius
    is ControlSpec.Shape.Rect -> width / 2f
    is ControlSpec.Shape.Stick -> radius
    is ControlSpec.Shape.Dpad -> radius
    is ControlSpec.Shape.Cluster -> members.maxOf { abs(it.shape.centerX) + it.shape.extentX() }
}

/**
 * This shape multiplied through by [factor] — its offset from the plate's centre as well as its
 * size, with no clamping or snapping of its own, both of which happen once for the plate.
 */
private fun ControlSpec.Shape.scaledUniformly(factor: Float): ControlSpec.Shape {
    val moved = withCenter(centerX * factor, centerY * factor)
    return when (moved) {
        is ControlSpec.Shape.Circle -> moved.copy(radius = moved.radius * factor)
        is ControlSpec.Shape.Rect -> moved.copy(
            width = moved.width * factor,
            height = moved.height * factor,
        )

        is ControlSpec.Shape.Stick -> moved.copy(
            radius = moved.radius * factor,
            knobRadius = moved.knobRadius * factor,
        )

        // deadZone is already a fraction of radius, so scaling it too would compound.
        is ControlSpec.Shape.Dpad -> moved.copy(radius = moved.radius * factor)
        is ControlSpec.Shape.Cluster -> moved.copy(
            members = moved.members.map { it.copy(shape = it.shape.scaledUniformly(factor)) },
        )
    }
}

/** How far outside a control its ring — and so its handles — sit, as a fraction of its extent. */
private const val SELECTION_INSET_RATIO = 0.25f

/** How big a handle is drawn, as a fraction of the layout unit. */
private const val HANDLE_RADIUS_RATIO = 0.028f

/** How much wider than it looks a handle is to a finger. */
const val HANDLE_TOUCH_RATIO: Float = 1.8f
