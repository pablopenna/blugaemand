package com.blugaemand.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import com.blugaemand.hid.Hat
import com.blugaemand.input.ControlId
import com.blugaemand.input.ControlSpec
import com.blugaemand.input.ResolvedControl
import com.blugaemand.input.StickTouch
import com.blugaemand.input.isDynamicStick
import com.blugaemand.ui.theme.OverlayColors
import com.blugaemand.ui.theme.PadColors

/**
 * Draws one resolved control. [pressed] drives the highlight, and [stickTouch] says where a
 * thumbstick is centred and how far its cap has been pushed.
 *
 * A control the layout's art pack has a picture for draws that alone — no plate, no label — because
 * the pack's prompts are whole buttons in their own right. Everything else falls back to the drawn
 * shape, which is what keeps thumbsticks working in both modes and what draws a button no pack has
 * a picture of rather than leaving a hole.
 *
 * A cluster draws as its members, each of which is an ordinary control with an ordinary id — which
 * is the whole reason art needed nothing adding for them. [heldMembers] says which of them are
 * down, by ordinal within the plate; a plate itself is never drawn, only what is on it.
 *
 * [pushed] is the one-piece D-pad's extra state: a cross is not merely held or not, it is held in a
 * direction, and an art pack draws each of them. Null everywhere else, and null on a cross nobody is
 * touching — [pressed] still says whether it is held, and the two agree because both come from the
 * same binding.
 *
 * [triggerValue] is the analog trigger's, drawn as a read-out beside the control while a finger is
 * on it. Nothing else on the pad sends a number a thumb can land anywhere within, so nothing else
 * needs telling what it is currently sending.
 */
fun DrawScope.drawControl(
    control: ResolvedControl,
    style: PadStyle,
    pressed: Boolean,
    stickTouch: StickTouch?,
    textMeasurer: TextMeasurer,
    heldMembers: Set<Int> = emptySet(),
    pushed: Hat? = null,
    triggerValue: Int? = null,
) {
    // Asked before the shape is looked at, so a member's own picture is found by its own id. A
    // cluster's id is never in a pack -- ArtPack.glyph is a map lookup, so it simply misses and
    // falls through to the branch below, which is what should happen and not a thing to shortcut.
    //
    // The cross asks a different question, because it has more answers than held-or-not. Keyed on
    // the id rather than on `pushed` being non-null, so a layout that gives some other control a
    // D-pad shape gets its own picture rather than a cross's.
    val glyph = if (pushed != null && control.spec.id is ControlId.Dpad) {
        style.dpadGlyph(pushed)
    } else {
        style.glyph(control.spec.id, pressed)
    }
    if (glyph != null) {
        drawGlyph(control, glyph)
    } else {
        when (val shape = control.spec.shape) {
            is ControlSpec.Shape.Circle -> drawCircleControl(control, style, pressed, textMeasurer)
            is ControlSpec.Shape.Rect -> drawRectControl(control, style, pressed, textMeasurer)
            is ControlSpec.Shape.Stick -> drawStick(control, style, pressed, stickTouch)
            is ControlSpec.Shape.Dpad -> drawDpad(control, style, shape, pressed)
            is ControlSpec.Shape.Cluster -> for (member in control.members) {
                drawControl(
                    control = member,
                    style = style,
                    pressed = member.index in heldMembers,
                    stickTouch = null,
                    textMeasurer = textMeasurer,
                )
            }
        }
    }

    // Last, and outside the branch above, so it sits over whatever the control drew and reads the
    // same whether that was a glyph or a shape.
    if (triggerValue != null) drawTriggerValue(control, triggerValue, textMeasurer)
}

/**
 * Draws what a trigger is currently sending, in a pill just clear of the control.
 *
 * Below it, unless the pill would fall off the bottom of the glass — a trigger against the lower
 * edge gets its read-out above instead. Measured against the screen rather than against which half
 * the control sits in, because what decides this is whether there is room, and a tall enough pad
 * has room below a control well past the midpoint.
 *
 * The pill is sized for `255` however few digits are showing, so it does not twitch wider and
 * narrower as the value crosses 10 and 100 — the number inside is what should be moving.
 */
private fun DrawScope.drawTriggerValue(
    control: ResolvedControl,
    value: Int,
    textMeasurer: TextMeasurer,
) {
    val textStyle = TextStyle(
        color = PadColors.LabelOnDark,
        fontSize = (control.extentY * 0.75f).toSp(),
    )
    val measured = textMeasurer.measure(value.toString(), textStyle)
    val widest = textMeasurer.measure("255", textStyle)

    val padding = widest.size.height * 0.3f
    val pill = Size(widest.size.width + padding * 2f, widest.size.height + padding)
    val gap = control.extentY * 0.35f

    val below = control.centerY + control.extentY + gap
    val top = if (below + pill.height <= size.height) {
        below
    } else {
        control.centerY - control.extentY - gap - pill.height
    }
    val left = control.centerX - pill.width / 2f

    drawRoundRect(
        color = OverlayColors.Pill,
        topLeft = Offset(left, top),
        size = pill,
        cornerRadius = CornerRadius(pill.height / 2f),
    )
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(
            control.centerX - measured.size.width / 2f,
            top + (pill.height - measured.size.height) / 2f,
        ),
    )
}

/**
 * Draws [glyph] centred on the control, square, and sized to the smaller of its two extents so a
 * wide control like a shoulder button gets a glyph of its height rather than a stretched one.
 *
 * The touch area is deliberately left as the resolved shape, which for those wide controls is
 * larger than the picture — a trigger that is easier to hit than it looks is the right way round.
 *
 * **The picture is always rasterised at [GLYPH_RASTER] and scaled into place**, rather than drawn
 * at the size it ends up. A `Painter` is resolved once per icon and shared by every control using
 * it, and a vector one re-renders itself when the size it is asked for changes — a change it
 * applies on the *next* frame. Two controls with the same icon at different sizes therefore each
 * left the other holding a picture rendered for its neighbour, which showed up as a duplicated
 * button flickering at the size of the one being resized. One size for every draw means the cache
 * is never asked to be two things at once, and the canvas does the resizing instead.
 */
private fun DrawScope.drawGlyph(control: ResolvedControl, glyph: Painter) {
    val extent = if (control.radius > 0f) {
        control.radius * 2f
    } else {
        minOf(control.halfWidth, control.halfHeight) * 2f
    }

    translate(left = control.centerX - extent / 2f, top = control.centerY - extent / 2f) {
        scale(extent / GLYPH_RASTER, pivot = Offset.Zero) {
            with(glyph) { draw(Size(GLYPH_RASTER, GLYPH_RASTER)) }
        }
    }
}

/**
 * The pixel square every glyph is rendered into. Comfortably above the size a control is drawn at
 * on a phone, so scaling is almost always down, and small enough that a plate's worth of cached
 * bitmaps stays in the hundreds of kilobytes each.
 */
private const val GLYPH_RASTER = 256f

private fun DrawScope.drawCircleControl(
    control: ResolvedControl,
    style: PadStyle,
    pressed: Boolean,
    textMeasurer: TextMeasurer,
) {
    val center = Offset(control.centerX, control.centerY)
    val fill = if (pressed) style.pressed else style.resting
    drawCircle(
        color = fill,
        radius = control.radius,
        center = center,
    )
    drawCircle(
        color = PadColors.ControlStroke,
        radius = control.radius,
        center = center,
        style = Stroke(width = control.radius * 0.08f),
    )
    drawLabel(control.spec.label, center, control.radius, fill, textMeasurer)
}

private fun DrawScope.drawRectControl(
    control: ResolvedControl,
    style: PadStyle,
    pressed: Boolean,
    textMeasurer: TextMeasurer,
) {
    val topLeft = Offset(control.centerX - control.halfWidth, control.centerY - control.halfHeight)
    val size = Size(control.halfWidth * 2f, control.halfHeight * 2f)
    val corner = CornerRadius(control.halfHeight * 0.4f)
    val fill = if (pressed) style.pressed else style.resting

    drawRoundRect(
        color = fill,
        topLeft = topLeft,
        size = size,
        cornerRadius = corner,
    )
    drawRoundRect(
        color = PadColors.ControlStroke,
        topLeft = topLeft,
        size = size,
        cornerRadius = corner,
        style = Stroke(width = control.halfHeight * 0.1f),
    )
    drawLabel(
        control.spec.label,
        Offset(control.centerX, control.centerY),
        control.halfHeight,
        fill,
        textMeasurer,
    )
}

/**
 * Draws a thumbstick: the well, and the cap displaced within it.
 *
 * Both modes come through here and differ only in where the well is. A fixed stick's is its own
 * centre and is always there; a dynamic one's is wherever the finger currently holding it put it,
 * and there is no well at all when nobody is. The base position is [StickTouch]'s to say and not
 * this function's to work out, which is what keeps the cap on screen and the axes on the wire from
 * ever disagreeing about where centre is.
 */
private fun DrawScope.drawStick(
    control: ResolvedControl,
    style: PadStyle,
    pressed: Boolean,
    stickTouch: StickTouch?,
) {
    // An area is drawn first and always, so that it is visible with nothing on it -- a control
    // with nothing of its own to draw is indistinguishable from a layout that has lost one, on
    // the pad as much as in the editor. Faint, because it is a region rather than a thing to
    // press, and there is nothing to see there until a thumb puts a stick in it.
    if (control.isDynamicStick) {
        drawStickArea(control)
        // The stick itself exists only while a finger is down, and only where that finger landed.
        if (stickTouch == null) return
    }

    val center = Offset(stickTouch?.baseX ?: control.centerX, stickTouch?.baseY ?: control.centerY)
    drawCircle(color = PadColors.StickBase, radius = control.radius, center = center)
    drawCircle(
        color = PadColors.ControlStroke,
        radius = control.radius,
        center = center,
        style = Stroke(width = control.radius * 0.04f),
    )

    // The cap may travel right to the edge of the base, so pull it in by its own radius to keep it
    // visually inside the well.
    val travel = control.radius - control.knobRadius
    val dx = stickTouch?.offsetX ?: 0f
    val dy = stickTouch?.offsetY ?: 0f
    val knobCenter = Offset(center.x + dx * travel, center.y + dy * travel)

    // The shaft the cap sits on: same direction, half the travel and smaller, so it hides entirely
    // under the cap at rest and only leans out from behind it near the edge of the well. Pale
    // against both the cap and the well, so that sliver of a second circle reads as a lit shaft —
    // a stick tilting away from the thumb.
    val shaftTravel = travel * SHAFT_TRAVEL
    drawCircle(
        color = PadColors.StickShaft,
        radius = control.knobRadius * SHAFT_RADIUS,
        center = Offset(center.x + dx * shaftTravel, center.y + dy * shaftTravel),
    )

    // The cap wears the layout's own two colours, the same as every other control: resting until a
    // thumb is on it, pressed while there is.
    drawCircle(
        color = if (pressed) style.pressed else style.resting,
        radius = control.knobRadius,
        center = knobCenter,
    )
}

/** Black or white, whichever the WCAG contrast ratio favours on [fill]. */
private fun labelOn(fill: Color) =
    if (fill.luminance() > LABEL_FLIP) PadColors.LabelOnLight else PadColors.LabelOnDark

private const val LABEL_FLIP = 0.18f

private const val SHAFT_RADIUS = 0.72f
private const val SHAFT_TRAVEL = 0.4f

/**
 * The outline of the rectangle a dynamic stick may be spawned in.
 *
 * Drawn in the control stroke and nothing else — no fill, no label — because it is not a control
 * anyone presses and it sits over whatever a layout has put inside it. What it is for is saying
 * *a stick lives here*: enough to find the region with a thumb, to select it in the editor, and to
 * tell an empty area apart from a layout that has lost a control.
 */
private fun DrawScope.drawStickArea(control: ResolvedControl) {
    val topLeft = Offset(control.centerX - control.halfWidth, control.centerY - control.halfHeight)
    val size = Size(control.halfWidth * 2f, control.halfHeight * 2f)
    val stroke = control.radius * 0.04f

    drawRoundRect(
        color = PadColors.ControlStroke,
        topLeft = topLeft,
        size = size,
        cornerRadius = CornerRadius(control.radius * 0.5f),
        style = Stroke(width = stroke),
    )
}

private fun DrawScope.drawDpad(
    control: ResolvedControl,
    style: PadStyle,
    shape: ControlSpec.Shape.Dpad,
    pressed: Boolean,
) {
    val r = control.radius
    val arm = r * 0.42f // half-thickness of the cross arms
    val fill = if (pressed) style.pressed else style.resting
    val corner = CornerRadius(arm * 0.4f)

    // Two overlapping rounded bars form the cross.
    drawRoundRect(
        color = fill,
        topLeft = Offset(control.centerX - r, control.centerY - arm),
        size = Size(r * 2f, arm * 2f),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = fill,
        topLeft = Offset(control.centerX - arm, control.centerY - r),
        size = Size(arm * 2f, r * 2f),
        cornerRadius = corner,
    )

    // Mark the dead zone so it is obvious where the D-pad stops responding.
    drawCircle(
        color = PadColors.ControlStroke,
        radius = r * shape.deadZone,
        center = Offset(control.centerX, control.centerY),
        style = Stroke(width = r * 0.03f),
    )
}

/**
 * Draws a control's label, in whichever of black or white reads better on [fill].
 *
 * A layout picks its own two colours from the palette, so no single label colour can stay legible
 * across them — pale grey on a pale fill is what this avoids. The pick is the WCAG contrast
 * comparison between white and black on that fill, which crosses over at a relative luminance of
 * about 0.18.
 */
private fun DrawScope.drawLabel(
    label: String,
    center: Offset,
    reference: Float,
    fill: Color,
    textMeasurer: TextMeasurer,
) {
    if (label.isEmpty()) return
    val style = TextStyle(
        color = labelOn(fill),
        fontSize = (reference * 0.7f).toSp(),
    )
    val measured = textMeasurer.measure(label, style)
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(
            center.x - measured.size.width / 2f,
            center.y - measured.size.height / 2f,
        ),
    )
}
