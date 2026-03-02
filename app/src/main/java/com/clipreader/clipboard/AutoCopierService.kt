package com.clipreader.clipboard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.widget.Toast
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

    // ---------- Voice Input Toggle ----------

    private var isListening = false

    /**
     * Toggle voice input:
     * - First tap: find & tap input field → wait → tap 豆包 "点击说话" button
     * - Second tap: tap "结束说话" (end speech) button
     */
    fun tryVoiceInput(overlayManager: com.clipreader.overlay.OverlayManager?) {
        tryVoiceInput() // Call the new overloaded function
    }

    private fun tryVoiceInput() {
        Log.d("AutoCopierService", "Attempting Doubao Voice Input via Intent")
        val packageName = getDoubaoPackage()
        if (packageName == null) {
            Log.e("AutoCopierService", "Doubao App not found (checked com.doubao.app and com.larus.nova)")
            Toast.makeText(this, "未找到豆包App，请安装后重试", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent("com.doubao.action.VOICE_INPUT").apply {
                setPackage(packageName)
                putExtra("extra_prompt", "请说出您要发送的内容")
                putExtra("extra_need_final_only", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Log.d("AutoCopierService", "Launched Doubao VOICE_INPUT for package: $packageName")
        } catch (e: Exception) {
            Log.e("AutoCopierService", "Failed to start Doubao Voice Input: ${e.message}")
            Toast.makeText(this, "启动豆包语音输入失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getDoubaoPackage(): String? {
        val packages = listOf("com.doubao.app", "com.larus.nova")
        for (pkg in packages) {
            try {
                packageManager.getPackageInfo(pkg, 0)
                return pkg
            } catch (e: Exception) {
                // Not found, continue to next package
            }
        }
        return null
    }

    /**
     * Called when Doubao ASR broadcast indicates completion.
     */
    fun handleDoubaoAsrResult(text: String) {
        if (text.isEmpty()) return
        Log.d("AutoCopierService", "Received ASR result: $text. Attempting to input and send.")
        
        // Strategy: 
        // 1. Find EditText
        // 2. Set text via AccessibilityNodeInfo.ACTION_SET_TEXT (if possible) or just wait for Doubao to fill it (some ASR intents auto-fill)
        // 3. Robustly tap Send button
        
        // According to common AI app behavior, we might need a small delay for the focus to return
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            tapSendButton()
        }, 1000)
    }

    private fun tapSendButton() {
        Log.d("AutoCopierService", "Attempting robust send button discovery...")
        val root = rootInActiveWindow ?: return
        
        // 1. Keyword-based discovery with Parent-Traversal
        val sendKeywords = listOf("发送", "Send", "提交", "Submit", "Add to chat") // Claude's icon-desc is sometimes Send or Add to chat
        for (keyword in sendKeywords) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword) +
                        findNodesByContentDescription(root, keyword)
            for (node in nodes) {
                // Find nearest clickable parent or the node itself
                var candidate: android.view.accessibility.AccessibilityNodeInfo? = node
                while (candidate != null) {
                    if (candidate.isClickable && candidate.isEnabled) {
                        val b = Rect()
                        candidate.getBoundsInScreen(b)
                        if (!b.isEmpty) {
                            val sx = b.centerX().toFloat()
                            val sy = b.centerY().toFloat()
                            Log.d("AutoCopierService", "Tapping send (node-traversal: $keyword) at ($sx, $sy)")
                            val p = Path().apply { moveTo(sx, sy) }
                            val g = GestureDescription.Builder()
                                .addStroke(GestureDescription.StrokeDescription(p, 0, 50))
                                .build()
                            dispatchGesture(g, null, null)
                            return
                        }
                    }
                    candidate = candidate.parent
                }
            }
        }

        // 2. Relative position discovery (Right of EditText)
        val editNodes = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        fun findEdits(node: android.view.accessibility.AccessibilityNodeInfo) {
            if (node.className?.contains("EditText") == true) editNodes.add(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let { findEdits(it) }
        }
        findEdits(root)

        val inputNode = editNodes.firstOrNull()
        if (inputNode != null) {
            val b = Rect()
            inputNode.getBoundsInScreen(b)
            if (!b.isEmpty) {
                // Look for siblings to the right of EditText
                val parent = inputNode.parent
                if (parent != null) {
                    for (i in 0 until parent.childCount) {
                        val sibling = parent.getChild(i) ?: continue
                        if (sibling != inputNode) {
                            val sb = Rect()
                            sibling.getBoundsInScreen(sb)
                            if (sb.left >= b.right - 50 && sb.centerY() in (b.top - 100)..(b.bottom + 100) && (sibling.isClickable || sibling.childCount > 0)) {
                                val sx = sb.centerX().toFloat()
                                val sy = sb.centerY().toFloat()
                                Log.d("AutoCopierService", "Tapping send (right-sibling) at ($sx, $sy)")
                                val p = Path().apply { moveTo(sx, sy) }
                                val g = GestureDescription.Builder()
                                    .addStroke(GestureDescription.StrokeDescription(p, 0, 50))
                                    .build()
                                dispatchGesture(g, null, null)
                                return
                            }
                        }
                    }
                }
                
                // Pure coordinate offset from EditText
                val sx = (b.right + 80).toFloat().coerceAtMost(resources.displayMetrics.widthPixels * 0.98f)
                val sy = b.centerY().toFloat()
                Log.d("AutoCopierService", "Tapping send (EditText-offset) at ($sx, $sy)")
                val p = Path().apply { moveTo(sx, sy) }
                val g = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(p, 0, 50))
                    .build()
                dispatchGesture(g, null, null)
                return
            }
        }

        // 3. Absolute Fallback
        val dm = resources.displayMetrics
        val sx = dm.widthPixels * 0.92f
        val sy = dm.heightPixels * 0.63f
        Log.d("AutoCopierService", "Tapping send (absolute-fallback) at ($sx, $sy)")
        val p = Path().apply { moveTo(sx, sy) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 50))
            .build()
        dispatchGesture(g, null, null)
    }

    /**
     * Similar to tryCopyAndPlay, but triggers a share broadcast after copying.
     */
    fun tryCopyAndShare() {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.w("AutoCopierService", "No root window, triggering share directly.")
            launchClipboardReader("SHARE")
            return
        }

        val packageName = rootNode.packageName?.toString() ?: ""
        Log.d("AutoCopierService", "tryCopyAndShare called for package: $packageName")

        val found = tapButtonByDescAndThenAction(rootNode, listOf("复制", "Copy", "拷贝", "Copy to clipboard"), "SHARE")
        if (!found) {
            Log.d("AutoCopierService", "No 复制 button found, triggering share directly.")
            launchClipboardReader("SHARE")
        }
    }

    /**
     * Called by ClipReaderService when user taps the play button.
     */
    fun tryCopyAndPlay() {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.w("AutoCopierService", "No root window, launching ClipboardReader directly.")
            launchClipboardReader("PLAY")
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
            val found = tapButtonByDescAndThenAction(rootNode, listOf("复制", "Copy", "拷贝", "Copy to clipboard"), "READ")
            if (!found) {
                Log.d("AutoCopierService", "No 复制 button found, reading clipboard directly.")
                launchClipboardReader("PLAY")
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

    /** Taps the LAST (bottommost) copy button, then executes the specified action after gesture. */
    private fun tapButtonByDescAndThenAction(root: AccessibilityNodeInfo, keywords: List<String>, action: String): Boolean {
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
        Log.d("AutoCopierService", "Tapping bottommost copy at ($cx, $cy), then will perform: $action")

        val path = Path().apply { moveTo(cx, cy) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    when (action) {
                        "READ" -> launchClipboardReader("PLAY")
                        "SHARE" -> launchClipboardReader("SHARE")
                    }
                }, 700)
            }
            override fun onCancelled(g: GestureDescription?) {
                Log.w("AutoCopierService", "Gesture cancelled.")
            }
        }, null)
        lastClickTime = now
        return true
    }

    private fun launchClipboardReader(actionType: String = "PLAY") {
        try {
            val intent = Intent(this, ClipboardReaderActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra("ACTION_TYPE", actionType)
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
