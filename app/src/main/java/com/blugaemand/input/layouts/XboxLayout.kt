package com.blugaemand.input.layouts

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.ControlIcon
import com.blugaemand.input.ControlId
import com.blugaemand.input.ControlId.Side
import com.blugaemand.input.GamepadLayout
import com.blugaemand.input.LayoutStyle

/**
 * The glyph each control draws, idle and held. The pack pairs an outline with a solid counterpart,
 * so a press reads as the button filling in; the face buttons additionally take on their real Xbox
 * colours.
 *
 * **The face buttons follow the label, not the slot.** [DEFAULT_LAYOUT] puts its Y key on
 * [GamepadButton.WEST], so that is the control that has to show the *Y* glyph. Guarded by
 * `LayoutArtTest`.
 *
 * Sticks are absent deliberately: the pack has only static pictures of a stick, and nothing in it
 * can show a knob displaced from centre, so they stay drawn in both modes.
 *
 * Declared above [XBOX_LAYOUT] because top-level properties initialise in declaration order, and
 * the layout reads this table while building itself.
 */
private val XBOX_ICONS: Map<ControlId, Pair<ControlIcon, ControlIcon?>> = mapOf(
    // Face buttons — crossed, see above.
    ControlId.Button(GamepadButton.WEST) to (ControlIcon.XBOX_Y to ControlIcon.XBOX_Y_PRESSED),
    ControlId.Button(GamepadButton.NORTH) to (ControlIcon.XBOX_X to ControlIcon.XBOX_X_PRESSED),
    ControlId.Button(GamepadButton.SOUTH) to (ControlIcon.XBOX_A to ControlIcon.XBOX_A_PRESSED),
    ControlId.Button(GamepadButton.EAST) to (ControlIcon.XBOX_B to ControlIcon.XBOX_B_PRESSED),

    // Shoulders and triggers.
    ControlId.Trigger(Side.LEFT) to (ControlIcon.XBOX_LT to ControlIcon.XBOX_LT_PRESSED),
    ControlId.Button(GamepadButton.L1) to (ControlIcon.XBOX_LB to ControlIcon.XBOX_LB_PRESSED),
    ControlId.Button(GamepadButton.R1) to (ControlIcon.XBOX_RB to ControlIcon.XBOX_RB_PRESSED),
    ControlId.Trigger(Side.RIGHT) to (ControlIcon.XBOX_RT to ControlIcon.XBOX_RT_PRESSED),

    // Centre cluster. Back is the modern View button and Start the Menu button; the pack names
    // them the way the hardware does, while the HID buttons keep their older names.
    ControlId.Button(GamepadButton.BACK) to (ControlIcon.XBOX_VIEW to ControlIcon.XBOX_VIEW_PRESSED),
    ControlId.Button(GamepadButton.GUIDE) to
        (ControlIcon.XBOX_GUIDE to ControlIcon.XBOX_GUIDE_PRESSED),
    ControlId.Button(GamepadButton.START) to
        (ControlIcon.XBOX_MENU to ControlIcon.XBOX_MENU_PRESSED),

    // Stick clicks.
    ControlId.Button(GamepadButton.L3) to (ControlIcon.XBOX_LS to ControlIcon.XBOX_LS_PRESSED),
    ControlId.Button(GamepadButton.R3) to (ControlIcon.XBOX_RS to ControlIcon.XBOX_RS_PRESSED),

    // The whole cross lights up on a press rather than the arm being pushed: the pack has
    // directional glyphs, but the renderer is told only whether the D-pad is held, not which way.
    ControlId.Dpad to (ControlIcon.XBOX_DPAD to ControlIcon.XBOX_DPAD_PRESSED),
)

/**
 * The same pad as [DEFAULT_LAYOUT], drawn with Kenney's Xbox input prompts instead of shapes and
 * letters.
 *
 * Geometry is derived rather than copied. The positions are the hand-tuned part of a layout and
 * tuning them against a real thumb is still on the backlog, so a second copy would drift the first
 * time that happens.
 */
val XBOX_LAYOUT: GamepadLayout = DEFAULT_LAYOUT.copy(
    id = "xbox",
    name = "Xbox",
    style = LayoutStyle.Images,
    controls = DEFAULT_LAYOUT.controls.map { spec ->
        val glyphs = XBOX_ICONS[spec.id] ?: return@map spec
        spec.copy(icon = glyphs.first, iconPressed = glyphs.second)
    },
)
