package com.blugaemand.input

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.art.PLAYSTATION_ART
import com.blugaemand.input.layouts.DEFAULT_LAYOUT
import com.blugaemand.input.layouts.Layouts
import com.blugaemand.input.layouts.PS5_LAYOUT
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The saved layout format.
 *
 * Layouts people build are stored as this JSON and shared as this JSON, which makes the names in it
 * — every `@SerialName`, every [GamepadButton] and [ControlIcon] entry, every [ArtPack.id] — a
 * compatibility surface rather than an implementation detail. The golden file below is what turns a
 * rename into a failing test instead of a phone full of layouts pointing at the wrong buttons.
 */
class LayoutSerializationTest {

    // -- Round trips ----------------------------------------------------------------------

    @Test
    fun `every built-in layout survives a round trip intact`() {
        // Full data-class equality, not an epsilon comparison: Float.toString round trips exactly,
        // so a coordinate that comes back even slightly different is a real bug.
        val decoded = decodeLayouts(encodeLayouts(Layouts.ALL))
        assertEquals(Layouts.ALL, decoded)
    }

    @Test
    fun `an image layout comes back holding the catalog's own pack`() {
        // Not merely an equal pack -- the same instance. A layout that deserialised into a copy
        // would compare equal today and stop doing so the moment ArtPack gains anything that is
        // not part of its equality.
        val decoded = decodeLayouts(encodeLayouts(listOf(PS5_LAYOUT))).single()
        assertSame(PLAYSTATION_ART, (decoded.style as LayoutStyle.Images).pack)
    }

    @Test
    fun `a layout with no controls at all round trips`() {
        // The editor can create one, and an empty list is exactly the kind of thing a format
        // quietly turns into a missing key and then into a null.
        val empty = GamepadLayout(id = "empty", name = "Empty", controls = emptyList())
        assertEquals(listOf(empty), decodeLayouts(encodeLayouts(listOf(empty))))
    }

    @Test
    fun `an empty library round trips`() {
        assertEquals(emptyList<GamepadLayout>(), decodeLayouts(encodeLayouts(emptyList())))
    }

    // -- Colours --------------------------------------------------------------------------

    @Test
    fun `colours are written as hex and read back exactly`() {
        val style = LayoutStyle.Colors(resting = 0xFF102030.toInt(), pressed = 0x80AABBCC.toInt())
        val layout = DEFAULT_LAYOUT.copy(style = style)
        val text = encodeLayouts(listOf(layout))

        assertTrue(text, "\"resting\": \"#FF102030\"" in text)
        assertTrue(text, "\"pressed\": \"#80AABBCC\"" in text)
        assertEquals(style, decodeLayouts(text).single().style)
    }

    @Test
    fun `a hand-written colour may drop the hash and the alpha`() {
        // Leniency aimed squarely at someone editing a shared file by hand; six digits mean opaque.
        val text = layoutFileOf(
            """{"id":"c","name":"c","controls":[],""" +
                """"style":{"type":"colors","resting":"102030","pressed":"#405060"}}""",
        )
        val style = decodeLayouts(text).single().style as LayoutStyle.Colors
        assertEquals(0xFF102030.toInt(), style.resting)
        assertEquals(0xFF405060.toInt(), style.pressed)
    }

    @Test
    fun `a colour that is not hex is rejected`() {
        val text = layoutFileOf(
            """{"id":"c","name":"c","controls":[],""" +
                """"style":{"type":"colors","resting":"tomato","pressed":"#FF405060"}}""",
        )
        val error = assertThrowsSerialization { decodeLayouts(text) }
        assertTrue(error, "tomato" in error)
    }

    // -- Art packs ------------------------------------------------------------------------

    @Test
    fun `a layout naming a pack that is not installed is refused, and says which`() {
        val text = layoutFileOf(
            """{"id":"x","name":"x","controls":[],""" +
                """"style":{"type":"images","pack":"gamecube"}}""",
        )
        val error = assertThrowsSerialization { decodeLayouts(text) }
        assertTrue(error, "gamecube" in error)
    }

    // -- Versioning -----------------------------------------------------------------------

    @Test
    fun `the version is stamped on what is written`() {
        assertTrue("\"version\": $LAYOUT_FORMAT_VERSION" in encodeLayouts(emptyList()))
    }

    @Test
    fun `a version this build does not know is refused rather than guessed at`() {
        val error = assertThrowsSerialization {
            decodeLayouts("""{"version": 99, "layouts": []}""")
        }
        assertTrue(error, "99" in error)
    }

    @Test
    fun `a field a newer build added does not stop this one reading the file`() {
        // The other half of forward compatibility: unknown keys are skipped, so a layout saved by a
        // build that learned a new property still loads on one that has not.
        val text = layoutFileOf(
            """{"id":"n","name":"n","controls":[],"opacity":0.5,""" +
                """"style":{"type":"colors","resting":"#FF000000","pressed":"#FFFFFFFF"}}""",
        )
        assertEquals("n", decodeLayouts(text).single().id)
    }

    // -- The names the format is made of --------------------------------------------------

    @Test
    fun `the on-disk shape is exactly this`() {
        // A golden file, deliberately covering all four shapes, all four control kinds and both
        // styles in one go. It fails on any rename of a @SerialName, a GamepadButton entry, a
        // ControlId.Side entry or an ArtPack id -- all of which are silent data corruption
        // otherwise, because a layout already saved on a phone names them.
        //
        // If this test fails for a change that is genuinely wanted, the fix is a format version
        // and a migration in decodeLayouts, not a new expected string.
        assertEquals(GOLDEN, encodeLayouts(listOf(GOLDEN_LAYOUT)))
    }

    @Test
    fun `the golden file is still readable`() {
        assertEquals(GOLDEN_LAYOUT, decodeLayouts(GOLDEN).single())
    }

    // -- Helpers --------------------------------------------------------------------------

    private fun layoutFileOf(layout: String): String =
        """{"version": $LAYOUT_FORMAT_VERSION, "layouts": [$layout]}"""

    /** Runs [block], returning the message of the [SerializationException] it must throw. */
    private fun assertThrowsSerialization(block: () -> Unit): String = try {
        block()
        throw AssertionError("expected a SerializationException")
    } catch (e: SerializationException) {
        e.message.orEmpty()
    }

    private companion object {

        val GOLDEN_LAYOUT = GamepadLayout(
            id = "golden",
            name = "Golden",
            controls = listOf(
                ControlSpec(
                    id = ControlId.Button(GamepadButton.WEST),
                    shape = ControlSpec.Shape.Circle(0.5f, 0.25f, radius = 0.072f),
                    label = "Y",
                ),
                ControlSpec(
                    id = ControlId.Trigger(ControlId.Side.LEFT),
                    shape = ControlSpec.Shape.Rect(0.09f, 0.08f, width = 0.12f, height = 0.11f),
                    label = "LT",
                ),
                ControlSpec(
                    id = ControlId.Stick(ControlId.Side.RIGHT),
                    shape = ControlSpec.Shape.Stick(0.78f, 0.8f, radius = 0.18f, knobRadius = 0.08f),
                ),
                ControlSpec(
                    id = ControlId.Dpad,
                    shape = ControlSpec.Shape.Dpad(0.13f, 0.83f, radius = 0.13f),
                ),
            ),
            style = LayoutStyle.Colors(),
        )

        val GOLDEN = """
            {
                "version": 1,
                "layouts": [
                    {
                        "id": "golden",
                        "name": "Golden",
                        "controls": [
                            {
                                "id": {
                                    "type": "button",
                                    "button": "WEST"
                                },
                                "shape": {
                                    "type": "circle",
                                    "centerX": 0.5,
                                    "centerY": 0.25,
                                    "radius": 0.072
                                },
                                "label": "Y"
                            },
                            {
                                "id": {
                                    "type": "trigger",
                                    "side": "LEFT"
                                },
                                "shape": {
                                    "type": "rect",
                                    "centerX": 0.09,
                                    "centerY": 0.08,
                                    "width": 0.12,
                                    "height": 0.11
                                },
                                "label": "LT"
                            },
                            {
                                "id": {
                                    "type": "stick",
                                    "side": "RIGHT"
                                },
                                "shape": {
                                    "type": "stick",
                                    "centerX": 0.78,
                                    "centerY": 0.8,
                                    "radius": 0.18,
                                    "knobRadius": 0.08
                                },
                                "label": ""
                            },
                            {
                                "id": {
                                    "type": "dpad"
                                },
                                "shape": {
                                    "type": "dpad",
                                    "centerX": 0.13,
                                    "centerY": 0.83,
                                    "radius": 0.13,
                                    "deadZone": 0.25
                                },
                                "label": ""
                            }
                        ],
                        "style": {
                            "type": "colors",
                            "resting": "#FF262B36",
                            "pressed": "#FF4C82F7"
                        }
                    }
                ]
            }
        """.trimIndent()
    }
}
