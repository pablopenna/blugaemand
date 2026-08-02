package com.blugaemand.input

import com.blugaemand.hid.GamepadButton
import com.blugaemand.input.art.ArtPacks
import com.blugaemand.input.layouts.Layouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariants for layouts drawn with art rather than shapes. Kept apart from the router's own tests
 * because they are about what a layout looks like, not about where a touch goes.
 *
 * Two things that used to need tests are now unrepresentable and so are absent: a pressed-only
 * control, because [Glyph] takes a non-null idle picture, and a colours-mode layout carrying dead
 * glyphs, because only [LayoutStyle.Images] holds an [ArtPack].
 *
 * Nothing here checks that a [ControlIcon] resolves to a real drawable either: the mapping in
 * `com.blugaemand.ui.drawableFor` is an exhaustive `when` over the enum, and every branch names an
 * `R.drawable` constant, so a missing picture or an unmapped icon is already a compile error.
 */
class LayoutArtTest {

    private val imageLayouts = Layouts.ALL.mapNotNull { layout ->
        (layout.style as? LayoutStyle.Images)?.let { layout to it.pack }
    }

    /**
     * The face symbol each pack draws in each position of the diamond, keyed by the label the
     * control carries — which is the letter the *host* reports for it, whatever the pack draws
     * there. Every image layout must appear, so a new face plate cannot land without saying which
     * way round its symbols go.
     */
    private val faceGlyphs: Map<String, Map<String, Glyph>> = mapOf(
        "xbox" to mapOf(
            "Y" to Glyph(ControlIcon.XBOX_Y, ControlIcon.XBOX_Y_PRESSED),
            "X" to Glyph(ControlIcon.XBOX_X, ControlIcon.XBOX_X_PRESSED),
            "A" to Glyph(ControlIcon.XBOX_A, ControlIcon.XBOX_A_PRESSED),
            "B" to Glyph(ControlIcon.XBOX_B, ControlIcon.XBOX_B_PRESSED),
        ),
        "playstation" to mapOf(
            "Y" to Glyph(ControlIcon.PS_TRIANGLE, ControlIcon.PS_TRIANGLE_PRESSED),
            "X" to Glyph(ControlIcon.PS_SQUARE, ControlIcon.PS_SQUARE_PRESSED),
            "A" to Glyph(ControlIcon.PS_CROSS, ControlIcon.PS_CROSS_PRESSED),
            "B" to Glyph(ControlIcon.PS_CIRCLE, ControlIcon.PS_CIRCLE_PRESSED),
        ),
        // Read this one carefully: the key is the letter the *host* reports, the value is the
        // letter Nintendo prints in that spot, and on a Switch the two disagree in all four
        // positions. The control the host calls Y is the top of the diamond, and Nintendo puts X
        // there; the one it calls B is the right, and Nintendo puts A there. Both pairs swap, which
        // is the swap every Switch owner already lives with.
        "switch" to mapOf(
            "Y" to Glyph(ControlIcon.SWITCH_X, ControlIcon.SWITCH_X_PRESSED),
            "X" to Glyph(ControlIcon.SWITCH_Y, ControlIcon.SWITCH_Y_PRESSED),
            "A" to Glyph(ControlIcon.SWITCH_B, ControlIcon.SWITCH_B_PRESSED),
            "B" to Glyph(ControlIcon.SWITCH_A, ControlIcon.SWITCH_A_PRESSED),
        ),
        // Valve kept Microsoft's arrangement, so this one is the only plate where the printed
        // letter and the reported letter agree everywhere.
        "steamdeck" to mapOf(
            "Y" to Glyph(ControlIcon.DECK_Y, ControlIcon.DECK_Y_PRESSED),
            "X" to Glyph(ControlIcon.DECK_X, ControlIcon.DECK_X_PRESSED),
            "A" to Glyph(ControlIcon.DECK_A, ControlIcon.DECK_A_PRESSED),
            "B" to Glyph(ControlIcon.DECK_B, ControlIcon.DECK_B_PRESSED),
        ),
    )

    /**
     * Controls an image layout draws as a shape on purpose, and why. Sticks are in every one
     * because no static picture can show a knob displaced from centre; the PS button is in `ps5`
     * because the pack has no picture of one, and lending it the mute or touchpad glyph would put
     * a different button's picture on the one that sends `GUIDE`.
     */
    private val drawnAsShapes: Map<String, Set<ControlId>> = mapOf(
        "xbox" to setOf(
            ControlId.Stick(ControlId.Side.LEFT),
            ControlId.Stick(ControlId.Side.RIGHT),
        ),
        "ps5" to setOf(
            ControlId.Stick(ControlId.Side.LEFT),
            ControlId.Stick(ControlId.Side.RIGHT),
            ControlId.Button(GamepadButton.GUIDE),
        ),
        // Kenney draws both a Switch Home button and a Steam button, so unlike the PS5 plate these
        // two have nothing falling back to a shape except the sticks.
        "switch" to setOf(
            ControlId.Stick(ControlId.Side.LEFT),
            ControlId.Stick(ControlId.Side.RIGHT),
        ),
        "steamdeck" to setOf(
            ControlId.Stick(ControlId.Side.LEFT),
            ControlId.Stick(ControlId.Side.RIGHT),
        ),
    )

    @Test
    fun `the face buttons show the symbol for where they sit, not for the slot they drive`() {
        // The layout puts its Y key -- the top of the diamond -- on GamepadButton.WEST, so that is
        // the control that has to carry each pack's top symbol: Xbox's Y, PlayStation's triangle.
        // Matching a picture to the slot's name instead puts the wrong one under the thumb, and
        // sends the wrong button with it.
        for ((layout, pack) in imageLayouts) {
            val faces = layout.controls.associateBy { it.label }
            for ((label, expected) in faceGlyphs.getValue(pack.id)) {
                val id = faces.getValue(label).id
                assertEquals("${pack.id}: $label", expected.idle, pack.glyph(id, held = false))
                assertEquals("${pack.id}: $label held", expected.pressed, pack.glyph(id, held = true))
            }
        }
    }

    @Test
    fun `the crossed face keys stay on the slots that report their letter`() {
        // The other half of the guard above: the pack tables are reached through the label, so
        // they would survive the crossing being undone. Hosts read HID's legacy aliases, where
        // BTN_WEST is Y and BTN_NORTH is X.
        for (layout in Layouts.ALL) {
            val faces = layout.controls.associateBy { it.label }
            assertEquals(
                "${layout.id}: Y",
                ControlId.Button(GamepadButton.WEST),
                faces.getValue("Y").id,
            )
            assertEquals(
                "${layout.id}: X",
                ControlId.Button(GamepadButton.NORTH),
                faces.getValue("X").id,
            )
        }
    }

    @Test
    fun `an image layout has a picture for every control, bar the ones it declares`() {
        for ((layout, pack) in imageLayouts) {
            val glyphless = layout.controls.map { it.id }.filterNot { it in pack.glyphs }.toSet()
            assertEquals(layout.id, drawnAsShapes.getValue(layout.id), glyphless)
        }
    }

    @Test
    fun `a control falling back to its shape still has something to draw in it`() {
        // The fallback draws the plate and its label, so a picture-less button with no label would
        // render as a blank disc among prompts. Sticks are exempt: they draw a well and a cap.
        for ((layout, pack) in imageLayouts) {
            for (spec in layout.controls) {
                if (spec.id in pack.glyphs || spec.id is ControlId.Stick) continue
                assertTrue("${layout.id}: ${spec.id} is blank", spec.label.isNotEmpty())
            }
        }
    }

    @Test
    fun `a group placed as one control still draws every pack's own art`() {
        // The reason a cluster needed nothing adding to ArtPack: a plate draws as its members, and
        // a member carries an ordinary ControlId, which is exactly what a pack is keyed by. So
        // every existing pack already has a picture for every button on a plate, pressed included,
        // and would go on doing so for a plate nobody has thought of yet.
        val members = ControlGroups.ALL
            .flatMap { group ->
                (ControlGroups.clustered(group).controls.single().shape
                    as ControlSpec.Shape.Cluster).members
            }
            .map { it.id }
            .toSet()
            // The one exception, and not a new one: no ControlIcon names a single D-pad arm, so
            // the four-arm group falls back to the arrows the specs carry. A plate is what would
            // make per-arm art worth having -- each member knows its own direction, which a
            // one-piece cross does not -- so this line is what to delete when that art arrives.
            .filterNot { it is ControlId.DpadButton }

        for ((layout, pack) in imageLayouts) {
            for (id in members - drawnAsShapes.getValue(layout.id)) {
                assertTrue("${pack.id} has no picture for $id", pack.glyph(id, held = false) != null)
            }
        }
    }

    @Test
    fun `every pack has a distinct id and a distinct name to be picked by`() {
        // The editor's Appearance page lists these, so a blank or repeated name is a row you cannot
        // tell from the one above it. Ids are separately unique because that is what a saved layout
        // resolves its pack through, and two packs answering to one id is a layout that loads as
        // the wrong pad.
        val packs = ArtPacks.ALL
        assertTrue(packs.isNotEmpty())
        for (pack in packs) assertTrue(pack.id, pack.name.isNotBlank())
        assertEquals(packs.size, packs.map { it.id }.toSet().size)
        assertEquals(packs.size, packs.map { it.name }.toSet().size)
    }

    @Test
    fun `the catalog offers both presentations`() {
        // Guards the point of the feature: if either mode falls out of ALL, the tests above start
        // passing vacuously.
        assertTrue(imageLayouts.isNotEmpty())
        assertTrue(Layouts.ALL.any { it.style is LayoutStyle.Colors })
    }
}
