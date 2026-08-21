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
 * How an analog trigger turns a touch into the value it sends.
 *
 * A setting on the control rather than on the layout, because the two triggers on a pad are not
 * obliged to agree: a racing game wants a progressive accelerator and a digital handbrake, and that
 * is one pad.
 *
 * Not part of [ControlId.Trigger]. An id is what a control *is*, and it is compared as one all over
 * the app — `withControlAdded` counts copies by it, `missingButtons` subtracts by it, the editor's
 * add page lists by it. A mode carried there would make a binary ZR and a progressive ZR two
 * different controls to every one of those.
 */
@Serializable
enum class TriggerMode {

    /**
     * Full pull the moment it is touched: [com.blugaemand.hid.GamepadState.AXIS_MAX] while a finger
     * is on it, released when it lifts.
     *
     * **The default**: most games only ask whether the trigger is down, and for those a value that
     * has to be aimed is worse than a tap. The slide is opted into per trigger.
     */
    BINARY,

    /** The value follows how far the finger has slid; see `TouchRouter.pullAt` for the rules. */
    PROGRESSIVE,
}

/**
 * How a thumbstick decides where its centre is.
 *
 * A setting on the control for the same reasons [TriggerMode] is: the two sticks on a pad are not
 * obliged to agree — a dynamic left stick to walk with and a fixed right stick to aim with is one
 * pad — and an id is compared as identity all over the app, so a mode carried on
 * [ControlId.Stick] would make a fixed left stick and a dynamic left stick two different controls
 * to `withControlAdded`, `missingButtons` and the editor's add page alike.
 */
@Serializable
enum class StickMode {

    /** The stick is where it is drawn, and a thumb has to find it. What a stick has always been. */
    FIXED,

    /**
     * The control is a bare area, and the stick appears wherever inside it a thumb lands, reading
     * centre; the finger drags it off centre from there and lifting takes it away again.
     *
     * The answer to the thing a fixed stick is bad at — a thumb that has to find the stick before
     * it can push it, in the dark, mid-game. See `TouchRouter` for how one behaves once it exists:
     * it stays where it spawned, the area governs spawning and nothing else, and one area carries
     * one stick at a time.
     */
    DYNAMIC,
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
    /**
     * Only meaningful for a [ControlId.Trigger], and ignored by every other control — like [label],
     * which is likewise carried by everything and shown only by some.
     *
     * Defaulted rather than required so a hand-written layout can leave it out and no file needs
     * migrating to gain it. Every built-in takes the default, so it is set here and not restated
     * eight times.
     */
    val triggerMode: TriggerMode = TriggerMode.BINARY,
    /**
     * Only meaningful for a [ControlId.Stick] drawn as a [Shape.Stick], and ignored by everything
     * else — carried by every control the way [label] and [triggerMode] are, and shown by some.
     *
     * Defaulted for the same reason [triggerMode] is: a layout saved before the setting existed
     * reads back as the fixed stick it was saved as, and a newer file read by an older build loses
     * the key and does the same. No format version bump either way.
     */
    val stickMode: StickMode = StickMode.FIXED,
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
         *
         * [areaWidth] and [areaHeight] are the rectangle a [StickMode.DYNAMIC] stick may be
         * spawned in, and are ignored by a fixed one. They are measured the way
         * [Rect.width] and [Rect.height] are — width against the screen, height against the unit —
         * because that is what they are: a box on the glass, and one that should spread with a
         * wider screen rather than stay the same slice of it.
         *
         * Two sizes on one shape rather than two shapes, because the throw and the area are
         * genuinely independent — the point of a big area is to spawn anywhere in it, not to need
         * a bigger sweep once you have — and because keeping both on the stick is what lets the
         * mode switch back and forth without losing either.
         */
        @Serializable
        @SerialName("stick")
        data class Stick(
            override val centerX: Float,
            override val centerY: Float,
            val radius: Float,
            val knobRadius: Float,
            val areaWidth: Float = DEFAULT_AREA_WIDTH,
            val areaHeight: Float = DEFAULT_AREA_HEIGHT,
        ) : Shape {
            companion object {
                /**
                 * The area a stick switched to [StickMode.DYNAMIC] arrives with, if it has never
                 * carried one: a fifth of the screen across and a third of it down on a 16:9,
                 * which is about as much glass as a thumb sweeps without the hand moving.
                 */
                const val DEFAULT_AREA_WIDTH = 0.18f
                const val DEFAULT_AREA_HEIGHT = 0.30f
            }
        }

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
         * stick's cap is positioned through `stickTouch`, which is keyed by top-level control; a
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

/**
 * Whether this control is an area a stick is spawned in rather than a stick drawn where it is.
 *
 * All three conditions, and not [ControlSpec.stickMode] alone: the mode is carried by every
 * control, so it says something only where there is both a stick to spawn and a stick's geometry
 * to spawn it with. A [ControlId.Stick] given a plain circle stays the circle it was drawn as, the
 * same way `GamepadScreen` dispatches its rendering on the shape.
 *
 * Asked wherever an area behaves unlike a control — [ResolvedControl.contains],
 * [ResolvedLayout.hitTest], the router's binding and the renderer — so the definition is in one
 * place and they cannot drift into disagreeing about which controls are areas.
 */
fun ControlSpec.isDynamicStick(): Boolean =
    stickMode == StickMode.DYNAMIC && id is ControlId.Stick && shape is ControlSpec.Shape.Stick

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
