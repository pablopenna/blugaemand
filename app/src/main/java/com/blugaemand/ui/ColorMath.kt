package com.blugaemand.ui

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Converting between the ARGB [Int]s a layout stores and the hue/saturation/value a person picks
 * with, which is the whole of what a colour picker is underneath the two rectangles it draws.
 *
 * Plain arithmetic with no Compose and no Android in it, so it is tested on the JVM like the rest of
 * the editor's arithmetic rather than by looking at a phone and deciding the blue looks right. The
 * picker that draws it is in [ColorPicker].
 */

/**
 * A colour as a person adjusts one: [hue] in degrees around the wheel, [saturation] and [value] as
 * fractions.
 *
 * **Not a faithful second copy of an ARGB colour** — greys and black have no hue at all, and the
 * whole of the black edge of a picker is one colour. Converting to this and back is lossy in exactly
 * those places, which is why [ColorPicker] holds a hue of its own rather than reading one back out
 * of the layout on every frame.
 */
data class Hsv(val hue: Float, val saturation: Float, val value: Float)

/** This ARGB colour's hue, saturation and value. The alpha byte is not part of any of the three. */
fun Int.toHsv(): Hsv {
    val r = ((this shr 16) and 0xFF) / 255f
    val g = ((this shr 8) and 0xFF) / 255f
    val b = (this and 0xFF) / 255f

    val max = maxOf(r, g, b)
    val chroma = max - minOf(r, g, b)
    val hue = when {
        // No chroma means no hue: every grey from black to white lands here, and zero is as good an
        // answer as any other. Nothing downstream should be relying on it; see the note on [Hsv].
        chroma == 0f -> 0f
        max == r -> 60f * (((g - b) / chroma) % 6f)
        max == g -> 60f * ((b - r) / chroma + 2f)
        else -> 60f * ((r - g) / chroma + 4f)
    }

    return Hsv(
        hue = (hue + 360f) % 360f,
        saturation = if (max == 0f) 0f else chroma / max,
        value = max,
    )
}

/** This colour as ARGB, carrying [alpha] through unchanged. Out-of-range components are clamped. */
fun Hsv.toArgb(alpha: Int): Int {
    val h = ((hue % 360f) + 360f) % 360f
    val chroma = value.coerceIn(0f, 1f) * saturation.coerceIn(0f, 1f)
    // Rises and falls across each 60 degree sector, which is what makes the six of them meet.
    val ramp = chroma * (1f - abs((h / 60f) % 2f - 1f))
    val base = value.coerceIn(0f, 1f) - chroma

    val (r, g, b) = when ((h / 60f).toInt()) {
        0 -> Triple(chroma, ramp, 0f)
        1 -> Triple(ramp, chroma, 0f)
        2 -> Triple(0f, chroma, ramp)
        3 -> Triple(0f, ramp, chroma)
        4 -> Triple(ramp, 0f, chroma)
        else -> Triple(chroma, 0f, ramp)
    }

    return ((alpha and 0xFF) shl 24) or
        (byteOf(r + base) shl 16) or
        (byteOf(g + base) shl 8) or
        byteOf(b + base)
}

/** This colour's alpha byte, which the picker carries through rather than offering to change. */
fun Int.alphaByte(): Int = (this ushr 24) and 0xFF

private fun byteOf(fraction: Float): Int = (fraction * 255f).roundToInt().coerceIn(0, 255)
