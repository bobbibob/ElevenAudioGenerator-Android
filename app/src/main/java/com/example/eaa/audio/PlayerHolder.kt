package com.example.eaa.audio

import android.media.MediaPlayer
import android.util.Log
import java.io.File

/**
 * Простейший singleton-плеер на базе MediaPlayer.
 * Один экземпляр на всё приложение — он не зависит от Activity.
 */
object PlayerHolder {
    private const val TAG = "ElevenAudioGen.Player"
    private var player: MediaPlayer? = null
    private var currentPath: String? = null

    /** Текущий путь, который играет (или null). */
    fun current(): String? = currentPath

    fun isPlaying(): Boolean = player?.isPlaying == true

    /** Играть указанный файл. Если уже играет тот же — пауза/продолжение. */
    fun toggle(file: File, onPrepared: (() -> Unit)? = null, onCompletion: (() -> Unit)? = null) {
        val path = file.absolutePath
        val current = player
        if (current != null && currentPath == path) {
            if (current.isPlaying) current.pause() else current.start()
            return
        }
        release()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(path)
                setOnPreparedListener { it.start(); onPrepared?.invoke() }
                setOnCompletionListener { onCompletion?.invoke() }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    true
                }
                prepareAsync()
            }
            player = mp
            currentPath = path
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start playback: ${t.message}", t)
        }
    }

    fun stop() {
        try { player?.stop() } catch (_: Throwable) {}
        release()
    }

    private fun release() {
        try { player?.release() } catch (_: Throwable) {}
        player = null
        currentPath = null
    }
}
