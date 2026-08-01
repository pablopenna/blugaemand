package com.blugaemand.hid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The encoder is what the host actually parses, so these assertions are written against exact byte
 * positions and values rather than round-tripping through the encoder's own helpers.
 */
class GenericHidProfileTest {

    private fun encode(state: GamepadState) = GenericHidProfile.encode(state)

    /** Reads a report byte as the unsigned value the host sees. */
    private fun ByteArray.u(index: Int): Int = this[index].toInt() and 0xFF

    @Test
    fun `report is nine bytes`() {
        assertEquals(GenericHidProfile.REPORT_SIZE, encode(GamepadState.NEUTRAL).size)
    }

    @Test
    fun `neutral state centres the sticks and releases everything else`() {
        val report = encode(GamepadState.NEUTRAL)
        assertEquals(0x80, report.u(0)) // left stick X
        assertEquals(0x80, report.u(1)) // left stick Y
        assertEquals(0x80, report.u(2)) // right stick X
        assertEquals(0x80, report.u(3)) // right stick Y
        assertEquals(0x00, report.u(4)) // left trigger
        assertEquals(0x00, report.u(5)) // right trigger
        assertEquals(0x08, report.u(6)) // hat: above the logical max, i.e. centred
        assertEquals(0x00, report.u(7)) // buttons 1-8
        assertEquals(0x00, report.u(8)) // buttons 9-16
    }

    @Test
    fun `each button sets exactly its own bit`() {
        // L2 and R2 are excluded: they mirror the analog triggers and are covered separately.
        val standalone = GamepadButton.entries - setOf(GamepadButton.L2, GamepadButton.R2)

        for (button in standalone) {
            val report = encode(GamepadState.NEUTRAL.withButton(button, true))
            val mask = report.u(7) or (report.u(8) shl 8)
            assertEquals(
                "$button should set only HID button ${button.hidButtonNumber}",
                1 shl (button.hidButtonNumber - 1),
                mask,
            )
        }
    }

    @Test
    fun `button numbering follows the hid-input gamepad order`() {
        // Locks in the ordering Linux and Android rely on to label buttons without extra config.
        assertEquals(1, GamepadButton.A.hidButtonNumber)
        assertEquals(2, GamepadButton.B.hidButtonNumber)
        assertEquals(4, GamepadButton.Y.hidButtonNumber)
        assertEquals(5, GamepadButton.X.hidButtonNumber)
        assertEquals(7, GamepadButton.L1.hidButtonNumber)
        assertEquals(8, GamepadButton.R1.hidButtonNumber)
        assertEquals(11, GamepadButton.BACK.hidButtonNumber)
        assertEquals(12, GamepadButton.START.hidButtonNumber)
        assertEquals(13, GamepadButton.GUIDE.hidButtonNumber)
        assertEquals(14, GamepadButton.L3.hidButtonNumber)
        assertEquals(15, GamepadButton.R3.hidButtonNumber)
    }

    @Test
    fun `several buttons combine into one mask`() {
        val state = GamepadState.NEUTRAL
            .withButton(GamepadButton.A, true)
            .withButton(GamepadButton.START, true)
        val report = encode(state)
        val mask = report.u(7) or (report.u(8) shl 8)
        assertEquals(GamepadButton.A.bit or GamepadButton.START.bit, mask)
    }

    @Test
    fun `every hat direction lands in the low nibble`() {
        for (hat in Hat.entries) {
            val report = encode(GamepadState.NEUTRAL.copy(hat = hat))
            assertEquals(hat.value, report.u(6))
            assertEquals("hat must not spill into the padding nibble", 0, report.u(6) and 0xF0)
        }
    }

    @Test
    fun `hat resolves opposing presses to centre`() {
        assertEquals(Hat.CENTER, Hat.of(up = true, down = true, left = false, right = false))
        assertEquals(Hat.CENTER, Hat.of(up = false, down = false, left = true, right = true))
        assertEquals(Hat.CENTER, Hat.of(up = false, down = false, left = false, right = false))
        assertEquals(Hat.NORTH, Hat.of(up = true, down = false, left = false, right = false))
        assertEquals(Hat.SOUTH_EAST, Hat.of(up = false, down = true, left = false, right = true))
        assertEquals(Hat.NORTH_WEST, Hat.of(up = true, down = false, left = true, right = false))
    }

    @Test
    fun `analog triggers also assert their digital buttons`() {
        val pulled = encode(GamepadState.NEUTRAL.copy(leftTrigger = 255, rightTrigger = 255))
        val mask = pulled.u(7) or (pulled.u(8) shl 8)
        assertEquals(255, pulled.u(4))
        assertEquals(255, pulled.u(5))
        assertTrue("L2 button should follow the left trigger", mask and GamepadButton.L2.bit != 0)
        assertTrue("R2 button should follow the right trigger", mask and GamepadButton.R2.bit != 0)
    }

    @Test
    fun `a barely-touched trigger does not assert its digital button`() {
        val report = encode(GamepadState.NEUTRAL.copy(leftTrigger = 1))
        val mask = report.u(7) or (report.u(8) shl 8)
        assertEquals(1, report.u(4))
        assertEquals(0, mask)
    }

    @Test
    fun `axes are clamped into the declared range`() {
        val report = encode(
            GamepadState.NEUTRAL.copy(
                leftStickX = -500,
                leftStickY = 9000,
                leftTrigger = -1,
                rightTrigger = 300,
            ),
        )
        assertEquals(0, report.u(0))
        assertEquals(255, report.u(1))
        assertEquals(0, report.u(4))
        assertEquals(255, report.u(5))
    }

    @Test
    fun `unit conversions map onto the declared range`() {
        assertEquals(0, GamepadState.axisFromUnit(-1f))
        assertEquals(128, GamepadState.axisFromUnit(0f))
        assertEquals(255, GamepadState.axisFromUnit(1f))
        assertEquals(0, GamepadState.axisFromUnit(-99f)) // out of range inputs clamp
        assertEquals(255, GamepadState.axisFromUnit(99f))
        assertEquals(0, GamepadState.triggerFromUnit(0f))
        assertEquals(255, GamepadState.triggerFromUnit(1f))
    }

    @Test
    fun `descriptor is a well-formed item stream declaring one gamepad collection`() {
        val d = GenericHidProfile.descriptor.map { it.toInt() and 0xFF }

        // Usage Page (Generic Desktop), Usage (Gamepad), Collection (Application).
        assertEquals(listOf(0x05, 0x01, 0x09, 0x05, 0xA1, 0x01), d.take(6))
        assertEquals("descriptor must close both collections", listOf(0xC0, 0xC0), d.takeLast(2))

        // Walk the short-item encoding: the low two bits of each prefix give the payload length.
        // If this terminates exactly at the end, the item stream is structurally sound.
        var i = 0
        var reportBits = 0
        while (i < d.size) {
            val prefix = d[i]
            val size = when (prefix and 0x03) {
                0 -> 0
                1 -> 1
                2 -> 2
                else -> 4
            }
            // Input (Data/Const) items are tag 0x80 on the Main type; total their declared bits.
            if (prefix and 0xFC == 0x80) reportBits += currentReportBits(d, i)
            i += 1 + size
        }
        assertEquals("item boundaries must land exactly on the end of the descriptor", d.size, i)

        // Nine bytes of payload, matching what encode() produces.
        assertEquals(GenericHidProfile.REPORT_SIZE * 8, reportBits)
    }

    /**
     * Report Size multiplied by Report Count, as declared by the most recent global items before
     * the Input item at [inputIndex].
     */
    private fun currentReportBits(d: List<Int>, inputIndex: Int): Int {
        var size = 0
        var count = 0
        var i = 0
        while (i < inputIndex) {
            val prefix = d[i]
            val len = when (prefix and 0x03) {
                0 -> 0
                1 -> 1
                2 -> 2
                else -> 4
            }
            when (prefix and 0xFC) {
                0x74 -> size = d[i + 1] // Report Size
                0x94 -> count = d[i + 1] // Report Count
            }
            i += 1 + len
        }
        return size * count
    }
}
