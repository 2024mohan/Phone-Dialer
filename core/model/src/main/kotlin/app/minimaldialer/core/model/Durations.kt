package app.minimaldialer.core.model

object Durations {
    /** 0:05, 12:34, 1:02:03. Negative input is clamped to zero. */
    fun formatSeconds(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(java.util.Locale.ROOT, h, m, sec) else "%d:%02d".format(java.util.Locale.ROOT, m, sec)
    }

    fun formatMillis(millis: Long): String = formatSeconds(millis.coerceAtLeast(0) / 1000)
}

object ByteSizes {
    fun format(bytes: Long): String {
        val b = bytes.coerceAtLeast(0)
        return when {
            b < 1024 -> "$b B"
            b < 1024L * 1024 -> "%.1f KB".format(java.util.Locale.ROOT, b / 1024.0)
            b < 1024L * 1024 * 1024 -> "%.1f MB".format(java.util.Locale.ROOT, b / (1024.0 * 1024))
            else -> "%.2f GB".format(java.util.Locale.ROOT, b / (1024.0 * 1024 * 1024))
        }
    }
}
