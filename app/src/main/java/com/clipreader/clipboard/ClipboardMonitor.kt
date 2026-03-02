package com.clipreader.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast

class ClipboardMonitor(
    private val context: Context,
    private val onNewText: (String) -> Unit
) {
    private var lastText: String? = null
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    
    fun readClipboard() {
        if (!clipboard.hasPrimaryClip()) {
            Toast.makeText(context, "系统剪切板为空", Toast.LENGTH_SHORT).show()
            return
        }
        
        val clip: ClipData = clipboard.primaryClip ?: return
        if (clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()
            if (!text.isNullOrBlank()) {
                val contentToPlay = if (text.length > 2000) text.substring(0, 2000) else text
                Log.d("ClipboardMonitor", "Manually read text: $contentToPlay")
                onNewText(contentToPlay)
            } else {
                Toast.makeText(context, "未获取到有效文本", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun start() {
        // No-op. Background listening is disabled on Android 10+.
    }

    fun stop() {
        // No-op.
    }
}
