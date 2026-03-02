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
    private var systemEngine: SystemTTSEngine? = null
    private var azureEngine: AzureTTSEngine? = null
    private var mediaPlayer: MediaPlayer? = null

    var isPlaying = false

    private var usingSystemTts = false

    init {
        systemEngine = SystemTTSEngine(
            context,
            onStart = {
                isPlaying = true
                onStateChange(true)
            },
            onDone = {
                isPlaying = false
                onStateChange(false)
            },
            onError = { msg ->
                onError("System TTS Error: $msg")
                stop()
            }
        )
    }

    fun play(text: String) {
        stop()
        isPlaying = true
        onStateChange(true)
        usingSystemTts = false

        val prefs = PrefsManager(context)
        val primary = prefs.getPrimaryEngine()

        if (primary == "Microsoft Azure TTS") {
            playAzureTTS(text, isPrimary = true)
        } else {
            playSystemTTS(text, isPrimary = true)
        }
    }

    fun testEngine(engineName: String, text: String) {
        stop()
        isPlaying = true
        onStateChange(true)
        usingSystemTts = false

        if (engineName == "Microsoft Azure TTS") {
            playAzureTTS(text, isPrimary = false)
        } else {
            playSystemTTS(text, isPrimary = false)
        }
    }

    private fun playAzureTTS(text: String, isPrimary: Boolean) {
        azureEngine = AzureTTSEngine(
            client = client,
            onAudioData = { mp3Bytes ->
                // Write MP3 to temp file and play with MediaPlayer
                playMp3Bytes(mp3Bytes)
            },
            onEnd = {
                // mediaPlayer completion listener handles state change
            },
            onError = { errorMsg ->
                Log.e("TTSManager", "Azure TTS failed: $errorMsg")
                if (isPrimary) {
                    checkFallbackAndPlay(text)
                } else {
                    stop()
                    onError("Azure TTS 备用引擎也失败了")
                }
            }
        )
        azureEngine?.synthesize(text)
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
                        Log.d("TTSManager", "MediaPlayer prepared, starting playback")
                        player.start()
                    }
                    mp.setOnCompletionListener {
                        Log.d("TTSManager", "MediaPlayer finished")
                        isPlaying = false
                        onStateChange(false)
                    }
                    mp.setOnErrorListener { _, what, extra ->
                        Log.e("TTSManager", "MediaPlayer error: what=$what extra=$extra")
                        onError("音频播放失败 ($what)")
                        isPlaying = false
                        onStateChange(false)
                        true
                    }
                    mp.prepareAsync()
                } catch (e: Exception) {
                    Log.e("TTSManager", "Error creating MediaPlayer: ${e.message}")
                    onError("创建播放器失败: ${e.message}")
                    isPlaying = false
                    onStateChange(false)
                }
            }
        } catch (e: Exception) {
            Log.e("TTSManager", "Error writing temp MP3: ${e.message}")
            onError("保存音频失败: ${e.message}")
            isPlaying = false
            onStateChange(false)
        }
    }

    private fun playSystemTTS(text: String, isPrimary: Boolean) {
        usingSystemTts = true
        systemEngine?.synthesize(text)
    }

    private fun checkFallbackAndPlay(text: String) {
        val prefs = PrefsManager(context)
        val fallback = prefs.getFallbackEngine()

        Log.d("TTSManager", "Primary failed, falling back to: $fallback")
        stopEnginesOnly()

        isPlaying = true
        onStateChange(true)

        if (fallback == "Microsoft Azure TTS") {
            playAzureTTS(text, isPrimary = false)
        } else {
            playSystemTTS(text, isPrimary = false)
        }
    }

    private fun stopEnginesOnly() {
        azureEngine?.close()
        azureEngine = null
        mediaPlayer?.release()
        mediaPlayer = null
        systemEngine?.stop()
    }

    fun stop() {
        azureEngine?.close()
        azureEngine = null
        safeReleaseMediaPlayer()
        systemEngine?.stop()
        isPlaying = false
        onStateChange(false)
    }

    private fun safeReleaseMediaPlayer() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val mp = mediaPlayer
            mediaPlayer = null
            if (mp != null) {
                try { mp.stop() } catch (_: Exception) {}
                try { mp.reset() } catch (_: Exception) {}
                try { mp.release() } catch (_: Exception) {}
            }
        }
    }

    fun release() {
        stop()
        systemEngine?.close()
        systemEngine = null
    }
}
