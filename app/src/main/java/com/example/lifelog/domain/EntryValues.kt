package com.example.lifelog.domain

object EntryStatus {
    const val Pending = "pending"
    const val Completed = "completed"
    const val Skipped = "skipped"
    const val Missed = "missed"
    const val Backfilled = "backfilled"
}

object EntrySource {
    const val Manual = "manual"
    const val VoiceRecording = "voice_recording"
}

val SupportedIntervals = listOf(15, 30, 60, 120)
