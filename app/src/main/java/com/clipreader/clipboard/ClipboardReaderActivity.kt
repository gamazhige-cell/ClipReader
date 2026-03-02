package com.clipreader.clipboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.clipreader.service.ClipReaderService

class ClipboardReaderActivity : Activity() {
    private var hasRead = false
    private var actionType = "PLAY"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionType = intent.getStringExtra("ACTION_TYPE") ?: "PLAY"
        Log.d("ClipboardReader", "Starting transparent activity for clipboard ($actionType)")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        
        if (hasFocus && !hasRead) {
            hasRead = true
            readClipboardAndFinish()
        }
    }

    private fun readClipboardAndFinish() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (!clipboard.hasPrimaryClip()) {
                Toast.makeText(this, "系统剪切板为空", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            
            val clip: ClipData? = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0)?.text?.toString()
                if (!text.isNullOrBlank()) {
                    val cleaned = stripMarkdown(text)
                    val contentToPlay = if (cleaned.length > 2000) cleaned.substring(0, 2000) else cleaned
                    Log.d("ClipboardReader", "Read text (cleaned): $contentToPlay")
                    
                    if (actionType == "SHARE") {
                        // Send intent to service to share the text
                        val shareIntent = Intent(this, ClipReaderService::class.java).apply {
                            action = "ACTION_SHARE_TEXT"
                            putExtra("TEXT_TO_SHARE", text) // Use original text for sharing, prompt added in service
                        }
                        startService(shareIntent)
                    } else {
                        // Send intent to service to play the text
                        val playIntent = Intent(this, ClipReaderService::class.java).apply {
                            action = "ACTION_PLAY_TEXT"
                            putExtra("TEXT_TO_PLAY", contentToPlay)
                        }
                        startService(playIntent)
                    }
                    
                    // Clear the clipboard explicitly to prevent accidental replays
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                } else {
                    Toast.makeText(this, "未获取到有效文本", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "系统剪切板为空", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("ClipboardReader", "Error reading clipboard: ${e.message}")
            Toast.makeText(this, "读取剪切板失败", Toast.LENGTH_SHORT).show()
        } finally {
            // Stay invisible and finish
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            finish()
        }
    }

    /**
     * Strip Markdown formatting for clean TTS playback.
     * Removes code blocks, links (keeps link text), headers, bold/italic, bullets etc.
     */
    private fun stripMarkdown(text: String): String {
        var s = text

        // Remove fenced code blocks (```...```)
        s = s.replace(Regex("```[\\s\\S]*?```"), "")

        // Remove inline code (`...`)
        s = s.replace(Regex("`[^`]*`"), "")

        // Remove markdown links: [text](url) → text
        s = s.replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "$1")

        // Remove bare URLs (https://... or http://...)
        s = s.replace(Regex("https?://\\S+"), "")

        // Remove image markdown: ![alt](url) → ""
        s = s.replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), "")

        // Remove heading markers (# ## ###)
        s = s.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")

        // Remove bold/italic markers (**, *, __, _)
        s = s.replace(Regex("(\\*\\*|__|\\*|_)(?=\\S)([\\s\\S]*?)(?<=\\S)\\1"), "$2")

        // Remove strikethrough (~~text~~)
        s = s.replace(Regex("~~([^~]+)~~"), "$1")

        // Remove blockquote markers (> at start of line)
        s = s.replace(Regex("^>+\\s*", RegexOption.MULTILINE), "")

        // Remove horizontal rules (--- or ***)
        s = s.replace(Regex("^([-*_]\\s*){3,}$", RegexOption.MULTILINE), "")

        // Replace bullet/numbered list markers with a pause
        s = s.replace(Regex("^[\\*\\-\\+]\\s+", RegexOption.MULTILINE), "")
        s = s.replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")

        // Collapse multiple blank lines into one
        s = s.replace(Regex("\\n{3,}"), "\n\n")

        return s.trim()
    }
}
