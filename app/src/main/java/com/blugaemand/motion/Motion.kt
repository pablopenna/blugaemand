package com.blugaemand.motion

import com.blugaemand.hid.GamepadState
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Turning the phone's own rotation into stick movement — gyro aiming, the thing a pad with no
 * motion axes of its own can still offer.
 *
 * **Not a motion axis on the wire.** The HID descriptor has four stick axes, two triggers and a
 * hat, and adding gyro axes to it would produce axes no host maps to anything: Windows would list
 * two more sliders in `joy.cpl` and no game would read them. What games do read is a thumbstick, so
 * that is where the phone's movement goes — which is also what every gyro-aim setup on a desktop
 * does, and what makes this useful the moment it is switched on.
 *
 * **Rate, not angle.** The deflection is proportional to how fast the phone is turning, not to how
 * far it has been turned from some remembered pose. Integrating angle would need a resting pose to
 * measure against and would drift away from it within a minute; a rate mapping self-centres the
 * instant the phone stops moving, needs no calibration, and is what a stick pushed and released
 * does anyway.
 *
 * Plain Kotlin with no Android in it, so the mapping is JVM-tested rather than judged by waving a
 * phone around. [MotionSensor] is the part that reads the gyroscope.
 */

/** Which stick the phone's movement drives. */
enum class MotionTarget {
    RIGHT_STICK,
    LEFT_STICK,
    ;

    fun describe(): String = if (this == RIGHT_STICK) "right" else "left"

    fun other(): MotionTarget = if (this == RIGHT_STICK) LEFT_STICK else RIGHT_STICK
}

/**
 * How the phone's movement is mapped, as the menu sets it.
 *
 * App-wide rather than part of a layout: this is about the phone and the hand holding it, and
 * carrying it on a layout would mean re-setting it for every pad and shipping it inside every
 * shared file.
 */
data class MotionSettings(
    val enabled: Boolean = false,
    val target: MotionTarget = MotionTarget.RIGHT_STICK,
    /** Multiplier on [MotionAim.FULL_SCALE_RATE]; see [SENSITIVITY_STEPS]. */
    val sensitivity: Float = 1f,
    /** Whether tipping the phone back aims down rather than up, the way flight sticks read. */
    val invertY: Boolean = false,
) {
    /** The next sensitivity the menu offers, wrapping at the top. */
    fun nextSensitivity(): MotionSettings {
        val next = SENSITIVITY_STEPS.firstOrNull { it > sensitivity } ?: SENSITIVITY_STEPS.first()
        return copy(sensitivity = next)
    }

    companion object {
        /**
         * What the menu cycles through. Coarse on purpose — a slider for this would be a slider
         * someone has to aim at with a thumb while the pad underneath is the thing being tuned.
         */
        val SENSITIVITY_STEPS = listOf(0.5f, 0.75f, 1f, 1.5f, 2f, 3f)
    }
}

/**
 * A stick displacement from the phone's movement, in the same -1..1 units
 * [GamepadState.axisFromUnit] takes.
 */
data class MotionAim(val x: Float, val y: Float) {

    companion object {
        val NONE = MotionAim(0f, 0f)

        /**
         * The turn rate that means full deflection at sensitivity 1, in radians per second.
         *
         * About 230°/s — a brisk flick of the wrists rather than anything a hand does while merely
         * holding the phone. Lower would make the pad twitch while being held; much higher would
         * need the phone swung to aim at all.
         */
        const val FULL_SCALE_RATE = 4f

        /**
         * Turning slower than this is treated as not turning, in radians per second.
         *
         * About 1.7°/s, which is hand tremor and gyroscope bias rather than intent. Without it a
         * phone lying on a table still walks the stick off centre, and a stick that never quite
         * reads zero is a stick a game reads as permanently held.
         */
        const val DEAD_ZONE_RATE = 0.03f
    }
}

/**
 * The aim [rateX], [rateY] and [rateZ] amount to — a gyroscope's reading in radians per second
 * about the device's own axes — with the screen turned [screenRotationDegrees] from the phone's
 * natural orientation.
 *
 * The rotation matters because the sensor frame is fixed to the phone's *natural* orientation and
 * the pad is held in landscape: the device axis that runs across the screen is a different one in
 * each of the two landscapes, and reading it in the wrong one puts aiming on the wrong axis and
 * backwards in one of the two.
 *
 * [rateZ] is roll — the phone turning in the plane of its own screen — and is deliberately unused.
 * It aims at nothing: the barrel points the same way throughout.
 */
fun MotionSettings.aimOf(
    rateX: Float,
    rateY: Float,
    rateZ: Float,
    screenRotationDegrees: Int,
): MotionAim {
    if (!enabled) return MotionAim.NONE

    // Where the screen's own right and up directions lie along the device's axes. Both are unit
    // vectors in the x-y plane, which is why roll -- rotation about z -- cannot reach either.
    val (rightX, rightY) = when (((screenRotationDegrees % 360) + 360) % 360) {
        90 -> 0f to -1f
        180 -> -1f to 0f
        270 -> 0f to 1f
        else -> 1f to 0f
    }
    // Up is right turned a quarter turn anticlockwise, which in these axes is (-y, x).
    val upX = -rightY
    val upY = rightX

    // Rotation about the screen's up axis swings the phone's face sideways, which is aiming left
    // and right; rotation about the screen's right axis tips it, which is aiming up and down. Both
    // are negated: turning the phone to the left aims left, and the left rail of a stick is -1.
    val yawRate = -(rateX * upX + rateY * upY)
    val pitchRate = -(rateX * rightX + rateY * rightY)

    val magnitude = hypot(yawRate, pitchRate)
    if (magnitude <= MotionAim.DEAD_ZONE_RATE) return MotionAim.NONE

    // Rescaled from the edge of the dead zone rather than merely thresholded, so movement starts
    // from nothing instead of jumping to whatever the dead zone was worth.
    val gain = (magnitude - MotionAim.DEAD_ZONE_RATE) * sensitivity /
        (magnitude * MotionAim.FULL_SCALE_RATE)
    val x = yawRate * gain
    val y = pitchRate * gain * (if (invertY) -1f else 1f)

    // Clamped by length rather than per axis, so a diagonal swing stays on its diagonal instead of
    // being squared off into the corner.
    val reach = hypot(x, y)
    if (reach <= 1f) return MotionAim(x, y)
    return MotionAim(x / reach, y / reach)
}

/**
 * This state with [aim] added to the stick it drives.
 *
 * Added rather than substituted, so a thumb already on that stick and the phone's movement combine
 * the way they do on a desktop gyro setup: the stick for the big turn, the phone for the last few
 * degrees of it. A stick nobody is touching rests at centre, so on that one the sum is the aim
 * alone.
 */
fun GamepadState.withAim(target: MotionTarget, aim: MotionAim): GamepadState {
    if (aim == MotionAim.NONE) return this
    val dx = (aim.x * (GamepadState.AXIS_CENTER)).roundToInt()
    val dy = (aim.y * (GamepadState.AXIS_CENTER)).roundToInt()
    return if (target == MotionTarget.RIGHT_STICK) {
        copy(
            rightStickX = GamepadState.clampAxis(rightStickX + dx),
            rightStickY = GamepadState.clampAxis(rightStickY + dy),
        )
    } else {
        copy(
            leftStickX = GamepadState.clampAxis(leftStickX + dx),
            leftStickY = GamepadState.clampAxis(leftStickY + dy),
        )
    }
}
