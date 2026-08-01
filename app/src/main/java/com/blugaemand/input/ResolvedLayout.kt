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
    val controls: List<ResolvedControl> = layout.controls.map { spec ->
        val cx = spec.shape.centerX * width
        val cy = spec.shape.centerY * height
        when (val shape = spec.shape) {
            is ControlSpec.Shape.Circle -> ResolvedControl(
                spec, cx, cy, radius = shape.radius * height, knobRadius = 0f,
                halfWidth = 0f, halfHeight = 0f,
            )

            is ControlSpec.Shape.Stick -> ResolvedControl(
                spec, cx, cy, radius = shape.radius * height,
                knobRadius = shape.knobRadius * height, halfWidth = 0f, halfHeight = 0f,
            )

            is ControlSpec.Shape.Dpad -> ResolvedControl(
                spec, cx, cy, radius = shape.radius * height, knobRadius = 0f,
                halfWidth = 0f, halfHeight = 0f,
            )

            is ControlSpec.Shape.Rect -> ResolvedControl(
                spec, cx, cy, radius = 0f, knobRadius = 0f,
                halfWidth = shape.width * width / 2f, halfHeight = shape.height * height / 2f,
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

    /** Smallest control dimension, useful for scaling text and stroke widths. */
    val referenceSize: Float
        get() = controls.minOfOrNull { c ->
            if (c.radius > 0f) c.radius else min(c.halfWidth, c.halfHeight)
        } ?: min(width, height) * 0.05f
}
