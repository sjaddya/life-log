package com.example.lifelog.recording

import android.media.MediaPlayer

// Minimal playback wrapper for reviewing a recorded entry. One player at a
// time; callers stop it when the screen leaves composition.
class AudioPlayer {
    private var player: MediaPlayer? = null

    val isPlaying: Boolean
        get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    // Returns true if playback started. onComplete fires on the main thread
    // when the clip finishes on its own.
    fun start(path: String, onComplete: () -> Unit): Boolean {
        stop()
        return runCatching {
            player = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener {
                    onComplete()
                    stop()
                }
                prepare()
                start()
            }
            true
        }.getOrDefault(false)
    }

    fun stop() {
        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            runCatching { it.release() }
        }
        player = null
    }
}
