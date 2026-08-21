package com.blugaemand.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import com.blugaemand.hid.GamepadState
import com.blugaemand.input.ControlSpec
import com.blugaemand.input.GamepadLayout
import com.blugaemand.input.ResolvedLayout
import com.blugaemand.input.TouchRouter
import com.blugaemand.ui.theme.PadColors

/**
 * The touch surface. Takes its [layout] as a parameter rather than reading a global, so the layout
 * editor planned for a later iteration only has to hand it a different instance.
 *
 * [onStateChange] fires on every pointer event; throttling to a sane report rate is the service's
 * job, not the UI's.
 */
@Composable
fun GamepadScreen(
    layout: GamepadLayout,
    onStateChange: (GamepadState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnStateChange by rememberUpdatedState(onStateChange)
    val textMeasurer = rememberTextMeasurer()
    // Resolved here rather than in the draw lambda: `painterResource` is a composable, and the
    // canvas below is not composition.
    val padStyle = rememberPadStyle(layout)

    var size by remember { mutableStateOf(IntSize.Zero) }
    val resolved = remember(layout, size) {
        if (size.width == 0 || size.height == 0) null
        else ResolvedLayout(layout, size.width.toFloat(), size.height.toFloat())
    }
    val router = remember(resolved) { resolved?.let { TouchRouter(it) } }

    // Bumped on every pointer event. The draw lambda reads it, so touches repaint the canvas
    // without forcing a recomposition of the whole screen.
    var tick by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PadColors.Background)
            .onSizeChanged { size = it },
    ) {
        if (resolved == null || router == null) return@Box

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(router) {
                    awaitPointerEventScope {
                        while (true) {
                            // Initial pass: claim the events before anything else can interpret
                            // them as scrolls or taps.
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            for (change in event.changes) {
                                val id = change.id.value
                                when {
                                    change.changedToDownIgnoreConsumed() ->
                                        router.down(id, change.position.x, change.position.y)

                                    change.changedToUpIgnoreConsumed() -> router.up(id)

                                    else -> router.move(id, change.position.x, change.position.y)
                                }
                                change.consume()
                            }
                            tick++
                            currentOnStateChange(router.state())
                        }
                    }
                },
        ) {
            @Suppress("UNUSED_EXPRESSION")
            tick // Read so the canvas repaints when a touch changes.

            val active = router.activeControls()
            withOpacity(padStyle.opacity) {
                for (control in resolved.controls) {
                    // Keyed on the shape rather than the id, because the shape is what dispatches
                    // to the stick renderer -- a layout may give ControlId.Stick a plain circle.
                    val stickTouch = (control.spec.shape as? ControlSpec.Shape.Stick)
                        ?.let { router.stickTouch(control.index) }
                    // Keyed on the shape for the same reason, since the sector arithmetic behind
                    // it is the D-pad shape's dead zone and half-width.
                    val pushed = (control.spec.shape as? ControlSpec.Shape.Dpad)
                        ?.let { router.dpadPush(control.index) }
                    // Not keyed on the shape, unlike those two: a trigger's pull is measured from
                    // where the finger landed and how tall the control is, which every shape has.
                    drawControl(
                        control = control,
                        style = padStyle,
                        pressed = control.index in active,
                        stickTouch = stickTouch,
                        textMeasurer = textMeasurer,
                        heldMembers = router.activeMembers(control.index),
                        pushed = pushed,
                        triggerValue = router.triggerValue(control.index),
                    )
                }
            }
        }
    }
}
