package com.blugaemand.input

import com.blugaemand.hid.GamepadButton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A control the user can touch. Distinct from [GamepadButton] because not every on-screen control
 * maps to a single button — sticks produce axes, the D-pad produces a hat, and the triggers
 * produce both an axis and a button.
 *
 * Every variant names its own discriminator. Left to itself kotlinx would write the fully-qualified
 * Kotlin name, and then moving this file to another package would orphan every layout already saved
 * on a phone; see [LayoutJson].
 */
@Serializable
sealed interface ControlId {

    /** A plain button that maps one-to-one onto an HID button. */
    @Serializable
    @SerialName("button")
    data class Button(val button: GamepadButton) : ControlId

    /** An analog thumbstick. */
    @Serializable
    @SerialName("stick")
    data class Stick(val side: Side) : ControlId

    /** An analog trigger. */
    @Serializable
    @SerialName("trigger")
    data class Trigger(val side: Side) : ControlId

    /**
     * The whole D-pad as one cross, resolving to a hat value from where within it the touch lands.
     *
     * One of two ways to have a D-pad; see [DpadButton]. This one is a single control, so a thumb
     * rolling across it gives diagonals without lifting, which is how a real cross behaves.
     */
    @Serializable
    @SerialName("dpad")
    data object Dpad : ControlId

    /**
     * One arm of a D-pad as a control of its own, so a layout can put the four directions where it
     * likes rather than accepting a cross.
     *
     * Still one hat to the host: [com.blugaemand.hid.Hat.of] folds the held directions into a
     * single value, so two arms make a diagonal and opposing arms cancel, exactly as a cross does.
     * What is given up is the roll — each arm has to be hit on its own.
     */
    @Serializable
    @SerialName("dpad_button")
    data class DpadButton(val direction: Direction) : ControlId

    /**
     * A plate of several controls that behaves as one: what it sends depends on where within itself
     * it is touched, which is what [Dpad] already does for the hat, generalised.
     *
     * This is a **marker only** — the members and their arrangement live on
     * [ControlSpec.Shape.Cluster], because everything in the app that asks how big a control is or
     * where its parts are asks its shape. Putting them here instead would make extent a function of
     * the id, and [Placement], [LayoutEdits] and [ResolvedLayout] all assume it is not.
     */
    @Serializable
    @SerialName("cluster")
    data object Cluster : ControlId

    @Serializable
    enum class Direction { UP, DOWN, LEFT, RIGHT }

    @Serializable
    enum class Side { LEFT, RIGHT }

    companion object {
        /**
         * Every control a layout could hold, which is what the editor's *add control* page offers
         * once the ones already placed are taken out.
         *
         * [GamepadButton.L2] and [GamepadButton.R2] appear as buttons here even though the built-in
         * layouts reach them through [Trigger] instead: they are the digital companions to the
         * analog axes, and a layout is free to place one as a plain button. That is also why
         * [missingButtons] excuses them.
         *
         * [Cluster] is deliberately absent: there is no such thing as *the* cluster, only a
         * particular arrangement of members, so it has no default spec to be added from. It arrives
         * through [ControlGroups] instead.
         */
        val ALL: List<ControlId> = buildList {
            add(Dpad)
            for (direction in Direction.entries) add(DpadButton(direction))
            for (side in Side.entries) add(Stick(side))
            for (side in Side.entries) add(Trigger(side))
            for (button in GamepadButton.entries) add(Button(button))
        }
    }
}

/**
 * Where a control sits on screen and how big it is, in coordinates normalised to the 0..1 range.
 *
 * Normalised rather than absolute so a layout renders identically on any screen size, and so the
 * whole thing serialises cleanly once layouts become user-editable.
 *
 * Positions are fractions of the screen's width and height. Sizes are fractions of
 * [ResolvedLayout.unit] instead, so controls stay round and keep their proportions whatever the
 * aspect ratio.
 */
@Serializable
data class ControlSpec(
    val id: ControlId,
    val shape: Shape,
    /**
     * Drawn in the middle of the control. In [LayoutStyle.Images] mode it shows only where the
     * pack has no glyph for the control and it falls back to its shape.
     */
    val label: String = "",
) {
    @Serializable
    sealed interface Shape {
        /** Centre of the control, both components in 0..1. */
        val centerX: Float
        val centerY: Float

        /** A circular control; [radius] is a fraction of the layout unit. */
        @Serializable
        @SerialName("circle")
        data class Circle(
            override val centerX: Float,
            override val centerY: Float,
            val radius: Float,
        ) : Shape

        /**
         * A rectangular control. [width] is a fraction of screen width so shoulder buttons stretch
         * across the top edge; [height] is a fraction of the layout unit.
         */
        @Serializable
        @SerialName("rect")
        data class Rect(
            override val centerX: Float,
            override val centerY: Float,
            val width: Float,
            val height: Float,
        ) : Shape

        /**
         * A thumbstick: [radius] is the base the finger may roam within, [knobRadius] the moving
         * cap drawn on top. Both are fractions of the layout unit.
         */
        @Serializable
        @SerialName("stick")
        data class Stick(
            override val centerX: Float,
            override val centerY: Float,
            val radius: Float,
            val knobRadius: Float,
        ) : Shape

        /**
         * A square D-pad cross. [radius] is half the width of the cross, as a fraction of the
         * layout unit. The dead zone in the middle is a fraction of [radius].
         */
        @Serializable
        @SerialName("dpad")
        data class Dpad(
            override val centerX: Float,
            override val centerY: Float,
            val radius: Float,
            val deadZone: Float = 0.25f,
        ) : Shape

        /**
         * A plate of [members] that is one control: one entry in the layout, one thing to select,
         * move and resize, resolving a touch to whichever member it landed nearest.
         *
         * **Every number inside a plate is a fraction of [ResolvedLayout.unit]** — the members'
         * offsets from the plate's centre as well as their sizes, including a member
         * [Rect.width]. At top level positions are fractions of screen width and height and a
         * rectangle's width is a fraction of screen width, and both of those exist for reasons that
         * stop at this edge: a layout should spread with the screen, and a shoulder button should
         * stretch the top of it. A plate is a compact object whose parts have to hold their
         * arrangement, which is the argument already made for sizes generally — measured against the
         * unit, a face diamond is the same diamond on 16:9 and on 4:3, where screen fractions would
         * stretch it vertically. It also makes resizing one factor applied to every member number,
         * whatever mixture of shapes they are.
         *
         * Members are plain buttons, triggers and D-pad arms. A [Stick] is excluded because a
         * stick's cap is positioned through `stickOffset`, which is keyed by top-level control; a
         * nested plate and a one-piece [Dpad] are excluded because neither adds anything a flat list
         * of members does not already do.
         */
        @Serializable
        @SerialName("cluster")
        data class Cluster(
            override val centerX: Float,
            override val centerY: Float,
            val members: List<ControlSpec>,
        ) : Shape {
            init {
                // Checked here rather than trusted because this also runs on deserialise, and a
                // hand-edited file is the one place these can be false. An empty plate would throw
                // from the bounding-box arithmetic in ResolvedLayout instead -- during composition,
                // with nothing left to say which layout was at fault.
                require(members.isNotEmpty()) { "a cluster needs at least one member" }
                require(members.all { it.id.canBeClustered() }) {
                    "a cluster holds buttons, triggers and D-pad arms, not " +
                        members.first { !it.id.canBeClustered() }.id
                }
            }
        }
    }
}

/** Whether this control is one a [ControlSpec.Shape.Cluster] may hold; see there for why. */
private fun ControlId.canBeClustered(): Boolean = when (this) {
    is ControlId.Button, is ControlId.Trigger, is ControlId.DpadButton -> true
    is ControlId.Stick, ControlId.Dpad, ControlId.Cluster -> false
}

/**
 * Every control this one drives: itself, or a plate's members.
 *
 * Anything asking what a layout can actually send has to go through this rather than reading
 * [ControlSpec.id], or a placed face plate would look like a pad with no face buttons on it.
 */
fun ControlSpec.leafIds(): List<ControlId> = when (val shape = shape) {
    is ControlSpec.Shape.Cluster -> shape.members.flatMap { it.leafIds() }
    else -> listOf(id)
}
