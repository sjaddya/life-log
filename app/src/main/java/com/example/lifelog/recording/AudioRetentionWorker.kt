package com.example.lifelog.recording

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.lifelog.data.SettingsRepository
import com.example.lifelog.data.local.AppDatabase
import java.io.File

// Periodic cleanup of voice recordings older than the user's
// audioRetentionDays setting. Enqueued once from MainActivity.
class AudioRetentionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val retentionDays = SettingsRepository(ctx).settings.value.audioRetentionDays
        val cutoff = System.currentTimeMillis() -
            retentionDays.toLong() * 24 * 60 * 60 * 1000
        val dao = AppDatabase.getInstance(ctx).entryDao()

        // Expire DB references whose file is missing or past the cutoff.
        dao.entriesWithAudio().forEach { entry ->
            val path = entry.audioPath ?: return@forEach
            val file = File(path)
            if (!file.exists() || file.lastModified() < cutoff) {
                runCatching { file.delete() }
                dao.update(entry.copy(audioPath = null))
            }
        }

        // Sweep orphaned recording files no surviving entry references.
        val referenced = dao.entriesWithAudio().mapNotNull { it.audioPath }.toSet()
        File(ctx.filesDir, "recordings").listFiles()?.forEach { file ->
            if (file.absolutePath !in referenced && file.lastModified() < cutoff) {
                runCatching { file.delete() }
            }
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "audio-retention"
    }
}
