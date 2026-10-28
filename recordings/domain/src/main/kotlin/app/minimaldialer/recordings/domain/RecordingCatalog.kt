package app.minimaldialer.recordings.domain

import app.minimaldialer.core.model.PhoneNumbers
import app.minimaldialer.recording.domain.RecordingMeta
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class GroupBy { DATE, CONTACT, NONE }
enum class SortOrder { NEWEST, OLDEST, LONGEST, LARGEST }

sealed interface GroupKey {
    data class Day(val date: LocalDate, val relative: RelativeDay) : GroupKey
    data class Person(val label: String) : GroupKey
    data object All : GroupKey
}

enum class RelativeDay { TODAY, YESTERDAY, OTHER }

data class RecordingGroup(val key: GroupKey, val items: List<RecordingMeta>) {
    val totalDurationMillis: Long get() = items.sumOf { it.durationMillis }
}

data class StorageSummary(
    val count: Int,
    val totalBytes: Long,
    val totalDurationMillis: Long,
    val silentCount: Int,
    val freeBytes: Long,
) {
    /** Warn when free space would allow less than about an hour of recording at 64 kbit/s. */
    val lowSpace: Boolean get() = freeBytes in 0 until LOW_SPACE_BYTES

    companion object { const val LOW_SPACE_BYTES = 64_000L / 8 * 3600 }
}

object RecordingCatalog {

    fun filter(items: List<RecordingMeta>, query: String): List<RecordingMeta> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        val digits = q.filter { it.isDigit() }
        return items.filter { m ->
            m.displayTitle.lowercase().contains(q) ||
                (m.contactName?.lowercase()?.contains(q) == true) ||
                (digits.isNotEmpty() && PhoneNumbers.normalize(m.number).contains(digits))
        }
    }

    fun sort(items: List<RecordingMeta>, order: SortOrder): List<RecordingMeta> = when (order) {
        SortOrder.NEWEST -> items.sortedByDescending { it.startedAtMillis }
        SortOrder.OLDEST -> items.sortedBy { it.startedAtMillis }
        SortOrder.LONGEST -> items.sortedWith(compareByDescending<RecordingMeta> { it.durationMillis }.thenByDescending { it.startedAtMillis })
        SortOrder.LARGEST -> items.sortedWith(compareByDescending<RecordingMeta> { it.sizeBytes }.thenByDescending { it.startedAtMillis })
    }

    /** Groups already-sorted items, keeping their order inside each group. */
    fun group(items: List<RecordingMeta>, by: GroupBy, zone: ZoneId, today: LocalDate): List<RecordingGroup> = when (by) {
        GroupBy.NONE -> if (items.isEmpty()) emptyList() else listOf(RecordingGroup(GroupKey.All, items))
        GroupBy.DATE -> items.groupBy { Instant.ofEpochMilli(it.startedAtMillis).atZone(zone).toLocalDate() }
            .map { (date, list) ->
                val rel = when (date) {
                    today -> RelativeDay.TODAY
                    today.minusDays(1) -> RelativeDay.YESTERDAY
                    else -> RelativeDay.OTHER
                }
                RecordingGroup(GroupKey.Day(date, rel), list)
            }
        GroupBy.CONTACT -> items.groupBy { personKey(it) }
            .map { (key, list) -> RecordingGroup(GroupKey.Person(list.first().contactName?.takeIf { it.isNotBlank() } ?: list.first().number.ifBlank { "Unknown number" }), list) }
            .sortedBy { (it.key as GroupKey.Person).label.lowercase() }
    }

    fun summary(items: List<RecordingMeta>, totalBytesOnDisk: Long, freeBytes: Long) = StorageSummary(
        count = items.size,
        totalBytes = totalBytesOnDisk,
        totalDurationMillis = items.sumOf { it.durationMillis },
        silentCount = items.count { it.audibleMillis == 0L },
        freeBytes = freeBytes,
    )

    /** Recordings older than [days] days, for the "delete older than" storage action. */
    fun olderThan(items: List<RecordingMeta>, days: Long, nowMillis: Long): List<RecordingMeta> {
        val cutoff = nowMillis - days * 24 * 3600 * 1000
        return items.filter { it.startedAtMillis < cutoff }
    }

    private fun personKey(m: RecordingMeta): String =
        m.contactName?.takeIf { it.isNotBlank() }?.lowercase()
            ?: m.number.filter { it.isDigit() }.takeLast(9).ifEmpty { "unknown" }
}

object RenameRules {
    const val MAX_LENGTH = 80

    sealed interface Result {
        data class Valid(val title: String?) : Result
        data object TooLong : Result
    }

    /** Blank resets to the default title. Control characters are stripped. */
    fun validate(input: String): Result {
        val cleaned = input.filterNot { it.isISOControl() }.trim()
        return when {
            cleaned.isEmpty() -> Result.Valid(null)
            cleaned.length > MAX_LENGTH -> Result.TooLong
            else -> Result.Valid(cleaned)
        }
    }
}
