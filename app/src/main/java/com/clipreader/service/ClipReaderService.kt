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
import android.util.Log
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

    private val doubaoReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val action = intent?.action ?: return
            val status = intent.getStringExtra("status") // START / PAUSE / COMPLETE / ERROR
            val text = intent.getStringExtra("text") ?: ""
            
            Log.d("ClipReaderService", "Doubao Broadcast: Action=$action, Status=$status, Text=$text")

            if (action == "com.doubao.broadcast.ASR_STATUS") {
                if (status == "COMPLETE") {
                    com.clipreader.clipboard.AutoCopierService.instance?.handleDoubaoAsrResult(text)
                }
            } else if (action == "com.doubao.broadcast.TTS_STATUS") {
                when (status) {
                    "START" -> ttsManager?.updatePlaybackState(true)
                    "COMPLETE", "ERROR" -> ttsManager?.updatePlaybackState(false)
                }
            } else if (action == "com.clipreader.ACTION_SHARE_TO_DOUBAO") {
                shareToDoubao()
            }
        }
    }

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
            onShare = {
                val service = com.clipreader.clipboard.AutoCopierService.instance
                if (service != null) {
                    service.tryCopyAndShare()
                } else {
                    shareToDoubao()
                }
            },
            onBack = {
                val service = com.clipreader.clipboard.AutoCopierService.instance
                if (service != null) {
                    // This is an accessibility service global action
                    service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                }
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

        val filter = android.content.IntentFilter().apply {
            addAction("com.doubao.broadcast.TTS_STATUS")
            addAction("com.doubao.broadcast.ASR_STATUS")
            addAction("com.clipreader.ACTION_SHARE_TO_DOUBAO")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(doubaoReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(doubaoReceiver, filter)
        }
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
        } else if (intent?.action == "ACTION_SHARE_TEXT") {
            val textToShare = intent.getStringExtra("TEXT_TO_SHARE")
            if (!textToShare.isNullOrEmpty()) {
                shareToDoubao(textToShare)
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
        try {
            unregisterReceiver(doubaoReceiver)
        } catch (_: Exception) {}
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

    private fun shareToDoubao(providedText: String? = null) {
        val text = if (providedText != null) {
            providedText
        } else {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                clipData.getItemAt(0).text?.toString() ?: ""
            } else ""
        }

        if (text.isEmpty()) {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show()
            return
        }

        val prompt = "将以下内容原文输出：\n\n"
        val shareText = prompt + text

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Try to target Doubao directly if possible
        val packages = listOf("com.larus.nova", "com.doubao.app")
        var targeted = false
        for (pkg in packages) {
            try {
                packageManager.getPackageInfo(pkg, 0)
                sendIntent.setPackage(pkg)
                targeted = true
                break
            } catch (e: Exception) {}
        }

        try {
            if (targeted) {
                startActivity(sendIntent)
            } else {
                val shareIntent = Intent.createChooser(sendIntent, "分享到豆包")
                shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(shareIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "无法启动分享: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
