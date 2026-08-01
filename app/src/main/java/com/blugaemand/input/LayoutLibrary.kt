package com.blugaemand.input

import com.blugaemand.input.layouts.Layouts
import java.util.UUID

/**
 * Every layout the app can offer: the built-ins, plus whatever the user has made.
 *
 * **Whether a layout can be edited is a fact about where it came from, not about the layout**, so
 * [GamepadLayout] carries no flag saying so. A built-in is a `val` in the source; a user layout is a
 * row in [user]. [isEditable] is the one place that distinction is drawn, which is what keeps the
 * built-ins read-only without every screen having to remember it.
 *
 * Android-free like the rest of `input`, so the whole thing is exercised in plain JVM tests;
 * [com.blugaemand.data.LayoutStore] is the part that touches a disk.
 */
data class LayoutLibrary(val user: List<GamepadLayout> = emptyList()) {

    /** The catalog the menu lists, built-ins first so they keep their familiar order. */
    val all: List<GamepadLayout> get() = Layouts.ALL + user

    fun byId(id: String): GamepadLayout? = all.firstOrNull { it.id == id }

    fun isEditable(id: String): Boolean = user.any { it.id == id }

    /**
     * Adds [layout], or replaces the one already under its id.
     *
     * One method rather than separate add and update calls because the editor cannot tell which it
     * is doing — it saves on every drag, and the first of those is an add.
     */
    fun with(layout: GamepadLayout): LayoutLibrary {
        val index = user.indexOfFirst { it.id == layout.id }
        return if (index < 0) copy(user = user + layout)
        else copy(user = user.toMutableList().also { it[index] = layout })
    }

    /** Drops a user layout. Built-ins are not in [user], so this cannot remove one. */
    fun without(id: String): LayoutLibrary = copy(user = user.filterNot { it.id == id })

    /**
     * [base], or `base 2`, `base 3`… — the first that no layout is already called.
     *
     * Names are the only handle the menu gives you on a layout, so two rows reading *Default copy*
     * would be two rows you cannot tell apart. Ids stay unique regardless; this is about the list
     * being usable.
     */
    fun uniqueName(base: String): String {
        val taken = all.mapTo(mutableSetOf()) { it.name }
        if (base !in taken) return base
        return generateSequence(2) { it + 1 }.map { "$base $it" }.first { it !in taken }
    }
}

/**
 * A user-owned copy of this layout under [name].
 *
 * The id is a fresh UUID rather than anything derived from the source, so a layout imported from
 * someone else can never land on top of one already here. Everything else comes across untouched,
 * including an [LayoutStyle.Images] pack — a copy of the PS5 pad is a PlayStation-looking pad you
 * can then move things around on.
 */
fun GamepadLayout.copyAsUser(name: String): GamepadLayout =
    copy(id = UUID.randomUUID().toString(), name = name)

/**
 * A layout with nothing on it, for building up from scratch.
 *
 * Starts in colours mode: an empty pad has no art to speak of, and picking a pack is a decision
 * better made once there are controls to see it on.
 */
fun emptyUserLayout(name: String): GamepadLayout = GamepadLayout(
    id = UUID.randomUUID().toString(),
    name = name,
    controls = emptyList(),
    style = LayoutStyle.Colors(),
)
