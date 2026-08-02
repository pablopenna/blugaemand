package com.blugaemand.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blugaemand.input.asHexColor
import com.blugaemand.ui.theme.OverlayColors

/**
 * A colour picker: a saturation/value square over the chosen hue, and a hue bar under it.
 *
 * This replaces the twelve presets the editor shipped with first. Presets were defensible while all
 * they had to do was tint a pad — but the two colours *are* a layout's identity in shapes mode, and
 * twelve of them meant everyone's pads were the same twelve pads. What lands in the layout is a
 * plain ARGB [Int] either way, so nothing outside this file knows the difference.
 *
 * The square and the bar are drawn rather than assembled out of Material parts, because each is a
 * gradient with a marker on it and neither is a slider: a slider has a track, a thumb and a notion
 * of steps, none of which apply, and three of them stacked would not read as a colour picker so much
 * as three number editors.
 *
 * The alpha byte is carried through untouched. A translucent control is a real thing to want and a
 * separate one — see the opacity item on the backlog — and doing it here would mean the colour
 * picker quietly deciding how see-through someone's pad is.
 */
@Composable
fun ColorPicker(
    color: Int,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hue is the one part of a colour that a round trip through ARGB cannot be trusted to give back:
    // black and every grey have none of it, so a colour dragged to the bottom of the square reads
    // back as hue zero. Held here, and used whenever the colour on screen has no hue of its own,
    // this is what stops dragging the brightness down turning a blue pad red on the way back up.
    var stickyHue by remember { mutableFloatStateOf(color.toHsv().hue) }

    val hsv = color.toHsv().let {
        if (it.saturation == 0f || it.value == 0f) it.copy(hue = stickyHue) else it
    }

    fun emit(next: Hsv) {
        stickyHue = next.hue
        onColorChange(next.toArgb(color.alphaByte()))
    }

    // Read inside the gestures below rather than captured by them: a pick recomposes this, and a
    // gesture holding the values from the composition it started in would go on writing the colour
    // it began with. Same reason `EditorScreen` reads its geometry through one of these.
    val currentOnSaturationValue by rememberUpdatedState<(Float, Float) -> Unit> { x, y ->
        emit(hsv.copy(saturation = x, value = 1f - y))
    }
    val currentOnHue by rememberUpdatedState<(Float) -> Unit> { x ->
        emit(hsv.copy(hue = x * 360f))
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(6.dp))
                .pointerInput(Unit) {
                    trackFraction { x, y -> currentOnSaturationValue(x, y) }
                },
        ) {
            // White to the chosen hue across, and that to black down. Two overlays in this order
            // rather than one combined brush, because the second has to darken the first.
            drawRect(Color(Hsv(hsv.hue, 1f, 1f).toArgb(OPAQUE)))
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

            drawCircle(
                color = Color.White,
                radius = MARKER_RADIUS.toPx(),
                center = Offset(hsv.saturation * size.width, (1f - hsv.value) * size.height),
                style = Stroke(width = MARKER_STROKE.toPx()),
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .pointerInput(Unit) {
                    trackFraction { x, _ -> currentOnHue(x) }
                },
        ) {
            drawRect(Brush.horizontalGradient(HUE_STOPS))

            val x = (hsv.hue / 360f) * size.width
            drawLine(
                color = Color.White,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = MARKER_STROKE.toPx(),
            )
        }

        // The same string the saved file holds, which is the one worth showing: it is what someone
        // copying a colour from one layout to another by hand would be looking for.
        Text(text = color.asHexColor(), fontSize = 11.sp, color = OverlayColors.Caption)
    }
}

/**
 * One of the two colours a layout in shapes mode has, as a row that both shows it and picks which
 * one the picker above is aimed at.
 *
 * Two rows and one picker rather than a picker each: a 240 dp panel on a landscape phone has room
 * for one square, and which of the two is being adjusted is a far smaller thing to say than a second
 * copy of the whole control.
 */
@Composable
fun ColorTargetRow(
    label: String,
    color: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(1.dp, OverlayColors.Caption, CircleShape),
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = OverlayColors.Label,
            modifier = Modifier.weight(1f),
        )
        if (selected) Text("✓", fontSize = 12.sp, color = OverlayColors.Accent)
    }
}

/**
 * Reports where a finger is as a fraction of this surface, for the whole of a gesture rather than
 * only where it lands.
 *
 * Dragging is the point — a picker that only takes taps is one you cannot creep up on a colour
 * with — so this follows the pointer down, tracks it, and clamps to the edges, which is what lets a
 * finger that strays off the square still mean the nearest colour rather than nothing at all.
 *
 * A gesture rather than a `Modifier` factory of its own: a composable one would have to be built
 * with `Modifier.composed` to satisfy lint, and there is nothing here that needs composition — the
 * caller reads its live values through [rememberUpdatedState] and hands this two plain lambdas.
 */
private suspend fun PointerInputScope.trackFraction(onPick: (x: Float, y: Float) -> Unit) {
    awaitEachGesture {
        fun report(at: Offset) {
            if (size.width == 0 || size.height == 0) return
            onPick(
                (at.x / size.width).coerceIn(0f, 1f),
                (at.y / size.height).coerceIn(0f, 1f),
            )
        }

        val down = awaitFirstDown()
        report(down.position)
        down.consume()

        do {
            val event = awaitPointerEvent()
            event.changes.lastOrNull()?.let { report(it.position) }
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

/** The wheel, as the six corners it turns on plus the red it comes back round to. */
private val HUE_STOPS: List<Color> = listOf(0, 60, 120, 180, 240, 300, 360)
    .map { Color(Hsv(it.toFloat(), 1f, 1f).toArgb(OPAQUE)) }

private const val OPAQUE = 0xFF

private val MARKER_RADIUS = 6.dp
private val MARKER_STROKE = 2.dp
