package com.blugaemand.input

import com.blugaemand.input.art.ArtPacks
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The version stamped on everything written out. Bump it when a change would make an older build
 * misread a newer file, and handle the older number in [decodeLayouts] rather than here.
 */
const val LAYOUT_FORMAT_VERSION: Int = 1

/**
 * What a layout looks like on disk.
 *
 * Always a list, even when there is one layout: the store saves the whole user library at once, and
 * sharing a single layout is a list of one. One shape means one version number to reason about.
 */
@Serializable
private data class LayoutFile(val version: Int, val layouts: List<GamepadLayout>)

private val json = Json {
    prettyPrint = true
    // A field added by a newer build must not stop an older one reading the rest of the file.
    ignoreUnknownKeys = true
    // A layout keeps the values it was saved with even if the default later moves. Without this a
    // control that happened to sit on the default would silently follow a change it never asked for.
    encodeDefaults = true
    classDiscriminator = "type"
}

/** Writes [layouts] as the JSON the store keeps and the share sheet will hand out. */
fun encodeLayouts(layouts: List<GamepadLayout>): String =
    json.encodeToString(LayoutFile(LAYOUT_FORMAT_VERSION, layouts))

/**
 * Reads back what [encodeLayouts] wrote.
 *
 * Throws [SerializationException] on anything it cannot fully make sense of — a version it does not
 * know, malformed JSON, or an art pack that is not installed. Decoding is deliberately
 * all-or-nothing: a layout that loaded halfway would be a pad with buttons missing from it, which is
 * worse than one that refuses to load and says so.
 */
fun decodeLayouts(text: String): List<GamepadLayout> {
    val file = try {
        json.decodeFromString<LayoutFile>(text)
    } catch (e: IllegalArgumentException) {
        // A file can be valid JSON of the right shape and still describe something that cannot
        // exist -- a cluster with no members. Those are caught by `require` in the control's own
        // constructor and arrive as plain IllegalArgumentExceptions, which would sail past every
        // caller: SerializationException is what this promises to throw and what LayoutStore
        // catches. Note the order is not a choice -- SerializationException *is* an
        // IllegalArgumentException, so this catches both kinds and only converts the other one.
        throw e as? SerializationException ?: SerializationException(e.message, e)
    }
    // Only one version exists so far. A second one is handled here, by migrating the older shape
    // forward -- not by loosening this check.
    if (file.version != LAYOUT_FORMAT_VERSION) {
        throw SerializationException(
            "layout format version ${file.version}, expected $LAYOUT_FORMAT_VERSION",
        )
    }
    return file.layouts
}

/**
 * An ARGB [Int] as `#AARRGGBB`.
 *
 * A colour with a full alpha byte is a negative [Int], so written as a number every saved colour
 * would read as a large negative integer — no use in a file meant to be shared and hand-edited.
 * Reading is lenient in the two ways a person editing one by hand would expect: the `#` is optional,
 * and six digits mean fully opaque.
 */
object ArgbColorSerializer : KSerializer<Int> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ArgbColor", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeString(value.asHexColor())
    }

    override fun deserialize(decoder: Decoder): Int {
        val text = decoder.decodeString().removePrefix("#")
        val value = text.toLongOrNull(16)
        if (value == null || (text.length != 6 && text.length != 8)) {
            throw SerializationException("'$text' is not a #RRGGBB or #AARRGGBB colour")
        }
        return if (text.length == 6) (value or OPAQUE).toInt() else value.toInt()
    }

    private const val OPAQUE = 0xFF000000L
}

/**
 * An ARGB [Int] written the way a saved layout writes it.
 *
 * Here rather than inline in [ArgbColorSerializer] because the editor's colour picker shows the
 * colour it is on, and the useful thing to show is the string someone would find in the file — two
 * formats for the same colour is one of them being wrong somewhere.
 */
fun Int.asHexColor(): String = "#%08X".format(this)

/**
 * An [ArtPack] as its id alone, resolved back through [ArtPacks].
 *
 * A layout in image mode is geometry plus the name of a pack, so writing out a picture per control
 * would let a saved layout disagree with the pack it claims to use. An id that is not installed
 * throws rather than degrading to colours mode — a layout quietly arriving as a different-looking
 * pad than the one that was shared is the worse failure.
 */
object ArtPackSerializer : KSerializer<ArtPack> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ArtPackId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ArtPack) {
        encoder.encodeString(value.id)
    }

    override fun deserialize(decoder: Decoder): ArtPack {
        val id = decoder.decodeString()
        return ArtPacks.byId(id) ?: throw SerializationException("no art pack with id '$id'")
    }
}
