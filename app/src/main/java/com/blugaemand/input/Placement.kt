package com.blugaemand.input

/**
 * Controls waiting to be dropped, positioned **relative to the point they will be dropped on**.
 *
 * Every member's shape centre is an offset from that point rather than a position on the screen, so
 * a single control sits at the origin and a group is arranged around it. That is what lets one code
 * path serve both: adding one button and adding a face diamond differ only in how many members come
 * along.
 *
 * The reason any of this exists is that a control's default position is almost never the wanted one.
 * Placing first and dropping second means the arrangement arrives where the thumb asked for it,
 * rather than somewhere it then has to be dragged from — four times over, for a group.
 */
data class Placement(val name: String, val controls: List<ControlSpec>) {

    /** This placement's controls translated onto [x], [y], in normalised coordinates. */
    fun at(x: Float, y: Float): List<ControlSpec> = controls.map { spec ->
        spec.copy(shape = spec.shape.movedTo(spec.shape.centerX + x, spec.shape.centerY + y))
    }

    companion object {

        /**
         * One control on its own, taking the shape and label [DEFAULT_LAYOUT] gives it and dropping
         * only its position — so a control arrives the size the built-in pad uses it at, carrying
         * the label the host will report for it, wherever it is put.
         */
        fun of(id: ControlId): Placement {
            val spec = defaultSpecFor(id)
            return Placement(id.describe(), listOf(spec.centredOnOrigin()))
        }

        /**
         * Several controls, keeping their arrangement and re-centred on their common middle.
         *
         * Centred on the **bounding box** rather than on the average position: with an odd member
         * out — the three centre buttons, where two are small and one is not — an average drifts
         * towards the crowded side, and the group would land beside the thumb rather than under it.
         */
        fun of(name: String, specs: List<ControlSpec>): Placement {
            require(specs.isNotEmpty()) { "a placement needs at least one control" }
            val midX = (specs.minOf { it.shape.centerX } + specs.maxOf { it.shape.centerX }) / 2f
            val midY = (specs.minOf { it.shape.centerY } + specs.maxOf { it.shape.centerY }) / 2f
            return Placement(
                name,
                specs.map { it.copy(shape = it.shape.movedTo(it.shape.centerX - midX, it.shape.centerY - midY)) },
            )
        }
    }
}

/**
 * [layout] with [placement] dropped at a point on screen, given in pixels.
 *
 * The drop point is snapped once and the members keep their offsets from it, so a group lands on the
 * grid **as a unit** — snapping each member on its own would pull the arrangement out of shape.
 * Each is then clamped on screen individually, which is the one thing that can distort a group, and
 * only ever at the very edge.
 */
fun ResolvedLayout.withPlacement(
    placement: Placement,
    atXPixels: Float,
    atYPixels: Float,
    snap: Boolean,
): GamepadLayout {
    val x = if (snap) snapToGrid(atXPixels, gridStep) else atXPixels
    val y = if (snap) snapToGrid(atYPixels, gridStep) else atYPixels

    val placed = placement.at(x / width, y / height).map { spec ->
        spec.copy(shape = spec.shape.clampedOnScreen(this))
    }
    return layout.copy(controls = layout.controls + placed)
}

/** Where [placement] would land if dropped at a point, for drawing the preview that follows a finger. */
fun ResolvedLayout.previewOf(
    placement: Placement,
    atXPixels: Float,
    atYPixels: Float,
    snap: Boolean,
): List<ResolvedControl> =
    ResolvedLayout(
        withPlacement(placement, atXPixels, atYPixels, snap),
        width,
        height,
    ).controls.takeLast(placement.controls.size)

// -- Internals ----------------------------------------------------------------------------

private fun ControlSpec.centredOnOrigin(): ControlSpec = copy(shape = shape.movedTo(0f, 0f))

private fun ControlSpec.Shape.movedTo(x: Float, y: Float): ControlSpec.Shape = when (this) {
    is ControlSpec.Shape.Circle -> copy(centerX = x, centerY = y)
    is ControlSpec.Shape.Rect -> copy(centerX = x, centerY = y)
    is ControlSpec.Shape.Stick -> copy(centerX = x, centerY = y)
    is ControlSpec.Shape.Dpad -> copy(centerX = x, centerY = y)
}

/**
 * This shape pulled back onto the screen by its own extent, so a control dropped near an edge is
 * fully reachable rather than half over the side.
 */
private fun ControlSpec.Shape.clampedOnScreen(layout: ResolvedLayout): ControlSpec.Shape {
    // Resolving a single-control layout is the cheapest way to reuse the extent arithmetic that
    // ResolvedLayout already owns, rather than restating which field measures against what.
    val probe = ResolvedLayout(
        GamepadLayout("probe", "probe", listOf(ControlSpec(ControlId.Dpad, this))),
        layout.width,
        layout.height,
    ).controls.single()

    val insetX = if (probe.radius > 0f) probe.radius else probe.halfWidth
    val insetY = if (probe.radius > 0f) probe.radius else probe.halfHeight

    val x = (probe.centerX.coerceIn(insetX, maxOf(insetX, layout.width - insetX)) / layout.width)
    val y = (probe.centerY.coerceIn(insetY, maxOf(insetY, layout.height - insetY)) / layout.height)
    return movedTo(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}
