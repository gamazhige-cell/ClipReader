package com.clipreader.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

class AudioPlayerHelper(
    private val sampleRate: Int = 24000
) {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    fun initAndPlay() {
        if (audioTrack != null) {
            stopAndRelease()
        }
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        
        isPlaying = false // Important: Do not `.play()` until we write actual bytes to prevent underrun pop/static!
    }

    fun writeAudioData(data: ByteArray) {
        if (audioTrack != null) {
            if (!isPlaying) {
                audioTrack?.play()
                isPlaying = true
            }
            audioTrack?.write(data, 0, data.size)
        }
    }

    fun pause() {
        if (isPlaying) {
            audioTrack?.pause()
            isPlaying = false
        }
    }

    fun resume() {
        if (!isPlaying && audioTrack != null) {
            audioTrack?.play()
            isPlaying = true
        }
    }

    fun stopAndRelease() {
        isPlaying = false
        audioTrack?.let {
            if (it.state == AudioTrack.STATE_INITIALIZED) {
                try {
                    it.stop()
                } catch (e: Exception) {
                    Log.w("AudioPlayerHelper", "AudioTrack stop failed", e)
                }
            }
            it.release()
        }
        audioTrack = null
    }
}
