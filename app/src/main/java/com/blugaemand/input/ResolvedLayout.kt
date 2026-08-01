package com.blugaemand.input

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * A [ControlSpec] converted from normalised coordinates into pixels for a specific surface size.
 *
 * Resolving once per size change rather than per touch event keeps hit-testing to arithmetic, and
 * gives the renderer and the router a single shared source of geometry so what is drawn is exactly
 * what is touchable.
 */
data class ResolvedControl(
    /**
     * Where this control sits in its layout's list, and the only thing that tells two of them
     * apart: a layout may hold the same [ControlId] more than once — two A buttons, one under each
     * thumb — so the id says what a control *does* and this says which one it *is*. Anything about
     * a particular control rather than about the button it drives keys on this.
     */
    val index: Int,
    val spec: ControlSpec,
    val centerX: Float,
    val centerY: Float,
    /** Radius for circles, sticks and the D-pad's bounding square. Zero for rectangles. */
    val radius: Float,
    /** Radius of the moving cap, for sticks only. */
    val knobRadius: Float,
    /** Half-extents for rectangles. Zero for everything else. */
    val halfWidth: Float,
    val halfHeight: Float,
) {
    val id: ControlId get() = spec.id

    fun contains(x: Float, y: Float): Boolean = when (spec.shape) {
        is ControlSpec.Shape.Rect -> abs(x - centerX) <= halfWidth && abs(y - centerY) <= halfHeight
        // The D-pad's touch area is its bounding square, so diagonal presses near the corners
        // register instead of falling into the gap a circle would leave.
        is ControlSpec.Shape.Dpad -> abs(x - centerX) <= radius && abs(y - centerY) <= radius
        else -> hypot(x - centerX, y - centerY) <= radius
    }
}

/**
 * A whole [GamepadLayout] resolved against a surface of [width] x [height] pixels.
 */
class ResolvedLayout(
    val layout: GamepadLayout,
    val width: Float,
    val height: Float,
) {
    /**
     * The scale that control sizes are measured against.
     *
     * Sizing purely off height breaks down on squarer screens: a 4:3 tablet has far more vertical
     * room than a 16:9 phone of the same width, so height-derived radii grow while the horizontal
     * gaps between controls — which follow width — do not, and neighbouring buttons end up
     * touching. Capping the scale at what a 16:9 screen of this width would have keeps the pad's
     * proportions intact on any aspect ratio. On 16:9 and wider this is just the height, so the
     * layout renders exactly as authored.
     */
    val unit: Float = min(height, width * REFERENCE_ASPECT)

    val controls: List<ResolvedControl> = layout.controls.mapIndexed { index, spec ->
        val cx = spec.shape.centerX * width
        val cy = spec.shape.centerY * height
        when (val shape = spec.shape) {
            is ControlSpec.Shape.Circle -> ResolvedControl(
                index, spec, cx, cy, radius = shape.radius * unit, knobRadius = 0f,
                halfWidth = 0f, halfHeight = 0f,
            )

            is ControlSpec.Shape.Stick -> ResolvedControl(
                index, spec, cx, cy, radius = shape.radius * unit,
                knobRadius = shape.knobRadius * unit, halfWidth = 0f, halfHeight = 0f,
            )

            is ControlSpec.Shape.Dpad -> ResolvedControl(
                index, spec, cx, cy, radius = shape.radius * unit, knobRadius = 0f,
                halfWidth = 0f, halfHeight = 0f,
            )

            // Rectangles keep their width relative to the screen: the shoulder buttons are meant
            // to stretch across the top edge however wide it is.
            is ControlSpec.Shape.Rect -> ResolvedControl(
                index, spec, cx, cy, radius = 0f, knobRadius = 0f,
                halfWidth = shape.width * width / 2f, halfHeight = shape.height * unit / 2f,
            )
        }
    }

    /**
     * The control under a touch point, or null. When controls overlap the nearest centre wins,
     * which keeps behaviour predictable in tight clusters like the face buttons.
     */
    fun hitTest(x: Float, y: Float): ResolvedControl? =
        controls.filter { it.contains(x, y) }
            .minByOrNull { hypot(x - it.centerX, y - it.centerY) }

    private companion object {
        /** Height as a fraction of width on a 16:9 screen, the ratio the layouts are authored for. */
        const val REFERENCE_ASPECT = 9f / 16f
    }
}
