package com.example.lifelog.recording

import android.content.Context
import android.media.MediaRecorder
import java.io.File

sealed class AudioStart {
    data class Success(val path: String) : AudioStart()
    data class Failure(val reason: String) : AudioStart()
}

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var activeFile: File? = null

    val amplitude: Int
        get() = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    fun start(entryId: String): AudioStart {
        stop()
        val directory = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(directory, "$entryId-${System.currentTimeMillis()}.m4a")
        activeFile = file

        return try {
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            AudioStart.Success(file.absolutePath)
        } catch (t: Throwable) {
            runCatching { recorder?.release() }
            recorder = null
            runCatching { file.delete() }
            activeFile = null
            AudioStart.Failure(t.message ?: t.javaClass.simpleName)
        }
    }

    fun stop(): String? {
        val path = activeFile?.absolutePath
        recorder?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        recorder = null
        activeFile = null
        return path
    }

    fun cancel() {
        val file = activeFile
        stop()
        runCatching { file?.delete() }
    }
}
