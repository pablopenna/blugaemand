package com.blugaemand.input

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.layouts.DEFAULT_LAYOUT
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
    val insetX = if (control.radius > 0f) control.radius else control.halfWidth
    val insetY = if (control.radius > 0f) control.radius else control.halfHeight

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
    return layout.replacingShape(index) { it.scaledBy(scale) }
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

private fun ControlSpec.Shape.withCenter(x: Float, y: Float): ControlSpec.Shape = when (this) {
    is ControlSpec.Shape.Circle -> copy(centerX = x, centerY = y)
    is ControlSpec.Shape.Rect -> copy(centerX = x, centerY = y)
    is ControlSpec.Shape.Stick -> copy(centerX = x, centerY = y)
    is ControlSpec.Shape.Dpad -> copy(centerX = x, centerY = y)
}

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
}
