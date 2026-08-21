package com.blugaemand.motion

import com.blugaemand.hid.GamepadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gyro-to-stick mapping.
 *
 * Waving a phone around tells you whether aiming feels right; it does not tell you that the phone
 * held the other way up aims the same direction, which is the half of this that is easy to get
 * backwards and impossible to notice on the phone you happen to be holding.
 */
class MotionTest {

    private val on = MotionSettings(enabled = true)

    /** Radians per second that means half deflection at sensitivity 1, dead zone included. */
    private val halfRate = MotionAim.FULL_SCALE_RATE / 2f + MotionAim.DEAD_ZONE_RATE

    // -- Off --------------------------------------------------------------------------------

    @Test
    fun `motion switched off aims nowhere, however fast the phone is turning`() {
        assertEquals(MotionAim.NONE, MotionSettings().aimOf(9f, 9f, 9f, 90))
    }

    @Test
    fun `a phone barely moving is a phone not moving`() {
        // Hand tremor and gyroscope bias, which would otherwise walk the stick off centre and read
        // to a game as a stick permanently held.
        val tremor = MotionAim.DEAD_ZONE_RATE * 0.9f
        // The zone is round rather than square, so two axes at nine tenths of it is over it --
        // which is the point of measuring the turn rather than each of its components.
        assertEquals(MotionAim.NONE, on.aimOf(tremor, 0f, 0f, 90))
        assertEquals(MotionAim.NONE, on.aimOf(0f, tremor, 0f, 90))
    }

    @Test
    fun `deflection starts from nothing at the edge of the dead zone`() {
        val justOver = on.aimOf(0f, -(MotionAim.DEAD_ZONE_RATE * 1.01f), 0f, 90)
        assertTrue("$justOver", justOver.y != 0f)
        assertTrue("$justOver", kotlin.math.abs(justOver.y) < 0.01f)
    }

    // -- Which way is which -----------------------------------------------------------------

    @Test
    fun `turning the phone left aims left, and the left rail is negative`() {
        // Landscape, top of the phone to the left: the screen's up axis is the device's +x, so
        // turning the face leftwards is a positive rate about x.
        val aim = on.aimOf(halfRate, 0f, 0f, 90)
        assertEquals(-0.5f, aim.x, 0.01f)
        assertEquals(0f, aim.y, 0.001f)
    }

    @Test
    fun `tipping the phone back aims up, and up is negative`() {
        // The screen's right axis is the device's -y in this landscape, so tipping the top away
        // from the hands is a negative rate about y.
        val aim = on.aimOf(0f, -halfRate, 0f, 90)
        assertEquals(-0.5f, aim.y, 0.01f)
        assertEquals(0f, aim.x, 0.001f)
    }

    @Test
    fun `the other landscape aims the same way, not the opposite one`() {
        // The whole reason the mapping takes a rotation at all. `sensorLandscape` flips between
        // these two without the activity noticing, and a phone that aims backwards when picked up
        // the other way round is unusable rather than merely wrong.
        val oneWay = on.aimOf(halfRate, halfRate, 0f, 90)
        val otherWay = on.aimOf(-halfRate, -halfRate, 0f, 270)
        assertEquals(oneWay.x, otherWay.x, 0.001f)
        assertEquals(oneWay.y, otherWay.y, 0.001f)
    }

    @Test
    fun `roll aims at nothing`() {
        // Turning the phone in the plane of its own screen points the barrel where it already was.
        assertEquals(MotionAim.NONE, on.aimOf(0f, 0f, 20f, 90))
    }

    @Test
    fun `inverting the vertical flips only the vertical`() {
        val plain = on.aimOf(halfRate, -halfRate, 0f, 90)
        val inverted = on.copy(invertY = true).aimOf(halfRate, -halfRate, 0f, 90)
        assertEquals(plain.x, inverted.x, 0.001f)
        assertEquals(-plain.y, inverted.y, 0.001f)
    }

    // -- How far ----------------------------------------------------------------------------

    @Test
    fun `sensitivity scales the deflection`() {
        val once = on.aimOf(halfRate, 0f, 0f, 90)
        val twice = on.copy(sensitivity = 2f).aimOf(halfRate, 0f, 0f, 90)
        assertEquals(once.x * 2f, twice.x, 0.001f)
    }

    @Test
    fun `a swing past full scale stops at the rail`() {
        val aim = on.aimOf(MotionAim.FULL_SCALE_RATE * 10f, 0f, 0f, 90)
        assertEquals(-1f, aim.x, 0.001f)
    }

    @Test
    fun `a diagonal swing stays on its diagonal rather than being squared off`() {
        // Clamped by length, not per axis. Squaring it off would turn a fast diagonal flick into a
        // corner press, which aims somewhere the phone was never pointed.
        val rate = MotionAim.FULL_SCALE_RATE * 5f
        val aim = on.aimOf(rate, -rate, 0f, 90)
        assertEquals(1f, kotlin.math.hypot(aim.x, aim.y), 0.001f)
        assertEquals(aim.x, aim.y, 0.001f)
    }

    @Test
    fun `the sensitivity menu cycles through the steps and wraps`() {
        var settings = MotionSettings(sensitivity = MotionSettings.SENSITIVITY_STEPS.last())
        settings = settings.nextSensitivity()
        assertEquals(MotionSettings.SENSITIVITY_STEPS.first(), settings.sensitivity)
    }

    // -- Reaching the report ------------------------------------------------------------------

    @Test
    fun `an aim moves the stick it is pointed at and leaves the other alone`() {
        val state = GamepadState.NEUTRAL.withAim(MotionTarget.RIGHT_STICK, MotionAim(0.5f, -0.5f))
        assertEquals(GamepadState.AXIS_CENTER + 64, state.rightStickX)
        assertEquals(GamepadState.AXIS_CENTER - 64, state.rightStickY)
        assertEquals(GamepadState.AXIS_CENTER, state.leftStickX)
        assertEquals(GamepadState.AXIS_CENTER, state.leftStickY)
    }

    @Test
    fun `an aim adds to what a thumb is already sending`() {
        // The point of adding rather than replacing: the stick makes the turn, the phone makes the
        // last few degrees of it.
        val thumb = GamepadState.NEUTRAL.copy(rightStickX = GamepadState.AXIS_CENTER + 40)
        val state = thumb.withAim(MotionTarget.RIGHT_STICK, MotionAim(0.25f, 0f))
        assertEquals(GamepadState.AXIS_CENTER + 72, state.rightStickX)
    }

    @Test
    fun `the sum stops at the rail rather than wrapping round it`() {
        val thumb = GamepadState.NEUTRAL.copy(leftStickX = GamepadState.AXIS_MAX)
        val state = thumb.withAim(MotionTarget.LEFT_STICK, MotionAim(1f, 1f))
        assertEquals(GamepadState.AXIS_MAX, state.leftStickX)
    }

    @Test
    fun `a centred aim leaves the state exactly as it was`() {
        val held = GamepadState.NEUTRAL.copy(rightStickX = 200)
        assertSame(held, held.withAim(MotionTarget.RIGHT_STICK, MotionAim.NONE))
    }
}
