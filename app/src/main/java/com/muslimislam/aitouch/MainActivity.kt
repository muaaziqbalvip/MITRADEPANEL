package com.muslimislam.aitouch

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etBackendUrl: EditText
    private lateinit var etGroqKey: EditText
    private lateinit var etPrompt: EditText
    private lateinit var tvStatus: TextView

    private val screenCaptureLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                // Hand the projection permission result to the ScreenCaptureService
                val svcIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_START
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                }
                ContextCompat.startForegroundService(this, svcIntent)
                Toast.makeText(this, "Screen capture ready", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Screen capture permission zaroori hai", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etBackendUrl = findViewById(R.id.etBackendUrl)
        etGroqKey = findViewById(R.id.etGroqKey)
        etPrompt = findViewById(R.id.etPrompt)
        tvStatus = findViewById(R.id.tvStatus)

        etBackendUrl.setText(AppStore.loadBackendUrl(this))
        etGroqKey.setText(AppStore.loadGroqKey(this))
        etPrompt.setText(AppStore.loadPrompt(this))

        findViewById<Button>(R.id.btnSaveSettings).setOnClickListener {
            AppStore.saveBackendUrl(this, etBackendUrl.text.toString().trim())
            AppStore.saveGroqKey(this, etGroqKey.text.toString().trim())
            AppStore.savePrompt(this, etPrompt.text.toString().trim())
            Toast.makeText(this, "Settings save ho gayin. Ab dobara prompt nahi maangega.", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission pehle se hai", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnAccessibilityPermission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "AI Touch service ko dhoondh kar ON karein", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btnNotifPermission).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            } else {
                Toast.makeText(this, "Zaroorat nahi is Android version par", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnStartPanel).setOnClickListener {
            startPanel()
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val accOk = isAccessibilityServiceEnabled()
        tvStatus.text = buildString {
            append(if (overlayOk) "✅ Overlay permission ON\n" else "❌ Overlay permission OFF\n")
            append(if (accOk) "✅ Accessibility service ON\n" else "❌ Accessibility service OFF\n")
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun startPanel() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Pehle Overlay permission dein", Toast.LENGTH_LONG).show()
            return
        }
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Pehle Accessibility service ON karein", Toast.LENGTH_LONG).show()
            return
        }
        if (AppStore.loadBackendUrl(this).isBlank() || AppStore.loadGroqKey(this).isBlank()) {
            Toast.makeText(this, "Pehle Backend URL aur Groq Key save karein", Toast.LENGTH_LONG).show()
            return
        }

        // Request screen capture permission (needed once per session)
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())

        // Start the floating panel overlay service
        val overlayIntent = Intent(this, OverlayService::class.java)
        ContextCompat.startForegroundService(this, overlayIntent)

        moveTaskToBack(true)
    }
}
