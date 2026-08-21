package com.blugaemand.input.layouts

import com.blugaemand.input.GamepadLayout
import com.blugaemand.input.LayoutStyle
import com.blugaemand.input.art.SWITCH2_ART

/**
 * A Nintendo Switch 2 Pro Controller: [SWITCH_LAYOUT]'s geometry under [SWITCH2_ART].
 *
 * **Derived rather than authored**, which is the opposite call from the one [SWITCH_LAYOUT] itself
 * makes, and for the same reason. That plate is authored in full because a Pro Controller is not an
 * Xbox pad with different letters on it. This one is derived because a Switch 2 Pro Controller *is*
 * a Pro Controller with different letters on it — the sticks, the diamond, the D-pad and the
 * shoulder row are in the same places, and the two things the newer pad adds are a C button and a
 * pair of back paddles that [com.blugaemand.hid.GamepadButton] has no slot for. Restating the
 * fifteen controls to change none of them would only give the two plates somewhere to drift apart.
 *
 * So what actually distinguishes this layout on screen is the trigger art, which is the only thing
 * Kenney redrew; see [SWITCH2_ART] for why that is the whole difference and not a shortcut.
 */
val SWITCH2_LAYOUT: GamepadLayout = SWITCH_LAYOUT.copy(
    id = "switch2",
    name = "Switch 2",
    style = LayoutStyle.Images(SWITCH2_ART),
)
