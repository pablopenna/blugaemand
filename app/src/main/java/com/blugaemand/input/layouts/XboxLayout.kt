package com.blugaemand.input.layouts

import com.blugaemand.input.GamepadLayout
import com.blugaemand.input.LayoutStyle
import com.blugaemand.input.art.XBOX_ART

/**
 * The same pad as [DEFAULT_LAYOUT], drawn with Kenney's Xbox input prompts instead of shapes and
 * letters.
 *
 * Geometry is derived rather than copied. The positions are the hand-tuned part of a layout and
 * tuning them against a real thumb is still on the backlog, so a second copy would drift the first
 * time that happens. What is left is the one thing that actually differs: which pictures to draw.
 */
val XBOX_LAYOUT: GamepadLayout = DEFAULT_LAYOUT.copy(
    id = "xbox",
    name = "Xbox",
    style = LayoutStyle.Images(XBOX_ART),
)
