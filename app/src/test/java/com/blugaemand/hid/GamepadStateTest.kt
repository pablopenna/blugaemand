package com.blugaemand.hid

import org.junit.Assert.assertEquals
import org.junit.Test

class GamepadStateTest {

    @Test
    fun `merging sources combines buttons triggers and independent stick axes`() {
        val touch = GamepadState.NEUTRAL.copy(leftStickX = 0, rightTrigger = 100)
            .withButton(GamepadButton.SOUTH, true)
        val controller = GamepadState.NEUTRAL.copy(leftStickY = 255, rightTrigger = 200)
            .withButton(GamepadButton.START, true)

        val merged = touch.mergeWith(controller)

        assertEquals(0, merged.leftStickX)
        assertEquals(255, merged.leftStickY)
        assertEquals(200, merged.rightTrigger)
        assertEquals(true, merged.isPressed(GamepadButton.SOUTH))
        assertEquals(true, merged.isPressed(GamepadButton.START))
    }

    @Test
    fun `merging dpad directions forms diagonals and cancels opposites`() {
        assertEquals(
            Hat.NORTH_EAST,
            GamepadState.NEUTRAL.copy(hat = Hat.NORTH)
                .mergeWith(GamepadState.NEUTRAL.copy(hat = Hat.EAST)).hat,
        )
        assertEquals(
            Hat.CENTER,
            GamepadState.NEUTRAL.copy(hat = Hat.NORTH)
                .mergeWith(GamepadState.NEUTRAL.copy(hat = Hat.SOUTH)).hat,
        )
    }
}
