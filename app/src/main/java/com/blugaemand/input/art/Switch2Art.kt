package com.blugaemand.input.art

import com.blugaemand.input.ArtPack
import com.blugaemand.input.ControlIcon
import com.blugaemand.input.ControlId
import com.blugaemand.input.ControlId.Side
import com.blugaemand.input.Glyph

/**
 * Kenney's Nintendo Switch 2 prompts — which are [SWITCH_ART]'s with the two triggers redrawn.
 *
 * **This pack is stated as a difference on purpose.** Of the 112 pictures Kenney ships across the
 * two Switch folders, 105 are byte-identical files; the Switch 2 redraws only ZL and ZR, and adds
 * C, GL and GR. So the honest way to say what this pad is, is *the Switch pack, with these two
 * changed* — copying the other thirteen entries would be thirteen chances for the two packs to
 * drift apart in a way no one intended, and the face-button crossing [SWITCH_ART] documents at
 * length would be restated with nothing new to say about it.
 *
 * The same argument as [PLAYSTATION_ART] and its `PS5_` glyphs, pushed one step further: there the
 * shared art was named apart and the two packs were written out separately, because a DualSense and
 * a DualShock differ in more than a picture. These two do not.
 *
 * **C, GL and GR are not here**, and not because they were forgotten: [com.blugaemand.hid.GamepadButton]
 * has no slot for them. Kenney draws all three, so the day the profile grows a button they are a
 * conversion run away.
 */
val SWITCH2_ART: ArtPack = ArtPack(
    id = "switch2",
    name = "Switch 2",
    glyphs = SWITCH_ART.glyphs + mapOf(
        ControlId.Trigger(Side.LEFT) to
            Glyph(ControlIcon.SWITCH2_ZL, ControlIcon.SWITCH2_ZL_PRESSED),
        ControlId.Trigger(Side.RIGHT) to
            Glyph(ControlIcon.SWITCH2_ZR, ControlIcon.SWITCH2_ZR_PRESSED),
    ),
    // Inherited for the same reason as the rest, and stated rather than defaulted: `dpadArms` is a
    // separate field with an empty default, so a pack that derives its glyphs and forgets this one
    // loses its lit arms silently and looks merely unfinished. `LayoutArtTest` also holds the line.
    dpadArms = SWITCH_ART.dpadArms,
)
