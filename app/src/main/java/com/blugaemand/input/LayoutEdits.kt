package com.blugaemand.input

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.layouts.DEFAULT_LAYOUT
import kotlin.math.abs
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

/** Largest a control may be made. The PS5 D-pad, the biggest thing shipped, is 0.21f. */
const val MAX_CONTROL_EXTENT: Float = 0.40f

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
    val scale = Scale(factor, if (snap) gridStep else 0f, unit, width)
    // Pulled back on screen afterwards because a plate grows about its centre and its whole
    // bounding box grows with it, so one near an edge would otherwise scale straight off the side.
    // A single control can do that too, but by half a radius rather than half a plate.
    return ResolvedLayout(layout.replacingShape(index) { it.scaledBy(scale) }, width, height)
        .let { grown -> grown.movedControl(index, 0f, 0f, snap = false) }
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
    // See ControlSpec.describe, which is what the editor actually calls.
    ControlId.Cluster -> "Control group"
}

/**
 * How a control is named in the editor, given the whole spec.
 *
 * A cluster needs this rather than [ControlId.describe]: its members live on its shape, so its id
 * alone cannot say what it is, and *"Control group"* is no use in a list where there may be three of
 * them. Naming the members instead — *"Y / X / B / A"* — says which plate is selected.
 */
fun ControlSpec.describe(): String = when (val shape = shape) {
    is ControlSpec.Shape.Cluster -> shape.members.joinToString(" / ") { it.describe() }
    else -> id.describe()
}

/** [value] rounded to the nearest multiple of [step]. */
fun snapToGrid(value: Float, step: Float): Float =
    if (step <= 0f) value else (value / step).roundToInt() * step

// -- Internals ----------------------------------------------------------------------------

private fun ControlId.Side.spelled(): String =
    name.lowercase().replaceFirstChar { it.uppercase() }

private fun GamepadLayout.replacingShape(
    index: Int,
    transform: (ControlSpec.Shape) -> ControlSpec.Shape,
): GamepadLayout = copy(
    controls = controls.mapIndexed { i, spec ->
        if (i == index) spec.copy(shape = transform(spec.shape)) else spec
    },
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
    private val factor: Float,
    private val stepPixels: Float,
    private val unitPixels: Float,
    val widthPixels: Float,
) {
    val unit: Float get() = unitPixels

    /**
     * The one factor a whole cluster scales by, so its arrangement comes out the same shape.
     *
     * Every number inside a plate is a fraction of the unit — offsets and sizes alike — so a single
     * multiplier is all it takes, and the limits work out in those fractions directly without going
     * near pixels: a member of stored size `s` ends up at `s * f`, which has to land between
     * [MIN_CONTROL_EXTENT] and [MAX_CONTROL_EXTENT] like any other control. The smallest member sets
     * the floor and the largest the ceiling, and those two can genuinely cross on a hand-edited file
     * that already breaks the limits, hence the ordered bounds — an inverted `coerceIn` throws, and
     * it would throw mid-pinch.
     *
     * Snapping is applied to the plate's own extent once rather than to each member, for the reason
     * [withPlacement] snaps a drop point once: per-member rounding pulls the arrangement out of
     * shape. Doing it at all is what stops a plate sliding smoothly while every other control on the
     * same grid ratchets.
     */
    fun forCluster(members: List<ControlSpec>): Float {
        val sizes = members.flatMap { it.shape.sizeFields() }.filter { it > 0f }
        if (sizes.isEmpty()) return factor
        val low = MIN_CONTROL_EXTENT / sizes.min()
        val high = MAX_CONTROL_EXTENT / sizes.max()
        val limited = { f: Float -> f.coerceIn(minOf(low, high), maxOf(low, high)) }

        val scaled = limited(factor)
        if (stepPixels <= 0f) return scaled

        val extent = members.maxOf { abs(it.shape.centerX) + it.shape.extentX() }
        if (extent <= 0f) return scaled
        val snapped = snapToGrid(extent * unitPixels * scaled, stepPixels) / (extent * unitPixels)
        return limited(snapped)
    }

    fun of(value: Float, referencePixels: Float): Float {
        val min = MIN_CONTROL_EXTENT * unitPixels
        val max = MAX_CONTROL_EXTENT * unitPixels
        // Clamped before snapping and again after: snapping a clamped value can round it back
        // outside the limits, and at the bottom end that is how a control becomes too small to
        // touch and so impossible to grab hold of again.
        var pixels = (value * referencePixels * factor).coerceIn(min, max)
        if (stepPixels > 0f) pixels = snapToGrid(pixels, stepPixels).coerceIn(min, max)
        return pixels / referencePixels
    }
}

/**
 * This shape scaled, which is per-variant for three reasons worth stating:
 *
 * - **Stick** scales its cap along with its base, so the knob keeps its proportion instead of
 *   growing into the well or vanishing inside it.
 * - **Dpad** scales only its radius. `deadZone` is already a fraction *of* that radius, so scaling
 *   it too would compound and the dead zone would swallow the cross.
 * - **Rect** scales both extents, each against its own reference; see [Scale].
 */
private fun ControlSpec.Shape.scaledBy(scale: Scale): ControlSpec.Shape = when (this) {
    is ControlSpec.Shape.Circle -> copy(radius = scale.of(radius, scale.unit))

    is ControlSpec.Shape.Rect -> copy(
        width = scale.of(width, scale.widthPixels),
        height = scale.of(height, scale.unit),
    )

    is ControlSpec.Shape.Stick -> {
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
 * Every size field of a shape, in the units [MIN_CONTROL_EXTENT] and [MAX_CONTROL_EXTENT] are
 * expressed in — which for a cluster member is all of them, since a plate measures everything
 * against the layout unit. Full width and height for a rectangle rather than halves, matching what
 * [Scale.of] clamps for one at top level, so a member is held to the same limits as a control.
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
