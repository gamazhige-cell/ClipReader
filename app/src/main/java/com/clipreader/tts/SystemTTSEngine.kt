package com.clipreader.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

class SystemTTSEngine(
    context: Context,
    private val onStart: () -> Unit,
    private val onDone: () -> Unit,
    private val onError: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.CHINESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                onError("Chinese language is not supported or missing data on this system.")
            } else {
                isInitialized = true
                setupListener()
                pendingText?.let {
                    synthesize(it)
                    pendingText = null
                }
            }
        } else {
            onError("Initialization of TextToSpeech failed.")
        }
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onStart()
            }

            override fun onDone(utteranceId: String?) {
                onDone()
            }

            override fun onError(utteranceId: String?) {
                onError("TTS playback error occurred.")
            }
        })
    }

    fun synthesize(text: String) {
        if (!isInitialized) {
            pendingText = text
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        
        // Use QUEUE_FLUSH to interrupt any ongoing playback
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        tts?.stop()
    }

    fun close() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
