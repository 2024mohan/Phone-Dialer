package app.minimaldialer.core.model

/**
 * Locale-independent phone-number helpers. Formatting for display is done on the Android
 * side with PhoneNumberUtils; these helpers only cover comparison and sanitising.
 */
object PhoneNumbers {

    /** Keeps digits, '*', '#' and a single leading '+'. */
    fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        raw.forEachIndexed { i, c ->
            when {
                c.isAsciiDigit() || c == '*' || c == '#' -> sb.append(c)
                c == '+' && sb.isEmpty() && raw.substring(0, i).none { it.isAsciiDigit() } -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Loose equality used to match call-log or in-call numbers against contacts when no
     * system lookup is available: compares the last [significantDigits] digits.
     */
    fun looselyEqual(a: String, b: String, significantDigits: Int = 9): Boolean {
        val da = a.filter { it.isAsciiDigit() }
        val db = b.filter { it.isAsciiDigit() }
        if (da.isEmpty() || db.isEmpty()) return false
        if (da.length < significantDigits || db.length < significantDigits) return da == db
        return da.takeLast(significantDigits) == db.takeLast(significantDigits)
    }

    /** Characters that may be shown in, and dialled from, the number field. */
    fun isDialable(c: Char): Boolean = c.isAsciiDigit() || c == '*' || c == '#' || c == '+' || c == ',' || c == ';'

    private fun Char.isAsciiDigit() = this in '0'..'9'
}
