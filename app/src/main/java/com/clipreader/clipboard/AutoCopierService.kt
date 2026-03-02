package com.clipreader.clipboard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutoCopierService : AccessibilityService() {

    companion object {
        // Static singleton so ClipReaderService can call us directly
        var instance: AutoCopierService? = null
    }

    private var lastClickTime = 0L
    private val CLICK_DEBOUNCE_MS = 2000L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val info = AccessibilityServiceInfo()
        // Listen for window state changes (app launch/foreground) + content changes
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                          AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.DEFAULT or 
                     AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 500
        this.serviceInfo = info
        Log.d("AutoCopierService", "Service connected. Instance registered.")
    }

    private val TARGET_PACKAGES = listOf(
        "com.openai.chatgpt",
        "claude",
        "com.anthropic"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        val isAiApp = TARGET_PACKAGES.any { pkg.contains(it, ignoreCase = true) }
        if (!isAiApp) return

        // Auto-start ClipReaderService if not already running
        if (!com.clipreader.service.ClipReaderService.isRunning) {
            Log.d("AutoCopierService", "Detected AI app: $pkg. Auto-starting ClipReaderService.")
            val intent = android.content.Intent(this, com.clipreader.service.ClipReaderService::class.java)
            startForegroundService(intent)
        }
    }

    override fun onInterrupt() {
        Log.d("AutoCopierService", "Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    /**
     * Called by ClipReaderService when user taps the play button.
     * 
     * Strategy:
     *  - ChatGPT: tap the built-in 朗读 (Read Aloud) button → let ChatGPT use its own TTS
     *  - Claude / other AI apps: tap 复制 → wait → launch ClipboardReaderActivity → our TTS plays
     */
    fun tryCopyAndPlay() {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.w("AutoCopierService", "No root window, launching ClipboardReader directly.")
            launchClipboardReader()
            return
        }

        val packageName = rootNode.packageName?.toString() ?: ""
        Log.d("AutoCopierService", "tryCopyAndPlay called for package: $packageName")

        if (packageName.contains("openai", ignoreCase = true) ||
            packageName.contains("chatgpt", ignoreCase = true)) {
            // ChatGPT: tap the 朗读 button (built-in TTS, no need to copy)
            tapButtonByDesc(rootNode, listOf("朗读", "Read aloud", "Speak"))
        } else {
            // Claude / Doubao / other apps: copy text, then use our TTS
            val found = tapButtonByDescAndThenRead(rootNode, listOf("复制", "Copy", "拷贝", "Copy to clipboard"))
            if (!found) {
                Log.d("AutoCopierService", "No 复制 button found, reading clipboard directly.")
                launchClipboardReader()
            }
        }
    }

    /** Taps the LAST (bottommost) matching button by content-desc. Returns true if tapped. */
    private fun tapButtonByDesc(root: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        // Collect ALL matches across all keywords, then take the one with highest Y (newest message)
        val allMatches = mutableListOf<Pair<AccessibilityNodeInfo, Rect>>()
        for (keyword in keywords) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword) +
                        findNodesByContentDescription(root, keyword)
            for (node in nodes.distinctBy { System.identityHashCode(it) }) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) allMatches.add(Pair(node, bounds))
            }
        }
        // Sort by bottom Y descending → take bottommost = latest message
        val target = allMatches.maxByOrNull { it.second.bottom } ?: return false

        val now = System.currentTimeMillis()
        if (now - lastClickTime < CLICK_DEBOUNCE_MS) return false

        val cx = target.second.centerX().toFloat()
        val cy = target.second.centerY().toFloat()
        Log.d("AutoCopierService", "Tapping bottommost button at ($cx, $cy), bounds=${target.second}")

        val path = Path().apply { moveTo(cx, cy) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
        lastClickTime = now
        return true
    }

    /** Taps the LAST (bottommost) copy button, then launches ClipboardReaderActivity after gesture. */
    private fun tapButtonByDescAndThenRead(root: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        // Collect ALL matches, take bottommost = latest message's copy button
        val allMatches = mutableListOf<Pair<AccessibilityNodeInfo, Rect>>()
        for (keyword in keywords) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword) +
                        findNodesByContentDescription(root, keyword)
            for (node in nodes.distinctBy { System.identityHashCode(it) }) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) allMatches.add(Pair(node, bounds))
            }
        }
        val target = allMatches.maxByOrNull { it.second.bottom } ?: return false

        val now = System.currentTimeMillis()
        if (now - lastClickTime < CLICK_DEBOUNCE_MS) return false

        val cx = target.second.centerX().toFloat()
        val cy = target.second.centerY().toFloat()
        Log.d("AutoCopierService", "Tapping bottommost copy at ($cx, $cy), then will read clipboard")

        val path = Path().apply { moveTo(cx, cy) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    launchClipboardReader()
                }, 700)
            }
            override fun onCancelled(g: GestureDescription?) {
                Log.w("AutoCopierService", "Gesture cancelled.")
            }
        }, null)
        lastClickTime = now
        return true
    }

    private fun launchClipboardReader() {
        try {
            val intent = Intent(this, ClipboardReaderActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("AutoCopierService", "Error launching ClipboardReaderActivity: ${e.message}")
        }
    }

    private fun findNodesByContentDescription(root: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val desc = node.contentDescription?.toString()
            if (desc != null && desc.contains(text, ignoreCase = true)) {
                result.add(node)
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) queue.add(child)
            }
        }
        return result
    }
}
