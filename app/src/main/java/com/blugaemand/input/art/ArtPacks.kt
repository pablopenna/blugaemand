package com.blugaemand.input.art

import com.blugaemand.input.ArtPack

/** The catalog of built-in art packs. */
object ArtPacks {

    /**
     * Every pack a layout can name. Adding one means adding a file beside this one and a line
     * here — the same shape as [com.blugaemand.input.layouts.Layouts.ALL].
     *
     * This is what a saved layout's pack id resolves against, so a pack that is not in this list is
     * a pack no layout can be loaded with.
     */
    val ALL: List<ArtPack> = listOf(
        XBOX_ART,
        PLAYSTATION_ART,
        SWITCH_ART,
        SWITCH2_ART,
        STEAM_DECK_ART,
        GAMECUBE_ART,
        WII_U_ART,
    )

    fun byId(id: String): ArtPack? = ALL.firstOrNull { it.id == id }
}
