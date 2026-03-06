package com.clipreader.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.os.Handler
import android.os.Looper
import kotlin.math.abs

import android.view.LayoutInflater
import com.clipreader.R

class OverlayManager(
    private val context: Context,
    private val onPlayPause: () -> Unit,
    private val onVoiceInput: () -> Unit,
    private val onShare: () -> Unit,
    private val onBack: () -> Unit,
    private val onStopService: () -> Unit
) {
    private var windowManager: WindowManager? = null
    private var windowContext: Context? = null
    private var floatingView: View? = null

    private var iconBackground: View? = null
    private var iconImage: ImageView? = null
    private var micBackground: View? = null
    private var micImage: ImageView? = null
    private var shareBackground: View? = null
    private var shareImage: ImageView? = null
    private var backBackground: View? = null
    private var backImage: ImageView? = null

    private var cardPlay: View? = null
    private var cardShare: View? = null
    private var cardMic: View? = null
    private var cardBack: View? = null
    private var cardExit: View? = null

    private var params: WindowManager.LayoutParams? = null

    enum class NavigationMode { NORMAL, BACK_ONLY }
    private var currentNavigationMode = NavigationMode.NORMAL

    enum class State { IDLE, LOADING, PLAYING, ERROR }
    private var currentState = State.IDLE

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable {
        floatingView?.animate()?.alpha(0.3f)?.setDuration(300)?.start()
    }

    enum class MicState { IDLE, LISTENING }
    private var micState = MicState.IDLE

    fun show() {
        if (floatingView != null) return

        val layoutFlag: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val display = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            windowContext = context.createWindowContext(display, layoutFlag, null)
            windowManager = windowContext?.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE
            windowContext = context
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }

        val ctx = windowContext ?: context
        floatingView = LayoutInflater.from(ctx).inflate(R.layout.overlay_bubble, null)
        iconBackground = floatingView?.findViewById(R.id.iconBackground)
        iconImage = floatingView?.findViewById(R.id.iconImage)
        micBackground = floatingView?.findViewById(R.id.micBackground)
        micImage = floatingView?.findViewById(R.id.micImage)
        shareBackground = floatingView?.findViewById(R.id.shareBackground)
        shareImage = floatingView?.findViewById(R.id.shareImage)
        backBackground = floatingView?.findViewById(R.id.backBackground)
        backImage = floatingView?.findViewById(R.id.backImage)
        
        cardPlay = floatingView?.findViewById(R.id.cardPlay)
        cardShare = floatingView?.findViewById(R.id.cardShare)
        cardMic = floatingView?.findViewById(R.id.cardMic)
        cardBack = floatingView?.findViewById(R.id.cardBack)
        cardExit = floatingView?.findViewById(R.id.cardExit)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 300 + cmToPx(2.0f)
        }

        setupControlTouchHandlers()

        windowManager?.addView(floatingView, params)
        setState(State.IDLE)
        setMicState(MicState.IDLE)
        setNavigationMode(NavigationMode.NORMAL)
        
        // Start auto-hide timer
        resetHideTimer()
    }

    private fun resetHideTimer() {
        hideHandler.removeCallbacks(hideRunnable)
        floatingView?.alpha = 1.0f
        hideHandler.postDelayed(hideRunnable, 3000)
    }

    fun setNavigationMode(mode: NavigationMode) {
        currentNavigationMode = mode
        if (mode == NavigationMode.NORMAL) {
            cardPlay?.visibility = View.VISIBLE
            cardShare?.visibility = View.VISIBLE
            cardMic?.visibility = View.VISIBLE
            cardBack?.visibility = View.GONE
            cardExit?.visibility = View.VISIBLE
        } else {
            cardPlay?.visibility = View.GONE
            cardShare?.visibility = View.GONE
            cardMic?.visibility = View.GONE
            cardBack?.visibility = View.VISIBLE
            cardExit?.visibility = View.VISIBLE
        }
        resetHideTimer()
    }

    fun setState(state: State) {
        currentState = state
        
        // Manage auto-hide based on activity
        floatingView?.alpha = 1.0f
        if (state != State.IDLE) {
            hideHandler.removeCallbacks(hideRunnable)
        } else if (micState == MicState.IDLE) {
            resetHideTimer()
        }

        when (state) {
            State.IDLE -> {
                iconBackground?.apply {
                    setBackgroundResource(R.drawable.bg_floating_minimal)
                    alpha = 1.0f
                }
                iconImage?.setImageResource(android.R.drawable.ic_media_play)
                iconImage?.setColorFilter(Color.parseColor("#34C759")) // Green
            }
            State.LOADING -> {
                iconBackground?.apply {
                    setBackgroundResource(R.drawable.bg_floating_minimal)
                    alpha = 1.0f
                }
                iconImage?.setColorFilter(Color.parseColor("#FFCC00")) // Yellow
            }
            State.PLAYING -> {
                iconBackground?.apply {
                    setBackgroundResource(R.drawable.bg_floating_minimal)
                    alpha = 1.0f
                }
                iconImage?.setImageResource(android.R.drawable.ic_media_pause)
                iconImage?.setColorFilter(Color.parseColor("#34C759")) // Green
            }
            State.ERROR -> {
                iconBackground?.apply {
                    setBackgroundResource(R.drawable.bg_floating_minimal)
                    alpha = 1.0f
                }
                iconImage?.setColorFilter(Color.parseColor("#FF3B30")) // Red
            }
        }
    }

    fun setMicState(state: MicState) {
        micState = state
        
        // Manage auto-hide based on activity
        floatingView?.alpha = 1.0f
        if (state != MicState.IDLE) {
            hideHandler.removeCallbacks(hideRunnable)
        } else if (currentState == State.IDLE) {
            resetHideTimer()
        }

        when (state) {
            MicState.IDLE -> {
                micBackground?.apply {
                    setBackgroundResource(R.drawable.bg_floating_minimal)
                    alpha = 1.0f
                }
                micImage?.setImageResource(android.R.drawable.ic_btn_speak_now)
                micImage?.setColorFilter(Color.parseColor("#FF9500")) // Orange
            }
            MicState.LISTENING -> {
                micBackground?.apply {
                    setBackgroundResource(R.drawable.bg_floating_minimal)
                    alpha = 1.0f
                }
                micImage?.setImageResource(android.R.drawable.ic_btn_speak_now)
                micImage?.setColorFilter(Color.parseColor("#FF3B30")) // Red indicating recording
            }
        }
    }

    private fun setupControlTouchHandlers() {
        val targetActions = listOf(
            Pair(cardPlay, { onPlayPause() }),
            Pair(cardShare, { onShare() }),
            Pair(cardMic, { onVoiceInput() }),
            Pair(cardBack, { onBack() }),
            Pair(cardExit, { onStopService() })
        )

        for ((targetView, clickAction) in targetActions) {
            targetView?.setOnTouchListener(createDragAndClickListener(clickAction))
        }
    }

    private fun createDragAndClickListener(clickAction: () -> Unit): View.OnTouchListener {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    hideHandler.removeCallbacks(hideRunnable)
                    floatingView?.alpha = 1.0f

                    val safeParams = params ?: return@OnTouchListener false
                    initialX = safeParams.x
                    initialY = safeParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isClick = false
                    }

                    val safeParams = params ?: return@OnTouchListener false
                    safeParams.x = initialX + dx
                    safeParams.y = initialY + dy
                    windowManager?.updateViewLayout(floatingView, safeParams)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isClick) {
                        val safeParams = params ?: return@OnTouchListener false
                        val screenHeight = context.resources.displayMetrics.heightPixels
                        if (safeParams.y >= screenHeight - 300) {
                            onStopService()
                        }
                    } else {
                        clickAction()
                    }

                    if (currentState == State.IDLE && micState == MicState.IDLE) {
                        resetHideTimer()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun cmToPx(cm: Float): Int {
        val metrics = context.resources.displayMetrics
        return ((cm / 2.54f) * metrics.ydpi).toInt()
    }

    fun remove() {
        hideHandler.removeCallbacksAndMessages(null)
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.w("OverlayManager", "Failed to remove floating view cleanly", e)
            }
            floatingView = null
        }
    }
}
