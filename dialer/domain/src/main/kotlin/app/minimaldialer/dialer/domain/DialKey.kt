package app.minimaldialer.dialer.domain

/** The twelve keys of the keypad, with the letters printed under the digit. */
enum class DialKey(val symbol: Char, val letters: String, val longPressSymbol: Char? = null) {
    ONE('1', ""),
    TWO('2', "ABC"),
    THREE('3', "DEF"),
    FOUR('4', "GHI"),
    FIVE('5', "JKL"),
    SIX('6', "MNO"),
    SEVEN('7', "PQRS"),
    EIGHT('8', "TUV"),
    NINE('9', "WXYZ"),
    STAR('*', "", longPressSymbol = ','),
    ZERO('0', "+", longPressSymbol = '+'),
    POUND('#', "", longPressSymbol = ';');

    companion object {
        /** Row-major layout as rendered on screen. */
        val layout: List<List<DialKey>> = listOf(
            listOf(ONE, TWO, THREE),
            listOf(FOUR, FIVE, SIX),
            listOf(SEVEN, EIGHT, NINE),
            listOf(STAR, ZERO, POUND),
        )

        fun fromSymbol(c: Char): DialKey? = entries.firstOrNull { it.symbol == c }
    }
}

/** Maps letters to keypad digits for T9-style contact search. */
object T9 {
    private val map: Map<Char, Char> = buildMap {
        DialKey.entries.forEach { key -> key.letters.forEach { put(it, key.symbol) } }
    }

    /** Returns the keypad digit for [c], the digit itself for digits, or null. */
    fun digitFor(c: Char): Char? {
        if (c in '0'..'9') return c
        val upper = c.uppercaseChar()
        return if (upper == '+') null else map[upper]
    }

    /** "Ann-Marie" -> "2666274". Characters without a key are dropped. */
    fun encode(text: String): String = buildString { text.forEach { ch -> digitFor(ch)?.let { append(it) } } }
}
