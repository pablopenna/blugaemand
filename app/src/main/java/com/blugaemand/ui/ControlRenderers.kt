package com.blugaemand.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import com.blugaemand.input.ControlSpec
import com.blugaemand.input.ResolvedControl
import com.blugaemand.ui.theme.PadColors

/**
 * Draws one resolved control. [pressed] drives the highlight, and [stickOffset] positions a
 * thumbstick's cap as a -1..1 displacement.
 */
fun DrawScope.drawControl(
    control: ResolvedControl,
    pressed: Boolean,
    stickOffset: Pair<Float, Float>?,
    textMeasurer: TextMeasurer,
) {
    when (val shape = control.spec.shape) {
        is ControlSpec.Shape.Circle -> drawCircleControl(control, pressed, textMeasurer)
        is ControlSpec.Shape.Rect -> drawRectControl(control, pressed, textMeasurer)
        is ControlSpec.Shape.Stick -> drawStick(control, pressed, stickOffset)
        is ControlSpec.Shape.Dpad -> drawDpad(control, shape, pressed)
    }
}

private fun DrawScope.drawCircleControl(
    control: ResolvedControl,
    pressed: Boolean,
    textMeasurer: TextMeasurer,
) {
    val center = Offset(control.centerX, control.centerY)
    drawCircle(
        color = if (pressed) PadColors.ControlFillPressed else PadColors.ControlFill,
        radius = control.radius,
        center = center,
    )
    drawCircle(
        color = PadColors.ControlStroke,
        radius = control.radius,
        center = center,
        style = Stroke(width = control.radius * 0.08f),
    )
    drawLabel(control.spec.label, center, control.radius, pressed, textMeasurer)
}

private fun DrawScope.drawRectControl(
    control: ResolvedControl,
    pressed: Boolean,
    textMeasurer: TextMeasurer,
) {
    val topLeft = Offset(control.centerX - control.halfWidth, control.centerY - control.halfHeight)
    val size = Size(control.halfWidth * 2f, control.halfHeight * 2f)
    val corner = CornerRadius(control.halfHeight * 0.4f)

    drawRoundRect(
        color = if (pressed) PadColors.ControlFillPressed else PadColors.ControlFill,
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
        pressed,
        textMeasurer,
    )
}

private fun DrawScope.drawStick(
    control: ResolvedControl,
    pressed: Boolean,
    stickOffset: Pair<Float, Float>?,
) {
    val center = Offset(control.centerX, control.centerY)
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
    val (dx, dy) = stickOffset ?: (0f to 0f)
    val knobCenter = Offset(center.x + dx * travel, center.y + dy * travel)

    drawCircle(
        color = if (pressed) PadColors.StickKnobActive else PadColors.StickKnob,
        radius = control.knobRadius,
        center = knobCenter,
    )
}

private fun DrawScope.drawDpad(
    control: ResolvedControl,
    shape: ControlSpec.Shape.Dpad,
    pressed: Boolean,
) {
    val r = control.radius
    val arm = r * 0.42f // half-thickness of the cross arms
    val fill = if (pressed) PadColors.ControlFillPressed else PadColors.ControlFill
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

private fun DrawScope.drawLabel(
    label: String,
    center: Offset,
    reference: Float,
    pressed: Boolean,
    textMeasurer: TextMeasurer,
) {
    if (label.isEmpty()) return
    val style = TextStyle(
        color = if (pressed) PadColors.LabelPressed else PadColors.Label,
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
