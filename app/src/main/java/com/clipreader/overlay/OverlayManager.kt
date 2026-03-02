package com.clipreader.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs

import android.view.LayoutInflater
import com.clipreader.R

class OverlayManager(
    private val context: Context,
    private val onPlayPause: () -> Unit,
    private val onVoiceInput: () -> Unit,
    private val onShare: () -> Unit,
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

    private var params: WindowManager.LayoutParams? = null

    enum class State { IDLE, LOADING, PLAYING, ERROR }
    private var currentState = State.IDLE

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

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 24
            val cm3InPx = (3.0f / 2.54f * context.resources.displayMetrics.densityDpi).toInt()
            y = 120 + cm3InPx
        }

        setupDragListener()
        setupMicButton()

        windowManager?.addView(floatingView, params)
        setState(State.IDLE)
        setMicState(MicState.IDLE)
    }

    fun setState(state: State) {
        currentState = state
        when (state) {
            State.IDLE -> {
                iconBackground?.apply {
                    setBackgroundResource(R.drawable.bg_floating_idle)
                    alpha = 0.8f
                }
                iconImage?.setImageResource(android.R.drawable.ic_media_play)
            }
            State.LOADING -> {
                iconBackground?.setBackgroundColor(Color.parseColor("#FFA500"))
                iconBackground?.alpha = 0.9f
            }
            State.PLAYING -> {
                iconBackground?.apply {
                    setBackgroundResource(R.drawable.bg_floating_playing)
                    alpha = 1.0f
                }
                iconImage?.setImageResource(android.R.drawable.ic_media_pause)
            }
            State.ERROR -> {
                iconBackground?.setBackgroundColor(Color.parseColor("#FF0000"))
                iconBackground?.alpha = 1.0f
            }
        }
    }

    fun setMicState(state: MicState) {
        micState = state
        when (state) {
            MicState.IDLE -> {
                micBackground?.apply {
                    setBackgroundResource(R.drawable.bg_floating_idle)
                    alpha = 0.8f
                }
                micImage?.setImageResource(android.R.drawable.ic_btn_speak_now)
            }
            MicState.LISTENING -> {
                micBackground?.setBackgroundColor(Color.parseColor("#E53935"))
                micBackground?.alpha = 1.0f
                micImage?.setImageResource(android.R.drawable.ic_btn_speak_now)
            }
        }
    }

    private fun setupMicButton() {
        micBackground?.setOnClickListener {
            onVoiceInput()
        }
        shareBackground?.setOnClickListener {
            onShare()
        }
    }

    private fun setupDragListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params!!.x
                    initialY = params!!.y
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

                    params!!.x = initialX + dx
                    params!!.y = initialY + dy
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val screenHeight = context.resources.displayMetrics.heightPixels
                    if (params!!.y < screenHeight - 300 && !isClick) {
                        // Dragged far down but not a click — nothing
                    } else if (params!!.y >= screenHeight - 300 && !isClick) {
                        onStopService()
                    } else if (isClick) {
                        // Split by Y: 3 segments: top = play, middle = share, bottom = mic
                        val viewHeight = floatingView?.height ?: 3
                        val touchY = event.y
                        when {
                            touchY < viewHeight / 3f -> onPlayPause()
                            touchY < 2 * viewHeight / 3f -> onShare()
                            else -> onVoiceInput()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun remove() {
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {}
            floatingView = null
        }
    }
}
