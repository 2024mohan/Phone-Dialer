package app.minimaldialer.recordings.domain

import app.minimaldialer.recording.domain.CaptureSource
import app.minimaldialer.recording.domain.RecordingMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class RecordingCatalogTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val day = 24 * 3600 * 1000L
    private val today = LocalDate.of(2026, 10, 28)
    private val todayMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun rec(id: String, name: String?, number: String, at: Long, dur: Long = 1000, size: Long = 10, audible: Long = 1) =
        RecordingMeta(id, number, name, null, at, dur, audible, CaptureSource.MICROPHONE, sampleRate = 16_000, sizeBytes = size)

    private val a = rec("a", "Mohan", "+91 98450 12345", todayMillis + 3_600_000, dur = 5_000, size = 50)
    private val b = rec("b", null, "080 2222 3333", todayMillis - day + 10, dur = 9_000, size = 20)
    private val c = rec("c", "mohan", "09845012345", todayMillis - 5 * day, dur = 1_000, size = 90, audible = 0)
    private val all = listOf(a, b, c)

    @Test fun sorting() {
        assertEquals(listOf("a", "b", "c"), RecordingCatalog.sort(all, SortOrder.NEWEST).map { it.id })
        assertEquals(listOf("c", "b", "a"), RecordingCatalog.sort(all, SortOrder.OLDEST).map { it.id })
        assertEquals(listOf("b", "a", "c"), RecordingCatalog.sort(all, SortOrder.LONGEST).map { it.id })
        assertEquals(listOf("c", "a", "b"), RecordingCatalog.sort(all, SortOrder.LARGEST).map { it.id })
    }

    @Test fun `group by date labels today and yesterday`() {
        val groups = RecordingCatalog.group(all, GroupBy.DATE, zone, today)
        assertEquals(
            listOf(RelativeDay.TODAY, RelativeDay.YESTERDAY, RelativeDay.OTHER),
            groups.map { (it.key as GroupKey.Day).relative },
        )
    }

    @Test fun `group by contact merges case variants`() {
        val groups = RecordingCatalog.group(all, GroupBy.CONTACT, zone, today)
        assertEquals(2, groups.size)
        assertEquals(listOf("080 2222 3333", "Mohan"), groups.map { (it.key as GroupKey.Person).label })
        assertEquals(6_000, groups[1].totalDurationMillis)
    }

    @Test fun `filter by name or digits`() {
        assertEquals(listOf("a", "c"), RecordingCatalog.filter(all, "moh").map { it.id })
        assertEquals(listOf("b"), RecordingCatalog.filter(all, "2222").map { it.id })
        assertEquals(all, RecordingCatalog.filter(all, " "))
    }

    @Test fun `summary and age filter`() {
        val s = RecordingCatalog.summary(all, totalBytesOnDisk = 999, freeBytes = 1_000)
        assertEquals(3, s.count); assertEquals(15_000, s.totalDurationMillis); assertEquals(1, s.silentCount)
        assertTrue(s.lowSpace)
        assertFalse(s.copy(freeBytes = 10L shl 30).lowSpace)
        assertEquals(listOf("c"), RecordingCatalog.olderThan(all, 2, todayMillis).map { it.id })
    }

    @Test fun `rename rules`() {
        assertEquals(RenameRules.Result.Valid("Bank"), RenameRules.validate("  Ba\u0000nk "))
        assertEquals(RenameRules.Result.Valid(null), RenameRules.validate("   "))
        assertEquals(RenameRules.Result.TooLong, RenameRules.validate("x".repeat(81)))
    }
}
