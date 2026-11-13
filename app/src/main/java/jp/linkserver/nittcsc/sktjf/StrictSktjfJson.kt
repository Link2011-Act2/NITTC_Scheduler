package jp.linkserver.nittcsc.sktjf

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Validate syntax, duplicate keys and depth before invoking platform-dependent org.json. */
internal fun strictSktjfObject(bytes: ByteArray, maxBytes: Int): JSONObject {
    require(bytes.size in 1..maxBytes)
    val text = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (error: java.nio.charset.CharacterCodingException) {
        throw IllegalArgumentException("Invalid UTF-8", error)
    }
    JsonGuard(text).check()
    return JSONObject(text)
}

private class JsonGuard(private val text: String) {
    private var pos = 0
    private var nodes = 0
    fun check() { value(0); space(); require(pos == text.length) }
    private fun space() { while (pos < text.length && text[pos] in " \n\r\t") pos++ }
    private fun value(depth: Int) {
        require(depth <= 32 && ++nodes <= 100_000)
        space(); require(pos < text.length)
        when (text[pos]) {
            '{' -> obj(depth + 1)
            '[' -> array(depth + 1)
            '"' -> string()
            't' -> literal("true")
            'f' -> literal("false")
            'n' -> literal("null")
            else -> number()
        }
    }
    private fun obj(depth: Int) {
        pos++; space(); val seen = mutableSetOf<String>()
        if (take('}')) return
        while (true) {
            space(); require(pos < text.length && text[pos] == '"')
            val key = string(); require(seen.add(key)); space(); require(take(':')); value(depth); space()
            if (take('}')) return
            require(take(','))
        }
    }
    private fun array(depth: Int) {
        pos++; space(); if (take(']')) return
        while (true) { value(depth); space(); if (take(']')) return; require(take(',')) }
    }
    private fun string(): String {
        val start = pos++
        while (pos < text.length) {
            val c = text[pos++]
            if (c == '"') return JSONTokener(text.substring(start, pos)).nextValue() as String
            require(c >= ' ')
            if (c == '\\') {
                require(pos < text.length)
                val escape = text[pos++]
                require(escape in "\"\\/bfnrtu")
                if (escape == 'u') repeat(4) { require(pos < text.length && text[pos++] in "0123456789abcdefABCDEF") }
            }
        }
        throw IllegalArgumentException("Invalid JSON string")
    }
    private fun literal(value: String) { require(text.startsWith(value, pos)); pos += value.length }
    private fun number() {
        val start = pos
        if (take('-')) require(pos < text.length)
        if (!take('0')) { require(pos < text.length && text[pos] in '1'..'9'); while (pos < text.length && text[pos] in '0'..'9') pos++ }
        if (take('.')) { require(pos < text.length && text[pos] in '0'..'9'); while (pos < text.length && text[pos] in '0'..'9') pos++ }
        if (pos < text.length && text[pos] in "eE") { pos++; if (pos < text.length && text[pos] in "+-") pos++; require(pos < text.length && text[pos] in '0'..'9'); while (pos < text.length && text[pos] in '0'..'9') pos++ }
        require(pos > start)
    }
    private fun take(c: Char): Boolean = if (pos < text.length && text[pos] == c) { pos++; true } else false
}

internal fun JSONObject.fields(required: Set<String>, optional: Set<String> = emptySet()) {
    val keys = keys().asSequence().toSet()
    require(keys.containsAll(required) && keys.all { it in required || it in optional })
}
internal fun JSONObject.text(key: String, max: Int = 1024): String =
    (get(key) as? String)?.also { require(it.isNotBlank() && it.length <= max) }
        ?: throw IllegalArgumentException("Invalid text")
internal fun JSONObject.integer(key: String, range: IntRange): Int {
    val value = get(key) as? Number ?: throw IllegalArgumentException("Invalid integer")
    val double = value.toDouble(); require(double.isFinite() && double % 1.0 == 0.0 && double >= range.first && double <= range.last)
    return double.toInt()
}
internal fun JSONObject.optionalText(key: String, max: Int = 1024): String? =
    if (!has(key) || isNull(key)) null else text(key, max)
internal fun JSONObject.boolean(key: String): Boolean = get(key) as? Boolean ?: throw IllegalArgumentException("Invalid boolean")
internal fun JSONArray.objects(max: Int): List<JSONObject> {
    require(length() <= max)
    return (0 until length()).map { get(it) as? JSONObject ?: throw IllegalArgumentException("Invalid object") }
}
internal fun JSONArray.strings(max: Int, maxLength: Int = 1024): List<String> {
    require(length() <= max)
    return (0 until length()).map { (get(it) as? String)?.also { value -> require(value.isNotBlank() && value.length <= maxLength) } ?: throw IllegalArgumentException("Invalid text") }
}
