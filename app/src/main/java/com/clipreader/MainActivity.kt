package com.clipreader

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.clipreader.service.ClipReaderService

import android.widget.ArrayAdapter
import android.widget.Spinner
import com.clipreader.util.PrefsManager

class MainActivity : AppCompatActivity() {
    private val PERMISSION_REQUEST_CODE = 1001
    private lateinit var prefsManager: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefsManager = PrefsManager(this)

        val spinnerPrimary = findViewById<Spinner>(R.id.spinnerPrimaryEngine)
        val spinnerFallback = findViewById<Spinner>(R.id.spinnerFallbackEngine)

        val options = arrayOf("Microsoft Azure TTS", "System TTS (本机离线)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        spinnerPrimary.adapter = adapter
        spinnerFallback.adapter = adapter

        // Set previous selections or defaults
        val pEngine = prefsManager.getPrimaryEngine()
        val fEngine = prefsManager.getFallbackEngine()
        spinnerPrimary.setSelection(if (pEngine == "System TTS") 1 else 0)
        spinnerFallback.setSelection(if (fEngine == "Microsoft Azure TTS") 0 else 1)

        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            val selectedPrimary = if (spinnerPrimary.selectedItemPosition == 0) "Microsoft Azure TTS" else "System TTS"
            val selectedFallback = if (spinnerFallback.selectedItemPosition == 0) "Microsoft Azure TTS" else "System TTS"
            
            prefsManager.savePrimaryEngine(selectedPrimary)
            prefsManager.saveFallbackEngine(selectedFallback)

            if (checkPermissions()) {
                startClipReaderService()
            }
        }

        findViewById<Button>(R.id.btnTestPrimary).setOnClickListener {
            val selectedEngine = if (spinnerPrimary.selectedItemPosition == 0) "Microsoft Azure TTS" else "System TTS"
            
            val intent = Intent(this, ClipReaderService::class.java).apply {
                action = "ACTION_TEST_ENGINE"
                putExtra("ENGINE_NAME", selectedEngine)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        findViewById<Button>(R.id.btnTestFallback).setOnClickListener {
            val selectedEngine = if (spinnerFallback.selectedItemPosition == 0) "Microsoft Azure TTS" else "System TTS"
            
            val intent = Intent(this, ClipReaderService::class.java).apply {
                action = "ACTION_TEST_ENGINE"
                putExtra("ENGINE_NAME", selectedEngine)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        findViewById<Button>(R.id.btnStopService).setOnClickListener {
            stopClipReaderService()
        }
    }

    private fun checkPermissions(): Boolean {
        // 1. Check Overlay Permission
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "请授予悬浮窗权限后再次点击启动", Toast.LENGTH_LONG).show()
            return false
        }

        // 2. Check Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PERMISSION_REQUEST_CODE
                )
                Toast.makeText(this, "请授予通知权限", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        return true
    }

    private fun startClipReaderService() {
        val intent = Intent(this, ClipReaderService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "服务已启动", Toast.LENGTH_SHORT).show()
    }

    private fun stopClipReaderService() {
        val intent = Intent(this, ClipReaderService::class.java)
        stopService(intent)
        Toast.makeText(this, "服务已停止", Toast.LENGTH_SHORT).show()
    }
}
