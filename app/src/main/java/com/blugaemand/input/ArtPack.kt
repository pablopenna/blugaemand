package com.blugaemand.input

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
    /** Stable identifier, for tests and for whatever serialises a layout's choice of pack. */
    val id: String,
    val glyphs: Map<ControlId, Glyph>,
) {
    /** The picture [control] draws while [held], or null if this pack has none for it. */
    fun glyph(control: ControlId, held: Boolean): ControlIcon? {
        val glyph = glyphs[control] ?: return null
        return glyph.pressed.takeIf { held } ?: glyph.idle
    }
}
