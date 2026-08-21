package com.blugaemand.input

import com.blugaemand.hid.Hat

/**
 * The picture a control draws, idle and held. [pressed] is optional: null means the control simply
 * does not animate, which is the right answer for anything an art pack ships only one glyph for.
 *
 * [idle] is not, which is what makes a pressed-only control unrepresentable rather than something
 * to test for — it would flicker into existence on touch and vanish again.
 */
data class Glyph(val idle: ControlIcon, val pressed: ControlIcon? = null)

/**
 * A set of button art, as the mapping from control to picture that a layout in
 * [LayoutStyle.Images] mode draws with.
 *
 * Keyed by [ControlId] rather than baked into each [ControlSpec] so the pack is stated once and a
 * layout is free to be nothing but geometry. It is also what lets two layouts over the same
 * geometry differ only in their art, and — since ids are unique within a layout — the mapping is
 * still per-control, so a one-off pack is how a hand-made layout will name its own pictures.
 *
 * **A pack does not have to be complete.** Anything it does not name falls back to the drawn shape
 * and label, which is what keeps thumbsticks moving in image mode and what covers a button the
 * pack simply has no picture of; see [com.blugaemand.input.art.PLAYSTATION_ART].
 */
data class ArtPack(
    /**
     * Stable identifier. This is the whole of what a saved layout writes down about its pack, so
     * changing one orphans every layout already using it; see [ArtPackSerializer].
     */
    val id: String,
    /**
     * What the pack is called where someone has to read it, which is the editor's *Appearance* page.
     *
     * Separate from [id] because that is a slug and a compatibility surface: `steamdeck` is the
     * right thing to write in a file and the wrong thing to put in front of a person, and deriving
     * one from the other is a guess that gets *Steam Deck* wrong. Free to change, unlike the id.
     */
    val name: String,
    val glyphs: Map<ControlId, Glyph>,
    /**
     * What a one-piece [ControlId.Dpad] draws while pushed, keyed by the direction it is sending.
     *
     * Separate from [glyphs] because a cross has more than the two states a [Glyph] can hold, and
     * because these pictures belong to *that* control alone. Each one is the whole cross with a
     * single arm lit, which is the only shape Kenney's D-pad art comes in — there is no picture of
     * an arm on its own — so keying them under [ControlId.DpadButton] instead would put a whole
     * cross on every arm of a four-button D-pad and four crosses on a plate that already is one.
     *
     * **Only the four cardinals are here**, because those are the only ones drawn. A diagonal falls
     * back to the pressed picture in [glyphs], which lights the cross whole: honest about being
     * pushed without claiming a direction the art cannot show. So a thumb rolling around the cross
     * alternates between one lit arm and a lit cross, which is the arrangement's own edge showing
     * rather than a state being missed.
     *
     * Empty is a valid answer, and means the cross only knows idle and pressed.
     */
    val dpadArms: Map<Hat, ControlIcon> = emptyMap(),
) {
    /** The picture [control] draws while [held], or null if this pack has none for it. */
    fun glyph(control: ControlId, held: Boolean): ControlIcon? {
        val glyph = glyphs[control] ?: return null
        return glyph.pressed.takeIf { held } ?: glyph.idle
    }

    /**
     * The picture a one-piece [ControlId.Dpad] draws while a thumb on it is sending [direction].
     *
     * [Hat.CENTER] is a thumb in the dead zone: touching the cross but sending nothing, so it draws
     * the resting picture. Lighting anything there would say the host is being told something it is
     * not — which is what the drawn cross's dead-zone ring already avoids in colours mode.
     */
    fun dpadGlyph(direction: Hat): ControlIcon? =
        dpadArms[direction] ?: glyph(ControlId.Dpad, held = direction != Hat.CENTER)
}
