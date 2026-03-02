package com.clipreader.tts

import android.content.Context
import android.content.Intent
import android.util.Log

class DoubaoAppEngine(
    private val context: Context,
    private val speed: Float = 1.0f,
    private val voice: String? = null,
    private val onStart: () -> Unit = {},
    private val onDone: () -> Unit = {},
    private val onError: (String) -> Unit = {}
) : TTSEngine {

    override fun play(text: String) {
        val packageName = getDoubaoPackage()
        if (packageName == null) {
            Log.e("DoubaoAppEngine", "Doubao App not found (checked com.doubao.app and com.larus.nova)")
            onError("未找到豆包App，请确认已安装")
            return
        }

        try {
            val intent = Intent("com.doubao.action.TTS_PLAY").apply {
                setPackage(packageName)
                putExtra("tts_text", text)
                putExtra("tts_speed", speed)
                voice?.let { putExtra("tts_voice", it) }
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.startService(intent)
            onStart()
            Log.d("DoubaoAppEngine", "Sent TTS_PLAY intent to $packageName")
        } catch (e: Exception) {
            Log.e("DoubaoAppEngine", "Failed to start Doubao TTS: ${e.message}")
            onError("启动豆包TTS播放失败，可能该版本不支持此接口")
        }
    }

    private fun getDoubaoPackage(): String? {
        val packages = listOf("com.doubao.app", "com.larus.nova")
        val pm = context.packageManager
        for (pkg in packages) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (e: Exception) {
                // Not found
            }
        }
        return null
    }

    override fun stop() {
        try {
            val packageName = getDoubaoPackage() ?: return
            val intent = Intent("com.doubao.action.TTS_STOP").apply {
                setPackage(packageName)
            }
            context.startService(intent)
            onDone()
        } catch (e: Exception) {
            Log.e("DoubaoAppEngine", "Failed to stop Doubao TTS")
        }
    }

    override fun release() {
        stop()
    }
}
