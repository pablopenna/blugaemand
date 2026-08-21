package com.blugaemand.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import com.blugaemand.input.GamepadLayout
import com.blugaemand.input.HANDLE_TOUCH_RATIO
import com.blugaemand.input.Placement
import com.blugaemand.input.ResizeHandle
import com.blugaemand.input.ResolvedControl
import com.blugaemand.input.ResolvedLayout
import com.blugaemand.input.gridStep
import com.blugaemand.input.handleAt
import com.blugaemand.input.handleCenterX
import com.blugaemand.input.handleCenterY
import com.blugaemand.input.handleRadius
import com.blugaemand.input.movedControl
import com.blugaemand.input.previewOf
import com.blugaemand.input.resizedControl
import com.blugaemand.input.selectionInset
import com.blugaemand.input.withPlacement
import com.blugaemand.ui.theme.OverlayColors
import com.blugaemand.ui.theme.PadColors
import kotlin.math.hypot

/**
 * The pad with its wiring pulled out: the same controls in the same places, but a finger moves one
 * instead of pressing it.
 *
 * A screen of its own rather than a mode on [GamepadScreen], because the two share no input at
 * all — there is no [com.blugaemand.input.TouchRouter] here and nothing is sent to a host. What
 * they do share is [drawControl], which is where the reuse belongs: the editor has to draw a
 * control exactly as the pad will, or it is not showing you what you are making.
 *
 * All of the arithmetic lives in `LayoutEdits`, which is plain Kotlin and tested as such. What is
 * left here is turning fingers into calls on it.
 */
@Composable
fun EditorScreen(
    layout: GamepadLayout,
    /** Index of the control being edited, in [GamepadLayout.controls]; see [ResolvedControl.index]. */
    selected: Int?,
    onSelect: (Int) -> Unit,
    onLayoutChange: (GamepadLayout) -> Unit,
    /** Waiting to be dropped, if anything is. While set, a touch places rather than drags. */
    pending: Placement?,
    /**
     * The layout with [pending] dropped where the finger lifted. Done here rather than by the
     * caller because the drop point is in pixels, and the pixels are this composable's business.
     */
    onPlaced: (GamepadLayout) -> Unit,
    snapToGrid: Boolean,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val padStyle = rememberPadStyle(layout)

    var size by remember { mutableStateOf(IntSize.Zero) }
    val resolved = remember(layout, size) {
        if (size.width == 0 || size.height == 0) null
        else ResolvedLayout(layout, size.width.toFloat(), size.height.toFloat())
    }

    // Every edit builds a new ResolvedLayout, so keying the gesture below on it would re-key it
    // mid-drag and cancel the very gesture doing the editing -- the trap already hit twice in the
    // hold-to-open pill. The gesture is keyed on the surface size instead, which does not change
    // while a finger is down, and reads the current geometry through these.
    val currentResolved by rememberUpdatedState(resolved)
    val currentSelected by rememberUpdatedState(selected)
    val currentSnap by rememberUpdatedState(snapToGrid)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnLayoutChange by rememberUpdatedState(onLayoutChange)
    val currentPending by rememberUpdatedState(pending)
    val currentOnPlaced by rememberUpdatedState(onPlaced)

    // Where the thing waiting to be dropped is currently hovering. Null means untouched so far, in
    // which case it shows in the middle of the screen -- somewhere visible, so that what is about to
    // be added can be seen before a finger goes anywhere near it.
    var previewAt by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PadColors.Background)
            .onSizeChanged { size = it },
    ) {
        if (resolved == null) return@Box

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(size) {
                    awaitEachGesture {
                        // The main pass, and unconsumed only -- unlike the pad, which claims events
                        // on the initial pass so nothing can reinterpret a thumb as a scroll. Here
                        // the editor bar sits over this canvas and has to win the taps that land on
                        // it, which is exactly what deferring to the normal order gives.
                        val down = awaitFirstDown()
                        // Captured for the whole gesture; see the note on accumulating deltas below.
                        val base = currentResolved ?: return@awaitEachGesture

                        // Placing takes over the whole surface, so a touch means one thing at a
                        // time: while something is waiting to be dropped, nothing is being dragged.
                        val placing = currentPending
                        if (placing != null) {
                            previewAt = down.position
                            do {
                                val event = awaitPointerEvent()
                                event.changes.lastOrNull()?.let { previewAt = it.position }
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })

                            previewAt?.let {
                                currentOnPlaced(
                                    base.withPlacement(placing, it.x, it.y, currentSnap),
                                )
                            }
                            previewAt = null
                            return@awaitEachGesture
                        }

                        // A handle belongs to the selection and is drawn outside the control, so
                        // it is asked about before the pad is hit-tested: a corner indicator on a
                        // button sitting over another control has to resize the one it belongs to,
                        // not select the one underneath it.
                        val grabbed = currentSelected
                            ?.let { base.controls.getOrNull(it) }
                            ?.handleAt(
                                down.position.x,
                                down.position.y,
                                base.handleRadius * HANDLE_TOUCH_RATIO,
                            )
                        if (grabbed != null) {
                            val index = currentSelected ?: return@awaitEachGesture
                            var drag = Offset.Zero
                            do {
                                val event = awaitPointerEvent()
                                drag += event.calculatePan()
                                if (drag != Offset.Zero) {
                                    currentOnLayoutChange(
                                        base.resizedControl(
                                            index, grabbed, drag.x, drag.y, currentSnap,
                                        ),
                                    )
                                }
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                            return@awaitEachGesture
                        }

                        // Deltas accumulate and are applied to `base`, rather than each frame's
                        // delta being applied to the result of the last: with snapping on, a drag
                        // slower than half a grid step per frame would otherwise round back to
                        // where it started every time and the control would never move at all.
                        val target = base.hitTest(down.position.x, down.position.y)
                            // A miss keeps whatever was selected -- losing the control you were
                            // working on to a fat-fingered tap would be worse than nothing
                            // happening -- but only what you actually touched can be dragged.
                            ?: return@awaitEachGesture
                        currentOnSelect(target.index)

                        var pan = Offset.Zero
                        var zoom = 1f
                        do {
                            val event = awaitPointerEvent()
                            pan += event.calculatePan()
                            zoom *= event.calculateZoom()

                            var edited: GamepadLayout? = null
                            // Both are guarded, because applying them unconditionally would snap
                            // a control the moment it was touched: a mere tap would shunt it onto
                            // the grid, and a plain drag would resize it on the way.
                            if (pan != Offset.Zero) {
                                edited = base.movedControl(
                                    target.index, pan.x, pan.y, currentSnap,
                                )
                            }
                            if (zoom != 1f) {
                                edited = ResolvedLayout(
                                    edited ?: base.layout, base.width, base.height,
                                ).resizedControl(target.index, zoom, currentSnap)
                            }
                            edited?.let(currentOnLayoutChange)

                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            if (snapToGrid) drawGrid(resolved.gridStep)

            // The pad's own opacity, so the editor shows what the pad will show. The grid, the
            // selection and the handles are outside it: they are the editor's furniture rather
            // than part of the layout, and a faint pad is exactly when they are most needed.
            withOpacity(padStyle.opacity) {
                for (control in resolved.controls) {
                    drawControl(
                        control = control,
                        style = padStyle,
                        // Nothing is held in here, and a control drawn pressed would be a control
                        // showing its pressed colour for no reason the user can act on.
                        pressed = false,
                        // Nothing is touching anything in here, which for a dynamic stick means
                        // its area is drawn and the stick inside it is not -- exactly what the pad
                        // shows before a thumb lands.
                        stickTouch = null,
                        textMeasurer = textMeasurer,
                    )
                }
            }

            selected?.let { resolved.controls.getOrNull(it) }?.let {
                drawSelection(it)
                drawHandles(it, resolved.handleRadius)
            }

            // Drawn last so it reads as sitting above the pad rather than as part of it. Each
            // member is drawn as it will look, ringed in the accent -- the ring is what says "not
            // yet", and showing the real control is what makes an arrangement worth previewing.
            if (pending != null) {
                val at = previewAt ?: Offset(size.width / 2f, size.height / 2f)
                for (control in resolved.previewOf(pending, at.x, at.y, snapToGrid)) {
                    withOpacity(padStyle.opacity) {
                        drawControl(control, padStyle, false, null, textMeasurer)
                    }
                    drawSelection(control)
                }
            }
        }
    }
}

/** The grid sizes and positions snap to, faint enough to read the pad through. */
private fun DrawScope.drawGrid(step: Float) {
    if (step <= 0f) return
    var x = step
    while (x < size.width) {
        drawLine(PadColors.ControlStroke, Offset(x, 0f), Offset(x, size.height), GRID_WIDTH)
        x += step
    }
    var y = step
    while (y < size.height) {
        drawLine(PadColors.ControlStroke, Offset(0f, y), Offset(size.width, y), GRID_WIDTH)
        y += step
    }
}

/**
 * A box around the control being edited, plus a dot on its centre — which is the part that stays
 * visible when the control itself is under a thumb.
 */
private fun DrawScope.drawSelection(control: ResolvedControl) {
    // Through the resolved extents rather than restating which field a shape measures by, so the
    // ring goes round a dynamic stick's area -- the part of it that is on the glass -- and not
    // round the throw of the stick it spawns.
    val halfWidth = control.extentX
    val halfHeight = control.extentY
    val inset = control.selectionInset

    drawRoundRect(
        color = OverlayColors.Accent,
        topLeft = Offset(
            control.centerX - halfWidth - inset,
            control.centerY - halfHeight - inset,
        ),
        size = Size((halfWidth + inset) * 2f, (halfHeight + inset) * 2f),
        cornerRadius = CornerRadius(inset),
        style = Stroke(width = inset * 0.3f),
    )
    drawCircle(
        color = OverlayColors.Accent,
        radius = inset * 0.4f,
        center = Offset(control.centerX, control.centerY),
    )
}

/**
 * The eight resize indicators, on the corners and the middle of each edge of the selection.
 *
 * Each wears the arrow it pulls along, which is what says a handle is for dragging and which way:
 * the corners scale the whole control and the edges stretch their own axis. They are drawn on the
 * ring rather than inside it so they read as belonging to the selection and not to the pad, and so
 * a small control is not buried under its own handles.
 */
private fun DrawScope.drawHandles(control: ResolvedControl, radius: Float) {
    for (handle in ResizeHandle.entries) {
        val center = Offset(control.handleCenterX(handle), control.handleCenterY(handle))
        drawCircle(OverlayColors.Accent, radius, center)
        drawArrows(center, radius, handle.dx.toFloat(), handle.dy.toFloat())
    }
}

/**
 * A double-headed arrow through [center], pointing along ([dirX], [dirY]) and back.
 *
 * Drawn in the background colour, so it is a hole in the handle rather than another thing on top of
 * it — at this size an outlined glyph would just read as noise.
 */
private fun DrawScope.drawArrows(center: Offset, radius: Float, dirX: Float, dirY: Float) {
    val length = hypot(dirX, dirY)
    val axis = Offset(dirX / length, dirY / length)
    val tip = axis * (radius * ARROW_REACH)
    val stroke = radius * ARROW_WIDTH
    drawLine(PadColors.Background, center - tip, center + tip, stroke, StrokeCap.Round)

    // The heads: two short lines off each tip, at 45 degrees either side of the shaft, which is
    // the rotation of the axis by +/-135 degrees written out.
    val head = radius * ARROW_HEAD
    for (sign in listOf(1f, -1f)) {
        val point = center + tip * sign
        for (turn in listOf(1f, -1f)) {
            val back = Offset(
                -axis.x * HALF_ROOT_TWO - turn * axis.y * HALF_ROOT_TWO,
                -axis.y * HALF_ROOT_TWO + turn * axis.x * HALF_ROOT_TWO,
            )
            drawLine(PadColors.Background, point, point + back * (head * sign), stroke, StrokeCap.Round)
        }
    }
}

private const val GRID_WIDTH = 1f

/** How far along the handle's radius the arrow reaches, and how it is proportioned within that. */
private const val ARROW_REACH = 0.55f
private const val ARROW_HEAD = 0.5f
private const val ARROW_WIDTH = 0.16f
private const val HALF_ROOT_TWO = 0.7071068f
