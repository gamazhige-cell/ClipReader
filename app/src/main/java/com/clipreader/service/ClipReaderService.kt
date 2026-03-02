package com.clipreader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.clipreader.clipboard.ClipboardMonitor
import com.clipreader.overlay.OverlayManager
import com.clipreader.tts.TTSManager
import okhttp3.OkHttpClient

class ClipReaderService : Service() {
    companion object {
        const val CHANNEL_ID = "ClipReaderServiceChannel"
        const val NOTIFICATION_ID = 1
        var isRunning = false
            private set
    }

    private var clipboardMonitor: ClipboardMonitor? = null
    private var overlayManager: OverlayManager? = null
    private var ttsManager: TTSManager? = null
    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        overlayManager = OverlayManager(
            this,
            onPlayPause = {
                if (ttsManager?.isPlaying == true) {
                    ttsManager?.stop()
                } else {
                    val service = com.clipreader.clipboard.AutoCopierService.instance
                    if (service != null) {
                        service.tryCopyAndPlay()
                    } else {
                        val actIntent = android.content.Intent(this, com.clipreader.clipboard.ClipboardReaderActivity::class.java).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        }
                        startActivity(actIntent)
                    }
                }
            },
            onVoiceInput = {
                com.clipreader.clipboard.AutoCopierService.instance?.tryVoiceInput(overlayManager)
            },
            onStopService = {
                stopSelf()
            }
        )

        ttsManager = TTSManager(
            context = this,
            client = client,
            onStateChange = { isPlaying ->
                mainHandler.post {
                    if (isPlaying) {
                        overlayManager?.setState(OverlayManager.State.PLAYING)
                    } else {
                        overlayManager?.setState(OverlayManager.State.IDLE)
                    }
                }
            },
            onError = { msg ->
                mainHandler.post {
                    overlayManager?.setState(OverlayManager.State.ERROR)
                    Toast.makeText(this@ClipReaderService, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )

        clipboardMonitor = ClipboardMonitor(
            context = this,
            onNewText = { text ->
                mainHandler.post {
                    overlayManager?.setState(OverlayManager.State.LOADING)
                    ttsManager?.play(text)
                }
            }
        )

        overlayManager?.show()
        clipboardMonitor?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_TEST_ENGINE") {
            val engineName = intent.getStringExtra("ENGINE_NAME") ?: "System TTS"
            ttsManager?.testEngine(engineName, "这是一条测试语音。如果能听到，说明您刚才点击的选择引擎运作正常。")
        } else if (intent?.action == "ACTION_PLAY_TEXT") {
            val textToPlay = intent.getStringExtra("TEXT_TO_PLAY")
            if (!textToPlay.isNullOrEmpty()) {
                ttsManager?.play(textToPlay)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        clipboardMonitor?.stop()
        ttsManager?.stop()
        overlayManager?.remove()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ClipReader Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ClipReader is running")
            .setContentText("Listening to clipboard...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }
}
