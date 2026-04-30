package com.example.lifelog.recording

import android.content.Context
import android.media.MediaRecorder
import java.io.File

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var activeFile: File? = null

    val amplitude: Int
        get() = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    fun start(entryId: String): String {
        stop()
        val directory = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(directory, "$entryId-${System.currentTimeMillis()}.m4a")
        activeFile = file

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        return file.absolutePath
    }

    fun stop(): String? {
        val path = activeFile?.absolutePath
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
        activeFile = null
        return path
    }

    fun cancel() {
        val file = activeFile
        stop()
        file?.delete()
    }
}
