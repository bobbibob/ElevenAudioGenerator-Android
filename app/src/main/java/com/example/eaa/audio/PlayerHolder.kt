package com.example.eaa.audio

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Singleton-плеер на базе MediaPlayer. Один на всё приложение.
 *
 * Для UI прогресса Compose читает [durationMs] / [positionMs] / [isPlaying]
 * (это `State<…>` — чтение `.value` подписывается на рекомпозицию).
 * Чтобы они «тикали», в @Composable-контексте надо вызвать
 * [rememberPlayerProgress] — она запускает лёгкий таймер.
 */
object PlayerHolder {
    private const val TAG = "ElevenAudioGen.Player"
    private var player: MediaPlayer? = null
    private var currentPath: String? = null

    private val _durationMs = mutableStateOf(0)
    private val _positionMs = mutableStateOf(0)
    private val _isPlaying = mutableStateOf(false)
    private val _isPrepared = mutableStateOf(false)
    private val _revision = mutableStateOf(0)

    val revision: State<Int> get() = _revision
    val durationMs: State<Int> get() = _durationMs
    val positionMs: State<Int> get() = _positionMs
    val isPlaying: State<Boolean> get() = _isPlaying
    val isPrepared: State<Boolean> get() = _isPrepared

    fun current(): String? = currentPath
    fun isPlaying(): Boolean = _isPlaying.value
    fun duration(): Int = _durationMs.value
    fun position(): Int = _positionMs.value

    fun toggle(
        file: File,
        seekToMs: Int = 0,
        onPrepared: (() -> Unit)? = null,
        onCompletion: (() -> Unit)? = null
    ) {
        val path = file.absolutePath
        val current = player
        if (current != null && currentPath == path) {
            if (_isPlaying.value) {
                current.pause()
                _isPlaying.value = false
            } else {
                if (_isPrepared.value) current.start()
                _isPlaying.value = true
            }
            _revision.value++
            return
        }
        release()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(path)
                setOnPreparedListener {
                    _isPrepared.value = true
                    if (seekToMs > 0) it.seekTo(seekToMs)
                    _durationMs.value = it.duration.coerceAtLeast(0)
                    _positionMs.value = seekToMs.coerceAtMost(_durationMs.value)
                    it.start()
                    _isPlaying.value = true
                    _revision.value++
                    onPrepared?.invoke()
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _positionMs.value = _durationMs.value
                    _revision.value++
                    onCompletion?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    true
                }
                prepareAsync()
            }
            player = mp
            currentPath = path
            _isPrepared.value = false
            _durationMs.value = 0
            _positionMs.value = 0
            _isPlaying.value = false
            _revision.value++
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start playback: ${t.message}", t)
        }
    }

    fun stop() {
        try { player?.stop() } catch (_: Throwable) {}
        release()
    }

    fun seekTo(ms: Int) {
        val p = player ?: return
        if (!_isPrepared.value) return
        val clamped = ms.coerceIn(0, _durationMs.value)
        p.seekTo(clamped)
        _positionMs.value = clamped
        _revision.value++
    }

    /**
     * Тикает позицию из MediaPlayer в наши State-переменные.
     * Вызывается из UI-таймера, пока играет этот трек.
     */
    fun tickPosition() {
        val p = player ?: return
        if (!_isPrepared.value) return
        try {
            _positionMs.value = p.currentPosition
        } catch (_: Throwable) {}
    }

    private fun release() {
        try { player?.release() } catch (_: Throwable) {}
        player = null
        currentPath = null
        _isPrepared.value = false
        _isPlaying.value = false
        _durationMs.value = 0
        _positionMs.value = 0
        _revision.value++
    }
}

/**
 * Подписка на прогресс текущего плеера. Пока [path] совпадает с играющим
 * треком, ~4 раза в секунду читает MediaPlayer.currentPosition.
 * Ничего не возвращает — UI читает `PlayerHolder.positionMs.value` напрямую.
 */
@Composable
fun rememberPlayerProgress(path: String?) {
    DisposableEffect(path) {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
        val job = scope.launch {
            while (true) {
                if (PlayerHolder.current() == path && PlayerHolder.isPlaying()) {
                    PlayerHolder.tickPosition()
                }
                delay(250)
            }
        }
        onDispose { job.cancel() }
    }
    // Подписка на изменение позиции (на случай seekTo / паузы / завершения)
    @Suppress("UNUSED_VARIABLE")
    val r = PlayerHolder.revision.value
}
