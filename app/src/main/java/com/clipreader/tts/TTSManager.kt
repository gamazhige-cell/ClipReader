package com.clipreader.tts

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.clipreader.util.PrefsManager
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream

class TTSManager(
    private val context: Context,
    private val client: OkHttpClient,
    private val onStateChange: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    private var currentEngine: TTSEngine? = null
    var isPlaying = false
        private set

    private fun getEngine(engineName: String, onStart: () -> Unit, onDone: () -> Unit, onError: (String) -> Unit): TTSEngine {
        return when (engineName) {
            "Microsoft Azure TTS" -> AzureTTSEngine(
                client = client,
                onAudioData = { mp3Bytes -> playMp3Bytes(mp3Bytes) },
                onEnd = { /* Handled by MediaPlayer */ },
                onError = { msg -> onError(msg) }
            )
            "Doubao App" -> DoubaoAppEngine(
                context = context,
                onStart = onStart,
                onDone = onDone,
                onError = onError
            )
            else -> SystemTTSEngine(
                context = context,
                onStart = onStart,
                onDone = onDone,
                onError = onError
            )
        }
    }

    fun play(text: String) {
        stop()
        val prefs = PrefsManager(context)
        val primary = prefs.getPrimaryEngine() ?: "System TTS (本机离线)"
        
        Log.d("TTSManager", "Playing with primary engine: $primary")
        playWithEngine(primary, text, isPrimary = true)
    }

    fun testEngine(engineName: String, text: String) {
        stop()
        Log.d("TTSManager", "Testing engine: $engineName")
        playWithEngine(engineName, text, isPrimary = false)
    }

    private fun playWithEngine(engineName: String, text: String, isPrimary: Boolean) {
        isPlaying = true
        onStateChange(true)

        currentEngine = getEngine(
            engineName = engineName,
            onStart = {
                isPlaying = true
                onStateChange(true)
            },
            onDone = {
                isPlaying = false
                onStateChange(false)
            },
            onError = { msg ->
                Log.e("TTSManager", "Engine $engineName failed: $msg")
                if (isPrimary) {
                    checkFallbackAndPlay(text)
                } else {
                    stop()
                    onError("引擎测试失败: $msg")
                }
            }
        )
        currentEngine?.play(text)
    }

    private fun playMp3Bytes(mp3Bytes: ByteArray) {
        try {
            val tempFile = File(context.cacheDir, "tts_audio.mp3")
            FileOutputStream(tempFile).use { it.write(mp3Bytes) }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    mediaPlayer?.release()
                    val mp = MediaPlayer()
                    mediaPlayer = mp
                    mp.setDataSource(tempFile.absolutePath)
                    mp.setOnPreparedListener { player ->
                        player.start()
                    }
                    mp.setOnCompletionListener {
                        isPlaying = false
                        onStateChange(false)
                    }
                    mp.setOnErrorListener { _, what, extra ->
                        onError("音频播放失败 ($what)")
                        isPlaying = false
                        onStateChange(false)
                        true
                    }
                    mp.prepareAsync()
                } catch (e: Exception) {
                    onError("创建播放器失败: ${e.message}")
                    isPlaying = false
                    onStateChange(false)
                }
            }
        } catch (e: Exception) {
            onError("保存音频失败: ${e.message}")
            isPlaying = false
            onStateChange(false)
        }
    }

    private var mediaPlayer: MediaPlayer? = null

    private fun checkFallbackAndPlay(text: String) {
        val prefs = PrefsManager(context)
        val fallback = prefs.getFallbackEngine() ?: "System TTS (本机离线)"

        Log.d("TTSManager", "Falling back to: $fallback")
        stopEnginesOnly()
        playWithEngine(fallback, text, isPrimary = false)
    }

    private fun stopEnginesOnly() {
        currentEngine?.stop()
        currentEngine?.release()
        currentEngine = null
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun updatePlaybackState(isPlaying: Boolean) {
        this.isPlaying = isPlaying
        onStateChange(isPlaying)
    }

    fun stop() {
        stopEnginesOnly()
        isPlaying = false
        onStateChange(false)
    }

    fun release() {
        stop()
    }
}
