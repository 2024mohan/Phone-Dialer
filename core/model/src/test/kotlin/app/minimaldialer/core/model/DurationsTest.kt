package app.minimaldialer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationsTest {
    @Test fun formats() {
        assertEquals("0:00", Durations.formatSeconds(0))
        assertEquals("0:05", Durations.formatSeconds(5))
        assertEquals("12:34", Durations.formatSeconds(754))
        assertEquals("1:02:03", Durations.formatSeconds(3723))
        assertEquals("0:00", Durations.formatSeconds(-4))
        assertEquals("0:01", Durations.formatMillis(1999))
    }

    @Test fun byteSizes() {
        assertEquals("512 B", ByteSizes.format(512))
        assertEquals("1.5 KB", ByteSizes.format(1536))
        assertEquals("2.0 MB", ByteSizes.format(2L * 1024 * 1024))
    }
}
