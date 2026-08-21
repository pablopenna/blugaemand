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
    fun `a layout holding the same control twice round trips`() {
        // Duplicates are a list, not a set, and a format that deduplicated them would quietly halve
        // someone's pad on the way back in.
        val id = ControlId.Button(GamepadButton.SOUTH)
        val spec = ControlSpec(id, ControlSpec.Shape.Circle(0.2f, 0.2f, radius = 0.07f), "A")
        val twice = GamepadLayout(
            id = "twice",
            name = "Twice",
            controls = listOf(spec, spec.copy(shape = ControlSpec.Shape.Circle(0.8f, 0.8f, 0.07f))),
        )
        assertEquals(listOf(twice), decodeLayouts(encodeLayouts(listOf(twice))))
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
        // The id is deliberately absurd. This test used to say `gamecube`, which was a fine
        // stand-in for "a pad we do not ship" right up until the GameCube plate landed and turned
        // an assertion about missing packs into an assertion that a real one is missing. Nothing
        // named after a console is safe here; `tomato` is, and it is what the bad-button test above
        // already uses.
        val text = layoutFileOf(
            """{"id":"x","name":"x","controls":[],""" +
                """"style":{"type":"images","pack":"tomato"}}""",
        )
        val error = assertThrowsSerialization { decodeLayouts(text) }
        assertTrue(error, "tomato" in error)
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

    @Test
    fun `a control saved before trigger modes existed reads as progressive`() {
        // The other direction, and the reason the setting is defaulted rather than required: every
        // layout already on a phone was written without this key, and each one has to come back as
        // the behaviour it was saved with rather than refusing to load.
        val text = layoutFileOf(
            """{"id":"old","name":"Old","controls":[{"id":{"type":"trigger","side":"LEFT"},""" +
                """"shape":{"type":"rect","centerX":0.09,"centerY":0.08,"width":0.12,""" +
                """"height":0.11},"label":"LT"}],""" +
                """"style":{"type":"colors","resting":"#FF000000","pressed":"#FFFFFFFF"}}""",
        )
        val control = decodeLayouts(text).single().controls.single()
        assertEquals(TriggerMode.PROGRESSIVE, control.triggerMode)
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

    // -- Clusters -------------------------------------------------------------------------

    @Test
    fun `a cluster refuses to exist with no members`() {
        // Written by hand rather than through the type, because the type is what is being tested:
        // an empty plate would otherwise get as far as ResolvedLayout's bounding box and throw
        // from there, during composition, with nothing left to say which layout was at fault.
        val message = assertThrowsSerialization {
            decodeLayouts(
                layoutFileOf(
                    """
                    {"id": "empty", "name": "Empty", "controls": [{
                        "id": {"type": "cluster"},
                        "shape": {"type": "cluster", "centerX": 0.5, "centerY": 0.5, "members": []}
                    }]}
                    """.trimIndent(),
                ),
            )
        }
        assertTrue(message, message.contains("at least one member"))
    }

    @Test
    fun `a cluster refuses to hold a thumbstick`() {
        val message = assertThrowsSerialization {
            decodeLayouts(
                layoutFileOf(
                    """
                    {"id": "sticky", "name": "Sticky", "controls": [{
                        "id": {"type": "cluster"},
                        "shape": {"type": "cluster", "centerX": 0.5, "centerY": 0.5, "members": [
                            {"id": {"type": "stick", "side": "LEFT"},
                             "shape": {"type": "stick", "centerX": 0.0, "centerY": 0.0,
                                       "radius": 0.2, "knobRadius": 0.09}}
                        ]}
                    }]}
                    """.trimIndent(),
                ),
            )
        }
        assertTrue(message, message.contains("buttons, triggers and D-pad arms"))
    }

    @Test
    fun `every group the editor offers survives a round trip as one control`() {
        // The catalog goes through the format, not just the one plate the golden file pins. A
        // cluster is the only nested thing the format has, so this is where a serializer that
        // cannot see its own children would show up.
        val clustered = ControlGroups.ALL.map { group ->
            GamepadLayout(
                id = group.name,
                name = group.name,
                controls = ControlGroups.clustered(group).controls,
            )
        }
        assertEquals(clustered, decodeLayouts(encodeLayouts(clustered)))
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
                    // The one control here that is not on the default, so the mode's own names are
                    // pinned by this file the way every other name in the format is.
                    triggerMode = TriggerMode.BINARY,
                ),
                ControlSpec(
                    id = ControlId.Stick(ControlId.Side.RIGHT),
                    shape = ControlSpec.Shape.Stick(0.78f, 0.8f, radius = 0.18f, knobRadius = 0.08f),
                ),
                ControlSpec(
                    id = ControlId.Dpad,
                    shape = ControlSpec.Shape.Dpad(0.13f, 0.83f, radius = 0.13f),
                ),
                ControlSpec(
                    id = ControlId.DpadButton(ControlId.Direction.UP),
                    shape = ControlSpec.Shape.Circle(0.13f, 0.75f, radius = 0.055f),
                    label = "▲",
                ),
                ControlSpec(
                    id = ControlId.Cluster,
                    shape = ControlSpec.Shape.Cluster(
                        0.75f,
                        0.35f,
                        members = listOf(
                            ControlSpec(
                                id = ControlId.Button(GamepadButton.EAST),
                                shape = ControlSpec.Shape.Circle(0.1f, 0f, radius = 0.06f),
                                label = "B",
                            ),
                            ControlSpec(
                                id = ControlId.Button(GamepadButton.SOUTH),
                                shape = ControlSpec.Shape.Circle(0f, 0.1f, radius = 0.06f),
                                label = "A",
                            ),
                        ),
                    ),
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
                                "label": "Y",
                                "triggerMode": "PROGRESSIVE"
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
                                "label": "LT",
                                "triggerMode": "BINARY"
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
                                "label": "",
                                "triggerMode": "PROGRESSIVE"
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
                                "label": "",
                                "triggerMode": "PROGRESSIVE"
                            },
                            {
                                "id": {
                                    "type": "dpad_button",
                                    "direction": "UP"
                                },
                                "shape": {
                                    "type": "circle",
                                    "centerX": 0.13,
                                    "centerY": 0.75,
                                    "radius": 0.055
                                },
                                "label": "▲",
                                "triggerMode": "PROGRESSIVE"
                            },
                            {
                                "id": {
                                    "type": "cluster"
                                },
                                "shape": {
                                    "type": "cluster",
                                    "centerX": 0.75,
                                    "centerY": 0.35,
                                    "members": [
                                        {
                                            "id": {
                                                "type": "button",
                                                "button": "EAST"
                                            },
                                            "shape": {
                                                "type": "circle",
                                                "centerX": 0.1,
                                                "centerY": 0.0,
                                                "radius": 0.06
                                            },
                                            "label": "B",
                                            "triggerMode": "PROGRESSIVE"
                                        },
                                        {
                                            "id": {
                                                "type": "button",
                                                "button": "SOUTH"
                                            },
                                            "shape": {
                                                "type": "circle",
                                                "centerX": 0.0,
                                                "centerY": 0.1,
                                                "radius": 0.06
                                            },
                                            "label": "A",
                                            "triggerMode": "PROGRESSIVE"
                                        }
                                    ]
                                },
                                "label": "",
                                "triggerMode": "PROGRESSIVE"
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
