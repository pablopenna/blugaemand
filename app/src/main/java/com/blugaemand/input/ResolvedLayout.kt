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
    /** Half-extents for rectangles and for a cluster's bounding box. Zero for everything else. */
    val halfWidth: Float,
    val halfHeight: Float,
    /**
     * A cluster's members, resolved against this control's centre. Empty for everything else.
     *
     * Their [index] is the member's ordinal **within this plate**, not a position in the layout;
     * only the plate itself has one of those. Nothing conflates the two because [ResolvedLayout]
     * only ever hands out top-level controls — a member is reached through its plate or not at all.
     */
    val members: List<ResolvedControl> = emptyList(),
) {
    val id: ControlId get() = spec.id

    /**
     * Whether this is a [StickMode.DYNAMIC] stick's spawning area — a box to touch rather than a
     * stick to push, and the one control on a pad that is *meant* to have others drawn over it.
     */
    val isDynamicStick: Boolean get() = spec.isDynamicStick()

    /**
     * Half this control's on-screen extent, per axis — the half-extents for the shapes measured as
     * a box, and [radius] for the round ones. Everything that has to know how much room a control
     * actually takes up asks these rather than restating the test.
     *
     * The box wins where a control has both, which is the dynamic stick alone: it carries a radius
     * for the throw of the stick it spawns *and* an area to spawn it in, and the area is the part
     * that is on the glass. Nothing else sets both, so the order only decides that one case.
     */
    val extentX: Float get() = if (halfWidth > 0f) halfWidth else radius
    val extentY: Float get() = if (halfHeight > 0f) halfHeight else radius

    fun contains(x: Float, y: Float): Boolean = when {
        // Before the shape is looked at, because a dynamic stick has a stick's shape and a
        // rectangle's touch area -- the one place the pad breaks "what is drawn is exactly what is
        // touchable", and it breaks it knowingly: the area is the control, and the stick inside it
        // is a transient the renderer places from the router.
        isDynamicStick -> abs(x - centerX) <= halfWidth && abs(y - centerY) <= halfHeight
        else -> containsShape(x, y)
    }

    private fun containsShape(x: Float, y: Float): Boolean = when (spec.shape) {
        // A cluster's touch area is its bounding box for the same reason the D-pad's is: what is
        // wanted is a plate with no dead spots in it, not four circles with gaps between them.
        is ControlSpec.Shape.Rect, is ControlSpec.Shape.Cluster ->
            abs(x - centerX) <= halfWidth && abs(y - centerY) <= halfHeight

        // The D-pad's touch area is its bounding square, so diagonal presses near the corners
        // register instead of falling into the gap a circle would leave.
        is ControlSpec.Shape.Dpad -> abs(x - centerX) <= radius && abs(y - centerY) <= radius
        else -> hypot(x - centerX, y - centerY) <= radius
    }

    /**
     * The member a touch inside this plate means, or null if this is not one.
     *
     * A member whose own area the touch is in wins; failing that, the nearest centre does. Nearest
     * centre alone would split the plate at the midpoint between two members and ignore how big
     * either is — on the centre cluster, where Home is larger than the buttons beside it, that line
     * falls inside Home's own glyph and touching its edge would send Back. Falling back to it
     * afterwards is what keeps the plate free of dead spots, so a thumb anywhere on it sends
     * something and can roll from one member to the next without lifting.
     */
    fun memberAt(x: Float, y: Float): ResolvedControl? =
        members.filter { it.contains(x, y) }.minByOrNull { hypot(x - it.centerX, y - it.centerY) }
            ?: members.minByOrNull { hypot(x - it.centerX, y - it.centerY) }
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
        resolve(
            index = index,
            spec = spec,
            centerX = spec.shape.centerX * width,
            centerY = spec.shape.centerY * height,
            widthReference = width,
        )
    }

    /**
     * One control in pixels, given a centre already worked out and what its widths measure against.
     *
     * Both of those differ between a top-level control and a cluster member — a member's centre is
     * an offset from its plate and everything about it is measured against [unit] — and everything
     * else about resolving one is identical, which is why this takes them rather than deciding.
     */
    private fun resolve(
        index: Int,
        spec: ControlSpec,
        centerX: Float,
        centerY: Float,
        widthReference: Float,
    ): ResolvedControl = when (val shape = spec.shape) {
        is ControlSpec.Shape.Circle -> ResolvedControl(
            index, spec, centerX, centerY, radius = shape.radius * unit, knobRadius = 0f,
            halfWidth = 0f, halfHeight = 0f,
        )

        // A dynamic stick carries both: the radius is the throw of the stick it spawns, the
        // half-extents are the area it may be spawned in. A fixed one has no area, and leaving it
        // at zero is what keeps `extentX` answering with the radius for it.
        is ControlSpec.Shape.Stick -> ResolvedControl(
            index, spec, centerX, centerY, radius = shape.radius * unit,
            knobRadius = shape.knobRadius * unit,
            halfWidth = if (spec.isDynamicStick()) shape.areaWidth * widthReference / 2f else 0f,
            halfHeight = if (spec.isDynamicStick()) shape.areaHeight * unit / 2f else 0f,
        )

        is ControlSpec.Shape.Dpad -> ResolvedControl(
            index, spec, centerX, centerY, radius = shape.radius * unit, knobRadius = 0f,
            halfWidth = 0f, halfHeight = 0f,
        )

        // Rectangles keep their width relative to the screen: the shoulder buttons are meant
        // to stretch across the top edge however wide it is.
        is ControlSpec.Shape.Rect -> ResolvedControl(
            index, spec, centerX, centerY, radius = 0f, knobRadius = 0f,
            halfWidth = shape.width * widthReference / 2f, halfHeight = shape.height * unit / 2f,
        )

        is ControlSpec.Shape.Cluster -> {
            val members = shape.members.mapIndexed { ordinal, member ->
                resolve(
                    index = ordinal,
                    spec = member,
                    centerX = centerX + member.shape.centerX * unit,
                    centerY = centerY + member.shape.centerY * unit,
                    widthReference = unit,
                )
            }
            // Rounded up to whichever side reaches furthest, because a ResolvedControl is a centre
            // and two half-extents and cannot say "further left than right". A plate whose members
            // are not symmetric about its centre therefore carries dead margin on the near side --
            // harmless, since a touch there still resolves to the nearest member, and only visible
            // in the selection ring and the on-screen clamp being a little generous.
            ResolvedControl(
                index, spec, centerX, centerY, radius = 0f, knobRadius = 0f,
                halfWidth = members.maxOf { abs(it.centerX - centerX) + it.extentX },
                halfHeight = members.maxOf { abs(it.centerY - centerY) + it.extentY },
                members = members,
            )
        }
    }

    /**
     * The control under a touch point, or null. When controls overlap the nearest centre wins,
     * which keeps behaviour predictable in tight clusters like the face buttons.
     *
     * **Except that a dynamic stick's area always loses.** A Start button sitting inside one is a
     * Start button, and nearest-centre alone would hand a touch on its edge to an area whose centre
     * happens to be closer. So an area is a background: anything else containing the point beats
     * it, however far away its centre is, and the areas are only considered when nothing else was
     * touched. Between two areas — a layout nobody sensible will make, but a representable one —
     * nearest centre decides as usual.
     */
    fun hitTest(x: Float, y: Float): ResolvedControl? {
        val touched = controls.filter { it.contains(x, y) }
        val drawnOnTop = touched.filterNot { it.isDynamicStick }
        return drawnOnTop.ifEmpty { touched }.minByOrNull { hypot(x - it.centerX, y - it.centerY) }
    }

    private companion object {
        /** Height as a fraction of width on a 16:9 screen, the ratio the layouts are authored for. */
        const val REFERENCE_ASPECT = 9f / 16f
    }
}
