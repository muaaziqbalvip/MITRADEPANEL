package com.muslimislam.aitouch

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.UUID
import kotlin.math.roundToInt

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var panelView: View? = null
    private val dotViews = mutableMapOf<String, View>()
    private val dots = mutableListOf<TouchDot>()

    companion object {
        const val CHANNEL_ID = "ai_touch_overlay"
        const val NOTIF_ID = 1001
        var isRunning = false
        var instance: OverlayService? = null
    }

    override fun onCreate() {
        super.onCreate()
        safe("onCreate") {
            isRunning = true
            instance = this
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            startForegroundNotif()
            dots.addAll(AppStore.loadDots(this))
            showPanel()
            dots.forEach { addDotView(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        safe("onDestroy-panel") { panelView?.let { windowManager?.removeView(it) } }
        dotViews.values.forEach { v -> safe("onDestroy-dot") { windowManager?.removeView(v) } }
    }

    private fun safe(where: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            try {
                Toast.makeText(
                    this,
                    "❌ [$where] ${e.javaClass.simpleName}: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } catch (_: Throwable) {
            }
        }
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
    }

    private fun showPanel() = safe("showPanel") {
        val wm = windowManager ?: return@safe
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.control_panel, null)
        panelView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 40
        params.y = 120

        makeDraggable(view.findViewById(R.id.btnDrag), view, params)

        view.findViewById<View>(R.id.btnAddDot).setOnClickListener {
            safe("btnAddDot click") { promptNewDotName() }
        }
        view.findViewById<View>(R.id.btnCapture).setOnClickListener {
            safe("btnCapture click") { runAiOnScreen() }
        }
        view.findViewById<View>(R.id.btnMinimize).setOnClickListener {
            safe("btnMinimize click") {
                view.visibility = if (view.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        view.findViewById<View>(R.id.btnClose).setOnClickListener {
            safe("btnClose click") { stopSelf() }
        }

        wm.addView(view, params)
    }

    private fun promptNewDotName() {
        val wm = windowManager ?: return
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_dot_name, null)
        val editText = dialogView.findViewById<EditText>(R.id.etDotName)

        val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("Naya Touching Dot")
            .setView(dialogView)
            .setPositiveButton("Add Karo") { d, _ ->
                safe("addDot button") {
                    val name = editText.text.toString().trim().ifEmpty { "dot${dots.size + 1}" }
                    d.dismiss()
                    android.os.Handler(mainLooper).postDelayed({
                        safe("addDot delayed") {
                            val dot = TouchDot(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                x = 300f,
                                y = 500f,
                                locked = false
                            )
                            dots.add(dot)
                            addDotView(dot)
                            persistDots()
                            Toast.makeText(this@OverlayService, "✓ Dot add: $name", Toast.LENGTH_SHORT).show()
                        }
                    }, 300)
                }
            }
            .setNegativeButton("Cancel") { d, _ ->
                safe("cancelDot button") { d.dismiss() }
            }
            .create()

        safe("dialog window type") { dialog.window?.setType(overlayType()) }
        safe("dialog show") { dialog.show() }
    }

    private fun addDotView(dot: TouchDot) = safe("addDotView") {
        val wm = windowManager ?: return@safe
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dot_view, null)
        val label = view.findViewById<TextView>(R.id.dotLabel)
        label.text = dot.name
        updateDotAppearance(view, dot)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = dot.x.roundToInt()
        params.y = dot.y.roundToInt()

        wm.addView(view, params)
        dotViews[dot.id] = view

        setupDotTouchBehavior(view, params, dot)
    }

    private fun updateDotAppearance(view: View, dot: TouchDot) = safe("updateDotAppearance") {
        val circle = view.findViewById<View>(R.id.dotCircle)
        circle.setBackgroundResource(if (dot.locked) R.drawable.dot_circle_locked else R.drawable.dot_circle)
    }

    private fun setupDotTouchBehavior(view: View, params: WindowManager.LayoutParams, dot: TouchDot) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            var handled = true
            safe("dot touch") {
                val wm = windowManager
                if (dot.locked) {
                    if (event.action == MotionEvent.ACTION_UP) {
                        showDotMenu(view, dot)
                    }
                    return@safe
                }
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).roundToInt()
                        val dy = (event.rawY - touchY).roundToInt()
                        if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) moved = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        wm?.updateViewLayout(view, params)
                        dot.x = params.x.toFloat()
                        dot.y = params.y.toFloat()
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            showDotMenu(view, dot)
                        } else {
                            persistDots()
                        }
                    }
                }
            }
            handled
        }
    }

    private fun showDotMenu(view: View, dot: TouchDot) = safe("showDotMenu") {
        val options = if (dot.locked)
            arrayOf("Unlock", "Rename", "Delete")
        else
            arrayOf("Lock", "Rename", "Delete")

        val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle(dot.name)
            .setItems(options) { d, which ->
                safe("dotMenu choice") {
                    when (which) {
                        0 -> {
                            dot.locked = !dot.locked
                            updateDotAppearance(view, dot)
                            persistDots()
                            d.dismiss()
                            Toast.makeText(this, if (dot.locked) "🔒 Locked" else "🔓 Unlocked", Toast.LENGTH_SHORT).show()
                        }
                        1 -> {
                            d.dismiss()
                            renameDot(view, dot)
                        }
                        2 -> {
                            d.dismiss()
                            deleteDot(view, dot)
                        }
                    }
                }
            }
            .create()

        safe("dotMenu window type") { dialog.window?.setType(overlayType()) }
        safe("dotMenu show") { dialog.show() }
    }

    private fun renameDot(view: View, dot: TouchDot) {
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_dot_name, null)
        val editText = dialogView.findViewById<EditText>(R.id.etDotName)
        editText.setText(dot.name)

        val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("Naam Badlo")
            .setView(dialogView)
            .setPositiveButton("Save") { d, _ ->
                safe("renameDot save") {
                    val newName = editText.text.toString().trim()
                    if (newName.isNotEmpty()) {
                        dot.name = newName
                        view.findViewById<TextView>(R.id.dotLabel).text = newName
                        persistDots()
                        d.dismiss()
                        Toast.makeText(this@OverlayService, "✓ Renamed: $newName", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel") { d, _ ->
                safe("renameDot cancel") { d.dismiss() }
            }
            .create()

        safe("renameDot window type") { dialog.window?.setType(overlayType()) }
        safe("renameDot show") { dialog.show() }
    }

    private fun deleteDot(view: View, dot: TouchDot) = safe("deleteDot") {
        windowManager?.removeView(view)
        dotViews.remove(dot.id)
        dots.remove(dot)
        persistDots()
        Toast.makeText(this, "✓ Dot deleted", Toast.LENGTH_SHORT).show()
    }

    private fun persistDots() = safe("persistDots") {
        AppStore.saveDots(this, dots)
    }

    private fun runAiOnScreen() {
        if (dots.isEmpty()) {
            Toast.makeText(this, "❌ Pehle dot add karein", Toast.LENGTH_SHORT).show()
            return
        }
        if (!ScreenCaptureService.isReady) {
            Toast.makeText(this, "❌ Screen capture ready nahi", Toast.LENGTH_LONG).show()
            return
        }
        safe("runAiOnScreen") {
            Toast.makeText(this, "⏳ Processing...", Toast.LENGTH_SHORT).show()

            setOverlayVisibility(View.INVISIBLE)

            val intent = Intent(this, ScreenCaptureService::class.java)
            intent.action = ScreenCaptureService.ACTION_CAPTURE_AND_ANALYZE
            intent.putExtra(ScreenCaptureService.EXTRA_DOTS_JSON, dotsToJsonString())
            intent.putExtra(ScreenCaptureService.EXTRA_PROMPT, AppStore.loadPrompt(this))
            ContextCompat.startForegroundService(this, intent)

            OverlayService.instance = this
            android.os.Handler(mainLooper).postDelayed({
                safe("restoreVisibility-timeout") { setOverlayVisibility(View.VISIBLE) }
            }, 15000)
        }
    }

    fun restoreVisibilityNow() = safe("restoreVisibilityNow") {
        setOverlayVisibility(View.VISIBLE)
    }

    private fun setOverlayVisibility(visibility: Int) {
        panelView?.visibility = visibility
        dotViews.values.forEach { it.visibility = visibility }
    }

    private fun dotsToJsonString(): String {
        val arr = org.json.JSONArray()
        dots.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }

    private fun makeDraggable(handle: View, container: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        handle.setOnTouchListener { _, event ->
            safe("panel drag") {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - touchX).roundToInt()
                        params.y = initialY + (event.rawY - touchY).roundToInt()
                        windowManager?.updateViewLayout(container, params)
                    }
                }
            }
            true
        }
    }

    private fun startForegroundNotif() = safe("startForegroundNotif") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "AI Touch Overlay", NotificationManager.IMPORTANCE_MIN
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Touch Active")
            .setContentText("Panel running")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }
}