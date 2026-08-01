package com.blugaemand.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.blugaemand.ui.theme.OverlayColors

/** How long a pill must be held before its panel opens. */
private const val HOLD_TO_OPEN_MS = 600

/** How quickly the bar empties again once the hold ends, however it ended. */
private const val BAR_DRAIN_MS = 180

/**
 * A pill along the top edge that opens its panel on a deliberate hold.
 *
 * Opening requires holding rather than tapping: the pills sit right where a thumb travels during
 * play, and a stray tap that threw up a panel mid-game would be worse than a slightly slower
 * deliberate open. A blue bar sweeps left to right while held so the hold is visibly doing
 * something and its length is obvious. Closing is a plain tap — there is nothing to protect
 * against there.
 *
 * [content] fills the pill; every pill shares this shape, background and gesture so a second one
 * cannot drift from the first.
 */
@Composable
fun HoldPill(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }

    // The bar is driven from here rather than from inside the gesture, because the gesture
    // coroutine does not survive long enough to clean up after itself: triggering flips `expanded`,
    // which re-keys the pointerInput below and cancels the gesture mid-suspension. Anything after
    // the await would never run, which is what previously left the bar stuck full after closing.
    LaunchedEffect(pressed, expanded) {
        if (pressed && !expanded) {
            progress.animateTo(1f, tween(HOLD_TO_OPEN_MS, easing = LinearEasing))
            // Reaching full is the trigger; the buzz confirms it without looking.
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onExpandedChange(true)
        } else {
            progress.animateTo(0f, tween(BAR_DRAIN_MS))
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(OverlayColors.Pill)
            .drawBehind {
                if (progress.value > 0f) {
                    drawRect(
                        color = OverlayColors.Accent,
                        size = Size(size.width * progress.value, size.height),
                    )
                }
            }
            .pointerInput(expanded) {
                try {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)

                        if (expanded) {
                            // Closing needs no ceremony; a plain tap will do.
                            if (waitForUpOrCancellation() != null) onExpandedChange(false)
                            return@awaitEachGesture
                        }

                        pressed = true
                        // Null means cancelled rather than lifted; either way the hold is over.
                        waitForUpOrCancellation()
                        pressed = false
                    }
                } finally {
                    // Triggering cancels this coroutine before the line above can run, so without
                    // this the flag would stay set and the bar would start refilling by itself the
                    // moment the panel closed.
                    pressed = false
                }
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * The card a pill's panel is drawn on. Every panel hangs the same distance below the pill row and
 * shares one surface, so which pill opened it is the only difference the eye has to pick up.
 */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.padding(top = 6.dp),
        colors = CardDefaults.cardColors(containerColor = OverlayColors.Panel),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}
