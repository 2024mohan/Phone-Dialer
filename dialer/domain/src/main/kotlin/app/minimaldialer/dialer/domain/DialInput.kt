package app.minimaldialer.dialer.domain

import app.minimaldialer.core.model.PhoneNumbers

/**
 * Immutable editing model for the number field. The cursor is always at the end; the
 * field is short enough that mid-string editing adds clutter without real benefit.
 */
@JvmInline
value class DialInput(val text: String = "") {

    val isEmpty: Boolean get() = text.isEmpty()

    fun press(key: DialKey): DialInput = append(key.symbol)

    fun longPress(key: DialKey): DialInput = key.longPressSymbol?.let { append(it) } ?: this

    fun append(c: Char): DialInput {
        if (!PhoneNumbers.isDialable(c) || text.length >= MAX_LENGTH) return this
        // '+' is only meaningful as the first character of an international number.
        if (c == '+' && text.isNotEmpty()) return this
        return DialInput(text + c)
    }

    fun backspace(): DialInput = if (text.isEmpty()) this else DialInput(text.dropLast(1))

    fun clear(): DialInput = EMPTY

    /** Pasted or intent-supplied text, keeping only dialable characters. */
    fun replaceWith(raw: String): DialInput {
        var out = EMPTY
        raw.forEach { out = out.append(it) }
        return out
    }

    /** The string handed to Telecom; empty when there is nothing callable. */
    fun dialString(): String = text.takeIf { s -> s.any { it in '0'..'9' || it == '*' || it == '#' } } ?: ""

    companion object {
        const val MAX_LENGTH = 64
        val EMPTY = DialInput("")
    }
}
