package com.wellnesswingman.data.model.analysis

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Number parsers that survive a model answering in words.
 *
 * A JSON Schema is sent with every extraction request, but whether it is *enforced* depends on
 * the provider and the call path, so it has to be treated as a hint the model usually follows
 * rather than a guarantee. In practice a model asked for `"confidence": 0.4` will sometimes
 * answer `"confidence": "medium"`.
 *
 * Strict parsing turns that into a total loss: one bad field aborts the whole object, discarding
 * the food and factors the model got right. These serializers make the mismatch cost the single
 * field instead.
 *
 * Note that `Json { coerceInputValues = true }` does not help here — it coerces nulls and unknown
 * enum values to defaults, not a string where a number was expected.
 */

/**
 * Reads a 0.0–1.0 confidence that may arrive as a number, a numeric string, a percentage, or one
 * of the words models reach for. Anything unreadable becomes 0.0 — an unknown confidence is
 * better expressed as "no confidence stated" than as a number nobody supplied.
 */
object LenientConfidenceSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientConfidence", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double =
        readLenientDouble(decoder, ::confidenceFromWord)?.coerceIn(0.0, 1.0) ?: 0.0

    override fun serialize(encoder: Encoder, value: Double) {
        encoder.encodeDouble(value)
    }
}

/**
 * Reads an optional quantity that may arrive as a number or as a string carrying its unit
 * ("300 kcal", "24g", "about 15"). Unreadable values become null rather than zero: a missing
 * figure and a figure of zero mean very different things in a nutrition total.
 */
object LenientNullableDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientNullableDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double? = readLenientDouble(decoder, { null })

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
}

/**
 * Pulls a number out of whatever the model actually sent.
 *
 * @param fromWord maps a bare word to a value, for fields where words have an agreed meaning.
 */
private fun readLenientDouble(decoder: Decoder, fromWord: (String) -> Double?): Double? {
    val jsonDecoder = decoder as? JsonDecoder
        ?: return runCatching { decoder.decodeDouble() }.getOrNull()

    val element = jsonDecoder.decodeJsonElement()
    if (element is JsonNull) return null

    val primitive = element as? JsonPrimitive ?: return null
    primitive.doubleOrNull?.let { return it }

    val text = primitive.content.trim()
    if (text.isEmpty() || text.equals("null", ignoreCase = true)) return null

    fromWord(text.lowercase())?.let { return it }

    // "300 kcal", "24 g", "about 15", "~520" — keep the first number and drop the prose.
    return firstNumberIn(text)
}

/**
 * Words models use in place of a confidence score, mapped to the middle of the range each
 * implies. Approximate on purpose: the point is to keep the rest of the object, not to pretend
 * the word carried more precision than it did.
 */
private fun confidenceFromWord(text: String): Double? = when {
    text.startsWith("very high") || text == "certain" -> 0.95
    text.startsWith("high") -> 0.85
    text.startsWith("medium") || text.startsWith("moderate") -> 0.5
    text.startsWith("very low") -> 0.1
    text.startsWith("low") -> 0.25
    text == "none" || text == "unknown" || text == "unsure" -> 0.0
    // "80%" reads as 0.8; a bare "0.8" was already handled as a numeric string.
    text.endsWith("%") -> text.dropLast(1).trim().toDoubleOrNull()?.let { it / 100.0 }
    else -> null
}

/** Extracts the first number in a string, tolerating a leading `~` and a trailing unit. */
private fun firstNumberIn(text: String): Double? {
    val builder = StringBuilder()

    for (char in text) {
        when {
            char.isDigit() -> builder.append(char)
            char == '.' && !builder.contains('.') && builder.isNotEmpty() -> builder.append(char)
            char == '-' && builder.isEmpty() -> builder.append(char)
            builder.isNotEmpty() -> return builder.toString().toDoubleOrNull()
            else -> Unit
        }
    }

    return builder.toString().toDoubleOrNull()
}
