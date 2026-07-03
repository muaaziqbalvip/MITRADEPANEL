package com.muslimislam.aitouch

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Foreground service that draws:
 *  1) A small draggable control panel (Add Dot / AI Run / Minimize / Close)
 *  2) User-added "touching dots" which can be dragged and then locked in place
 *
 * When "AI Run" is tapped, it triggers a screenshot capture + sends it,
 * along with dot positions/names and the ONE-TIME saved prompt, to the
 * HF Space backend. The backend replies with which dot/action to perform,
 * and TapAccessibilityService executes the real tap/gesture.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var panelView: View? = null
    private val dotViews = mutableMapOf<String, View>()
    private val dots = mutableListOf<TouchDot>()

    companion object {
        const val CHANNEL_ID = "ai_touch_overlay"
        const val NOTIF_ID = 1001
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundNotif()
        dots.addAll(AppStore.loadDots(this))
        showPanel()
        dots.forEach { addDotView(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        panelView?.let { runCatching { windowManager.removeView(it) } }
        dotViews.values.forEach { runCatching { windowManager.removeView(it) } }
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
    }

    // ---------------- Control Panel ----------------

    private fun showPanel() {
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
            promptNewDotName()
        }
        view.findViewById<View>(R.id.btnCapture).setOnClickListener {
            runAiOnScreen()
        }
        view.findViewById<View>(R.id.btnMinimize).setOnClickListener {
            view.visibility = if (view.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        view.findViewById<View>(R.id.btnClose).setOnClickListener {
            stopSelf()
        }

        windowManager.addView(view, params)
    }

    // ---------------- Dots ----------------

    private fun promptNewDotName() {
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_dot_name, null)
        val editText = dialogView.findViewById<EditText>(R.id.etDotName)

        val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("Naya Touching Dot")
            .setView(dialogView)
            .setPositiveButton("Add Karo") { _, _ ->
                val name = editText.text.toString().trim().ifEmpty { "dot${dots.size + 1}" }
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
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setType(overlayType())
        dialog.show()
    }

    private fun addDotView(dot: TouchDot) {
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

        windowManager.addView(view, params)
        dotViews[dot.id] = view

        setupDotTouchBehavior(view, params, dot)
    }

    private fun updateDotAppearance(view: View, dot: TouchDot) {
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
            if (dot.locked) {
                // Locked dots: only respond to long-press to unlock / rename, ignore drag
                return@setOnTouchListener handleLockedDotTap(view, dot, event)
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).roundToInt()
                    val dy = (event.rawY - touchY).roundToInt()
                    if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(view, params)
                    dot.x = params.x.toFloat()
                    dot.y = params.y.toFloat()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        // Simple tap on unlocked dot -> offer lock/rename/delete menu
                        showDotMenu(view, dot)
                    } else {
                        persistDots()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun handleLockedDotTap(view: View, dot: TouchDot, event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            showDotMenu(view, dot)
        }
        return true
    }

    private fun showDotMenu(view: View, dot: TouchDot) {
        val options = if (dot.locked)
            arrayOf("Unlock (move karne do)", "Rename", "Delete")
        else
            arrayOf("Lock (fix kar do)", "Rename", "Delete")

        val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle(dot.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        dot.locked = !dot.locked
                        updateDotAppearance(view, dot)
                        persistDots()
                        Toast.makeText(this, if (dot.locked) "Dot lock ho gaya" else "Dot unlock ho gaya", Toast.LENGTH_SHORT).show()
                    }
                    1 -> renameDot(view, dot)
                    2 -> deleteDot(view, dot)
                }
            }
            .create()
        dialog.window?.setType(overlayType())
        dialog.show()
    }

    private fun renameDot(view: View, dot: TouchDot) {
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_dot_name, null)
        val editText = dialogView.findViewById<EditText>(R.id.etDotName)
        editText.setText(dot.name)

        val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("Naam Badlo")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    dot.name = newName
                    view.findViewById<TextView>(R.id.dotLabel).text = newName
                    persistDots()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.window?.setType(overlayType())
        dialog.show()
    }

    private fun deleteDot(view: View, dot: TouchDot) {
        windowManager.removeView(view)
        dotViews.remove(dot.id)
        dots.remove(dot)
        persistDots()
    }

    private fun persistDots() {
        AppStore.saveDots(this, dots)
    }

    // ---------------- AI Run ----------------

    private fun runAiOnScreen() {
        if (dots.isEmpty()) {
            Toast.makeText(this, "Pehle kam az kam ek dot add karein", Toast.LENGTH_SHORT).show()
            return
        }
        if (!ScreenCaptureService.isReady) {
            Toast.makeText(this, "Screen capture permission ready nahi. App dobara kholein.", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "AI screen process kar raha hai...", Toast.LENGTH_SHORT).show()

        // Hide panel + dots briefly so they don't appear in the screenshot itself
        setOverlayVisibility(View.INVISIBLE)

        val intent = Intent(this, ScreenCaptureService::class.java)
        intent.action = ScreenCaptureService.ACTION_CAPTURE_AND_ANALYZE
        intent.putExtra(ScreenCaptureService.EXTRA_DOTS_JSON, dotsToJsonString())
        intent.putExtra(ScreenCaptureService.EXTRA_PROMPT, AppStore.loadPrompt(this))
        startService(intent)

        // Restore visibility shortly after capture is triggered
        android.os.Handler(mainLooper).postDelayed({
            setOverlayVisibility(View.VISIBLE)
        }, 900)
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

    // ---------------- Drag helper for panel itself ----------------

    private fun makeDraggable(handle: View, container: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).roundToInt()
                    params.y = initialY + (event.rawY - touchY).roundToInt()
                    windowManager.updateViewLayout(container, params)
                    true
                }
                else -> false
            }
        }
    }

    // ---------------- Foreground Notification ----------------

    private fun startForegroundNotif() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "AI Touch Overlay", NotificationManager.IMPORTANCE_MIN
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Touch chal raha hai")
            .setContentText("Panel aur dots active hain")
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
