package app.minimaldialer.core.model

enum class CallDirection { INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED, VOICEMAIL, UNKNOWN }

/** One row of the system call log. */
data class RecentCall(
    val id: Long,
    val number: String,
    val cachedName: String?,
    val direction: CallDirection,
    val timestampMillis: Long,
    val durationSeconds: Long,
)
