package com.clipreader.tts

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.clipreader.util.PrefsManager
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.io.File
import java.io.FileOutputStream

class TTSManager(
    private val context: Context,
    private val client: OkHttpClient,
    private val onStateChange: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val AZURE_ENGINE_NAME = "Microsoft Azure TTS"
        private const val AZURE_CACHE_FILE_PREFIX = "tts_cache_"
        private const val AZURE_CACHE_FILE_SUFFIX = ".mp3"
        private const val MAX_AZURE_CACHE_FILES = 50
        private const val MAX_AZURE_CACHE_BYTES = 100L * 1024L * 1024L
    }

    private var currentEngine: TTSEngine? = null
    var isPlaying = false
        private set

    private fun getEngine(
        engineName: String,
        onStart: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
        azureCacheKey: String?
    ): TTSEngine {
        return when (engineName) {
            AZURE_ENGINE_NAME -> {
                val prefs = PrefsManager(context)
                AzureTTSEngine(
                    client = client,
                    apiKey = prefs.getAzureKey() ?: "",
                    region = prefs.getAzureRegion() ?: "eastasia",
                    onAudioData = { mp3Bytes -> playMp3Bytes(mp3Bytes, azureCacheKey) },
                    onEnd = { /* Handled by MediaPlayer */ },
                    onError = { msg -> onError(msg) }
                )
            }
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
        val prefs = PrefsManager(context)
        val azureRegion = prefs.getAzureRegion() ?: "eastasia"
        val azureCacheKey = if (engineName == AZURE_ENGINE_NAME) buildAzureCacheKey(text, azureRegion) else null
        if (azureCacheKey != null) {
            val cached = getAzureCacheFile(azureCacheKey)
            if (cached.exists() && cached.length() > 1024L) {
                Log.d("TTSManager", "Reusing cached Azure MP3 for current text")
                playMp3File(cached)
                return
            } else if (cached.exists()) {
                cached.delete()
            }
        }

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
            },
            azureCacheKey = azureCacheKey
        )
        currentEngine?.play(text)
    }

    private fun playMp3Bytes(mp3Bytes: ByteArray, cacheKey: String?) {
        try {
            val targetFile = if (cacheKey != null) {
                val finalFile = getAzureCacheFile(cacheKey)
                val tempFile = File(context.cacheDir, "${finalFile.name}.tmp")
                FileOutputStream(tempFile).use { it.write(mp3Bytes) }
                if (finalFile.exists()) {
                    finalFile.delete()
                }
                if (!tempFile.renameTo(finalFile)) {
                    throw IllegalStateException("无法原子写入缓存文件")
                }
                enforceAzureCacheLimits()
                finalFile
            } else {
                val tempFile = File(context.cacheDir, "tts_audio.mp3.tmp")
                val finalFile = File(context.cacheDir, "tts_audio.mp3")
                FileOutputStream(tempFile).use { it.write(mp3Bytes) }
                if (finalFile.exists()) {
                    finalFile.delete()
                }
                if (!tempFile.renameTo(finalFile)) {
                    throw IllegalStateException("无法写入临时播放文件")
                }
                finalFile
            }
            playMp3File(targetFile)
        } catch (e: Exception) {
            onError("保存音频失败: ${e.message}")
            isPlaying = false
            onStateChange(false)
        }
    }

    private fun playMp3File(file: File) {
        isPlaying = true
        onStateChange(true)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                mediaPlayer?.release()
                val mp = MediaPlayer()
                mediaPlayer = mp
                mp.setDataSource(file.absolutePath)
                mp.setOnPreparedListener { player ->
                    player.start()
                }
                mp.setOnCompletionListener {
                    isPlaying = false
                    onStateChange(false)
                }
                mp.setOnErrorListener { _, what, _ ->
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
    }

    private var mediaPlayer: MediaPlayer? = null

    private fun checkFallbackAndPlay(text: String) {
        val prefs = PrefsManager(context)
        val fallback = prefs.getFallbackEngine() ?: "System TTS (本机离线)"

        Log.d("TTSManager", "Falling back to: $fallback")
        stopEnginesOnly()
        playWithEngine(fallback, text, isPrimary = false)
    }

    private fun buildAzureCacheKey(text: String, azureRegion: String): String {
        val normalized = text
            .replace("\r\n", "\n")
            .replace(Regex("\\s+"), " ")
            .trim()
        val keyMaterial = listOf(
            AZURE_ENGINE_NAME,
            "zh-CN-XiaoxiaoNeural",
            "audio-24khz-48kbitrate-mono-mp3",
            azureRegion,
            normalized
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(keyMaterial.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun getAzureCacheFile(cacheKey: String): File {
        return File(context.cacheDir, "${AZURE_CACHE_FILE_PREFIX}$cacheKey$AZURE_CACHE_FILE_SUFFIX")
    }

    private fun enforceAzureCacheLimits() {
        val files = context.cacheDir.listFiles { file ->
            file.isFile && file.name.startsWith(AZURE_CACHE_FILE_PREFIX) && file.name.endsWith(AZURE_CACHE_FILE_SUFFIX)
        }?.toMutableList() ?: return

        var totalBytes = files.sumOf { it.length() }
        if (files.size <= MAX_AZURE_CACHE_FILES && totalBytes <= MAX_AZURE_CACHE_BYTES) {
            return
        }

        files.sortBy { it.lastModified() }
        var index = 0
        while ((files.size - index) > MAX_AZURE_CACHE_FILES || totalBytes > MAX_AZURE_CACHE_BYTES) {
            if (index >= files.size) {
                break
            }
            val fileToDelete = files[index]
            val size = fileToDelete.length()
            if (fileToDelete.delete()) {
                totalBytes -= size
            }
            index += 1
        }
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
