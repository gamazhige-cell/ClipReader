package com.clipreader.tts

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException

class AzureTTSEngine(
    private val client: OkHttpClient,
    private val voice: String = "zh-CN-XiaoxiaoNeural",
    private val apiKey: String = com.clipreader.BuildConfig.AZURE_API_KEY,
    private val region: String = com.clipreader.BuildConfig.AZURE_REGION,
    private val onAudioData: (ByteArray) -> Unit,
    private val onEnd: () -> Unit,
    private val onError: (String) -> Unit
) : TTSEngine {
    private var currentCall: Call? = null

    private fun mkssml(text: String): String {
        val escapedText = text.replace("&", "&amp;")
                              .replace("<", "&lt;")
                              .replace(">", "&gt;")
        return """
            <speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>
                <voice name='$voice'>
                    <prosody pitch='+0Hz' rate='+0%' volume='+0%'>
                        $escapedText
                    </prosody>
                </voice>
            </speak>
        """.trimIndent()
    }

    override fun play(text: String) {
        synthesize(text)
    }

    fun synthesize(text: String) {
        val ttsUrl = "https://$region.tts.speech.microsoft.com/cognitiveservices/v1"
        val ssml = mkssml(text)

        val requestBody = ssml.toRequestBody("application/ssml+xml".toMediaType())

        val request = Request.Builder()
            .url(ttsUrl)
            .post(requestBody)
            .addHeader("Ocp-Apim-Subscription-Key", apiKey)
            // Use MP3 format — much more robust for streaming than raw PCM
            .addHeader("X-Microsoft-OutputFormat", "audio-24khz-48kbitrate-mono-mp3")
            .addHeader("Content-Type", "application/ssml+xml")
            .addHeader("User-Agent", "ClipReader")
            .build()

        currentCall = client.newCall(request)
        currentCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    Log.e("AzureTTSEngine", "Request failed: ${e.message}")
                    onError("Azure TTS 请求失败: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body
                    if (body != null) {
                        try {
                            // Accumulate all MP3 bytes, then deliver as one chunk
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            val inputStream = body.byteStream()
                            var read: Int
                            while (inputStream.read(buffer).also { read = it } != -1) {
                                if (call.isCanceled()) break
                                output.write(buffer, 0, read)
                            }
                            if (!call.isCanceled()) {
                                val mp3Bytes = output.toByteArray()
                                Log.d("AzureTTSEngine", "Downloaded ${mp3Bytes.size} bytes of MP3 audio")
                                if (mp3Bytes.isNotEmpty()) {
                                    onAudioData(mp3Bytes)
                                }
                            }
                        } catch (e: Exception) {
                            if (!call.isCanceled()) {
                                Log.e("AzureTTSEngine", "Error reading audio stream", e)
                                onError("读取音频流失败: ${e.message}")
                            }
                        } finally {
                            body.close()
                            if (!call.isCanceled()) {
                                onEnd()
                            }
                        }
                    } else {
                        onError("Azure TTS 返回空体.")
                        onEnd()
                    }
                } else {
                    val errorBody = response.body?.string()
                    Log.e("AzureTTSEngine", "Request failed with code ${response.code}: $errorBody")
                    onError("Azure TTS 错误: HTTP ${response.code}")
                    onEnd()
                }
            }
        })
    }

    override fun stop() {
        currentCall?.cancel()
        currentCall = null
    }

    override fun release() {
        stop()
    }

    fun close() {
        stop()
    }
}
