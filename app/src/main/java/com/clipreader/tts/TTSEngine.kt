package com.clipreader.tts

interface TTSEngine {
    fun play(text: String)
    fun stop()
    fun release()
}
