package com.example.lifelog.domain

// Persisted as the `raw` string in the Room `status` / `source` columns via
// the Room TypeConverters — the on-disk representation is unchanged from when
// these were plain string constants, so no migration is needed.

enum class EntryStatus(val raw: String) {
    Pending("pending"),
    Completed("completed"),
    Skipped("skipped"),
    Missed("missed"),
    Backfilled("backfilled");

    companion object {
        // Unknown / legacy values fall back to Pending rather than crashing.
        fun fromRaw(raw: String): EntryStatus =
            values().firstOrNull { it.raw == raw } ?: Pending
    }
}

enum class EntrySource(val raw: String) {
    Manual("manual"),
    VoiceRecording("voice_recording");

    companion object {
        fun fromRaw(raw: String): EntrySource =
            values().firstOrNull { it.raw == raw } ?: Manual
    }
}

val SupportedIntervals = listOf(15, 30, 45, 60, 120)
