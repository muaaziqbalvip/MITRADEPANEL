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

/**
 * Floating toggle bubble (always on screen) + the control panel it shows/hides,
 * + user-placed "touching dots".
 *
 * Tap the bubble once -> panel appears. Tap again -> panel hides. The bubble
 * itself never disappears, so the user always has a way to bring the panel
 * back. Everything is wrapped in safe() so a single failure never crashes
 * the whole service — errors show as a Toast instead.
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var panelView: View? = null
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var isPanelVisible = false
    private val dotViews = mutableMapOf<String, View>()
    private val dots = mutableListOf<TouchDot>()

    companion object {
        const val CHANNEL_ID = "ai_touch_overlay"
        const val NOTIF_ID = 1001
        var isRunning = false
        var instance: OverlayService? = null
        const val MIN_DOT_SIZE = 28f
        const val MAX_DOT_SIZE = 90f
    }

    override fun onCreate() {
        super.onCreate()
        safe("onCreate") {
            isRunning = true
            instance = this
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            startForegroundNotif()
            dots.addAll(AppStore.loadDots(this))
            showBubble()
            dots.forEach { addDotView(it) }
            setDotsVisibility(View.GONE) // dots hidden until panel is opened
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        safe("onDestroy-bubble") { bubbleView?.let { windowManager?.removeView(it) } }
        safe("onDestroy-panel") { panelView?.let { windowManager?.removeView(it) } }
        dotViews.values.forEach { v -> safe("onDestroy-dot") { windowManager?.removeView(v) } }
    }

    /** Runs [block]; on ANY failure shows a Toast instead of crashing the service. */
    private fun safe(where: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            try {
                Toast.makeText(this, "❌ [$where] ${e.javaClass.simpleName}: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (_: Throwable) { }
        }
    }

    private fun overlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    // ---------------- Toggle Bubble ----------------

    private fun showBubble() = safe("showBubble") {
        val wm = windowManager ?: return@safe
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.toggle_bubble, null)
        bubbleView = view

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
        bubbleParams = params

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            safe("bubble touch") {
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
                        if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        wm.updateViewLayout(view, params)
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) togglePanel()
                    }
                }
            }
            true
        }

        wm.addView(view, params)
    }

    private fun togglePanel() = safe("togglePanel") {
        if (isPanelVisible) {
            hidePanelAndDots()
        } else {
            showPanelAndDots()
        }
    }

    private fun showPanelAndDots() = safe("showPanelAndDots") {
        if (panelView == null) {
            buildPanel()
        }
        panelView?.visibility = View.VISIBLE
        setDotsVisibility(View.VISIBLE)
        isPanelVisible = true
        Toast.makeText(this, "✓ Panel dikh raha hai", Toast.LENGTH_SHORT).show()
    }

    private fun hidePanelAndDots() = safe("hidePanelAndDots") {
        panelView?.visibility = View.GONE
        setDotsVisibility(View.GONE)
        isPanelVisible = false
        Toast.makeText(this, "✓ Panel chhupa diya", Toast.LENGTH_SHORT).show()
    }

    // ---------------- Control Panel ----------------

    private fun buildPanel() = safe("buildPanel") {
        val wm = windowManager ?: return@safe
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.control_panel, null)
        panelView = view

        val bp = bubbleParams
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = bp?.x ?: 40
        params.y = (bp?.y ?: 120) + 70

        makeDraggable(view.findViewById(R.id.btnDrag), view, params)

        view.findViewById<View>(R.id.btnAddDot).setOnClickListener {
            safe("btnAddDot click") { promptNewDotName() }
        }
        view.findViewById<View>(R.id.btnCapture).setOnClickListener {
            safe("btnCapture click") { runAiOnScreen() }
        }
        view.findViewById<View>(R.id.btnFeedback).setOnClickListener {
            safe("btnFeedback click") { showFeedbackDialog() }
        }
        view.findViewById<View>(R.id.btnPatternMatch).setOnClickListener {
            safe("btnPatternMatch click") { runPatternMatch() }
        }
        view.findViewById<View>(R.id.btnMinimize).setOnClickListener {
            safe("btnMinimize click") { hidePanelAndDots() }
        }
        view.findViewById<View>(R.id.btnClose).setOnClickListener {
            safe("btnClose click") {
                Toast.makeText(this, "✓ AI Touch band ho raha hai", Toast.LENGTH_SHORT).show()
                stopSelf()
            }
        }

        wm.addView(view, params)
    }

    /**
     * Safety check called after operations that involve showing a dialog
     * (which occasionally seems to disturb overlay window ordering). If the
     * bubble or panel view somehow lost its window attachment, this puts
     * it back so the user never loses access to the controls.
     */
    private fun ensureBubbleAndPanelVisible() = safe("ensureBubbleAndPanelVisible") {
        val wm = windowManager ?: return@safe

        val bubble = bubbleView
        if (bubble != null && !bubble.isAttachedToWindow) {
            safe("reattach bubble") {
                wm.addView(bubble, bubbleParams)
            }
        }

        val panel = panelView
        if (isPanelVisible && panel != null && !panel.isAttachedToWindow) {
            safe("reattach panel") {
                val bp = bubbleParams
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    overlayType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
                params.gravity = Gravity.TOP or Gravity.START
                params.x = bp?.x ?: 40
                params.y = (bp?.y ?: 120) + 70
                wm.addView(panel, params)
            }
        }
    }

    private fun showFeedbackDialog() = safe("showFeedbackDialog") {
        if (BackendClient.lastDescription == null) {
            Toast.makeText(this, "❌ Pehle 'AI Run' se ek analysis karein", Toast.LENGTH_LONG).show()
            return@safe
        }

        val options = arrayOf("✅ Win (profit hua)", "❌ Loss (loss hua)", "📊 Stats dekhein")
        val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("Pichle trade ka result")
            .setItems(options) { d, which ->
                safe("feedback choice") {
                    d.dismiss()
                    when (which) {
                        0 -> reportResult("win")
                        1 -> reportResult("loss")
                        2 -> showStats()
                    }
                }
            }
            .create()

        safe("feedback window type") { dialog.window?.setType(overlayType()) }
        safe("feedback show") { dialog.show() }
    }

    private fun reportResult(result: String) = safe("reportResult") {
        Toast.makeText(this, "⏳ Save ho raha hai...", Toast.LENGTH_SHORT).show()
        BackendClient.sendFeedback(this, result) { success, error ->
            safe("feedback callback") {
                if (success) {
                    val label = if (result == "win") "✅ Win" else "❌ Loss"
                    Toast.makeText(this, "$label save ho gaya — agli baar AI is se seekhega", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "❌ ${error ?: "Feedback save nahi hua"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showStats() = safe("showStats") {
        BackendClient.fetchStats(this) { wins, losses, winRate ->
            safe("stats callback") {
                val total = wins + losses
                val msg = if (total == 0)
                    "Abhi tak koi feedback record nahi hai."
                else
                    "📊 Wins: $wins | Losses: $losses\nWin Rate: $winRate%"
                AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
                    .setTitle("Trading Stats")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .create()
                    .also { d -> safe("stats dialog type") { d.window?.setType(overlayType()) } }
                    .show()
            }
        }
    }

    // ---------------- Add / Rename dot ----------------

    private fun promptNewDotName() {
        val wm = windowManager ?: return
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_dot_name, null)
        val editText = dialogView.findViewById<EditText>(R.id.etDotName)
        editText.filters = arrayOf(android.text.InputFilter.LengthFilter(2))

        val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("Naya Touching Dot")
            .setView(dialogView)
            .setPositiveButton("Add Karo") { d, _ ->
                safe("addDot button") {
                    val name = editText.text.toString().trim().take(2).ifEmpty { "${dots.size + 1}" }
                    d.dismiss()
                    android.os.Handler(mainLooper).postDelayed({
                        safe("addDot delayed") {
                            val dot = TouchDot(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                x = 300f,
                                y = 500f,
                                locked = false,
                                sizeDp = 48f
                            )
                            dots.add(dot)
                            addDotView(dot)
                            persistDots()
                            ensureBubbleAndPanelVisible()
                            Toast.makeText(this@OverlayService, "✓ Dot add ho gaya: $name", Toast.LENGTH_SHORT).show()
                        }
                    }, 300)
                }
            }
            .setNegativeButton("Cancel") { d, _ ->
                safe("cancelDot button") {
                    d.dismiss()
                    Toast.makeText(this@OverlayService, "Cancel ho gaya", Toast.LENGTH_SHORT).show()
                }
            }
            .create()

        safe("dialog window type") { dialog.window?.setType(overlayType()) }
        safe("dialog show") { dialog.show() }
    }

    // ---------------- Dot view creation ----------------

    private fun addDotView(dot: TouchDot) = safe("addDotView") {
        val wm = windowManager ?: return@safe
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dot_view, null)
        applyDotSize(view, dot)
        val label = view.findViewById<TextView>(R.id.dotLabel)
        label.text = dot.name.take(2)
        updateDotAppearance(view, dot)
        view.visibility = if (isPanelVisible) View.VISIBLE else View.GONE

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

    private fun applyDotSize(view: View, dot: TouchDot) = safe("applyDotSize") {
        val sizePx = dp(dot.sizeDp)
        val circle = view.findViewById<View>(R.id.dotCircle)
        circle.layoutParams = circle.layoutParams.apply { width = sizePx; height = sizePx }
        val label = view.findViewById<TextView>(R.id.dotLabel)
        label.layoutParams = label.layoutParams.apply { width = sizePx; height = sizePx }
        label.textSize = (dot.sizeDp * 0.32f).coerceIn(10f, 28f)
        view.requestLayout()
    }

    private fun updateDotAppearance(view: View, dot: TouchDot) = safe("updateDotAppearance") {
        val circle = view.findViewById<View>(R.id.dotCircle)
        circle.setBackgroundResource(if (dot.locked) R.drawable.dot_circle_locked else R.drawable.dot_circle)
    }

    // ---------------- Touch: drag + resize (pinch) + tap-to-menu ----------------

    private fun setupDotTouchBehavior(view: View, params: WindowManager.LayoutParams, dot: TouchDot) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        var startSize = dot.sizeDp
        var scaleGestureInProgress = false

        val scaleDetector = android.view.ScaleGestureDetector(this, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: android.view.ScaleGestureDetector): Boolean {
                startSize = dot.sizeDp
                scaleGestureInProgress = true
                return true
            }

            override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                safe("pinch resize") {
                    val newSize = (startSize * detector.scaleFactor).coerceIn(MIN_DOT_SIZE, MAX_DOT_SIZE)
                    dot.sizeDp = newSize
                    applyDotSize(view, dot)
                }
                return true
            }

            override fun onScaleEnd(detector: android.view.ScaleGestureDetector) {
                scaleGestureInProgress = false
                persistDots()
            }
        })

        view.setOnTouchListener { _, event ->
            var handled = true
            safe("dot touch") {
                scaleDetector.onTouchEvent(event)
                if (scaleGestureInProgress || event.pointerCount > 1) {
                    return@safe
                }

                val wm = windowManager
                if (dot.locked) {
                    if (event.action == MotionEvent.ACTION_UP) showDotMenu(view, dot)
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
                        if (!moved) showDotMenu(view, dot) else persistDots()
                    }
                }
            }
            handled
        }
    }

    // ---------------- Dot menu: Lock/Unlock, Resize, Rename, Delete ----------------

    private fun showDotMenu(view: View, dot: TouchDot) = safe("showDotMenu") {
        val lockLabel = if (dot.locked) "Unlock" else "Lock"
        val options = arrayOf(lockLabel, "Size ➕/➖", "Rename", "Delete")

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
                        1 -> { d.dismiss(); showResizeDialog(view, dot) }
                        2 -> { d.dismiss(); renameDot(view, dot) }
                        3 -> { d.dismiss(); deleteDot(view, dot) }
                    }
                }
            }
            .create()

        safe("dotMenu window type") { dialog.window?.setType(overlayType()) }
        safe("dotMenu show") { dialog.show() }
    }

    private fun showResizeDialog(view: View, dot: TouchDot): Unit = safe("showResizeDialog") {
        val options = arrayOf("➖ Chota karo", "➕ Bada karo", "🔄 Reset (48dp)")
        val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("Dot Size — ${dot.name}")
            .setItems(options) { d, which ->
                safe("resize choice") {
                    when (which) {
                        0 -> dot.sizeDp = (dot.sizeDp - 8f).coerceIn(MIN_DOT_SIZE, MAX_DOT_SIZE)
                        1 -> dot.sizeDp = (dot.sizeDp + 8f).coerceIn(MIN_DOT_SIZE, MAX_DOT_SIZE)
                        2 -> dot.sizeDp = 48f
                    }
                    applyDotSize(view, dot)
                    persistDots()
                    d.dismiss()
                    reopenResizeDialog(view, dot)
                }
            }
            .setNegativeButton("Done") { d, _ -> d.dismiss() }
            .create()
        safe("resize window type") { dialog.window?.setType(overlayType()) }
        safe("resize show") { dialog.show() }
    }

    private fun reopenResizeDialog(view: View, dot: TouchDot) {
        showResizeDialog(view, dot)
    }

    private fun renameDot(view: View, dot: TouchDot) {
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_dot_name, null)
        val editText = dialogView.findViewById<EditText>(R.id.etDotName)
        editText.filters = arrayOf(android.text.InputFilter.LengthFilter(2))
        editText.setText(dot.name)

        val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setTitle("Naam Badlo (max 2 letters)")
            .setView(dialogView)
            .setPositiveButton("Save") { d, _ ->
                safe("renameDot save") {
                    val newName = editText.text.toString().trim().take(2)
                    if (newName.isNotEmpty()) {
                        dot.name = newName
                        view.findViewById<TextView>(R.id.dotLabel).text = newName
                        persistDots()
                        d.dismiss()
                        ensureBubbleAndPanelVisible()
                        Toast.makeText(this@OverlayService, "✓ Renamed: $newName", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel") { d, _ -> safe("renameDot cancel") { d.dismiss() } }
            .create()

        safe("renameDot window type") { dialog.window?.setType(overlayType()) }
        safe("renameDot show") { dialog.show() }
    }

    private fun deleteDot(view: View, dot: TouchDot) = safe("deleteDot") {
        windowManager?.removeView(view)
        dotViews.remove(dot.id)
        dots.remove(dot)
        persistDots()
        ensureBubbleAndPanelVisible()
        Toast.makeText(this, "✓ Dot delete ho gaya", Toast.LENGTH_SHORT).show()
    }

    private fun persistDots() = safe("persistDots") {
        AppStore.saveDots(this, dots)
    }

    // ---------------- AI Run ----------------

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
            Toast.makeText(this, "⏳ AI analyze kar raha hai...", Toast.LENGTH_SHORT).show()

            // Hide everything only for a brief instant so dots/panel don't
            // appear IN the screenshot itself, then restore immediately.
            val wasDotsVisible = isPanelVisible
            setOverlayVisibility(View.INVISIBLE)

            val intent = Intent(this, ScreenCaptureService::class.java)
            intent.action = ScreenCaptureService.ACTION_CAPTURE_AND_ANALYZE
            intent.putExtra(ScreenCaptureService.EXTRA_DOTS_JSON, dotsToJsonString())
            intent.putExtra(ScreenCaptureService.EXTRA_PROMPT, AppStore.loadPrompt(this))
            ContextCompat.startForegroundService(this, intent)

            android.os.Handler(mainLooper).postDelayed({
                safe("restoreVisibility-afterCapture") {
                    if (wasDotsVisible) setOverlayVisibility(View.VISIBLE)
                }
            }, 400)
        }
    }

    fun restoreVisibilityNow() = safe("restoreVisibilityNow") {
        if (isPanelVisible) setOverlayVisibility(View.VISIBLE)
    }

    // ---------------- Pattern Match (separate, non-AI image matching) ----------------

    private fun runPatternMatch() {
        if (!ScreenCaptureService.isReady) {
            Toast.makeText(this, "❌ Screen capture ready nahi", Toast.LENGTH_LONG).show()
            return
        }
        safe("runPatternMatch") {
            Toast.makeText(this, "🔍 Pattern dhoond rahe hain...", Toast.LENGTH_SHORT).show()

            val wasVisible = isPanelVisible
            setOverlayVisibility(View.INVISIBLE)

            val intent = Intent(this, ScreenCaptureService::class.java)
            intent.action = ScreenCaptureService.ACTION_CAPTURE_FOR_PATTERN
            ContextCompat.startForegroundService(this, intent)

            android.os.Handler(mainLooper).postDelayed({
                safe("restorePatternVisibility") {
                    if (wasVisible) setOverlayVisibility(View.VISIBLE)
                }
            }, 400)
        }
    }

    fun onPatternMatchResult(result: PatternClient.PatternMatchResult) = safe("onPatternMatchResult") {
        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_pattern_result, null)

        val outcomeEmoji = when (result.outcomeHint) {
            "win" -> "✅"
            "loss" -> "❌"
            else -> "ℹ️"
        }
        dialogView.findViewById<TextView>(R.id.tvPatternSummary).text =
            "🔍 Match: ${result.similarityPercent}% — $outcomeEmoji ${result.outcomeHint} (${result.matchedReference})"

        result.annotatedImage?.let {
            dialogView.findViewById<android.widget.ImageView>(R.id.imgAnnotated).setImageBitmap(it)
        }
        val nextImgView = dialogView.findViewById<android.widget.ImageView>(R.id.imgNextCandles)
        val nextLabel = dialogView.findViewById<TextView>(R.id.tvNextCandlesLabel)
        if (result.nextCandlesImage != null) {
            nextImgView.setImageBitmap(result.nextCandlesImage)
        } else {
            nextLabel.text = "Aage kya hua: match chart ke aakhir tak pahunch gaya, aage kuch nahi hai"
            nextImgView.visibility = View.GONE
        }

        val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert)
            .setView(dialogView)
            .setPositiveButton("Band Karo", null)
            .create()

        safe("pattern result window type") { dialog.window?.setType(overlayType()) }
        safe("pattern result show") { dialog.show() }
    }

    fun onPatternMatchFailed(message: String) = safe("onPatternMatchFailed") {
        Toast.makeText(this, "❌ $message", Toast.LENGTH_LONG).show()
    }

    /**
     * Hides ONLY the given dot's own view briefly, so its overlay window
     * doesn't intercept the real tap gesture meant for the app underneath —
     * then shows it again shortly after. Everything else on screen (panel,
     * other dots) stays visible the whole time.
     */
    fun hideDotBrieflyForTap(dotName: String, durationMs: Long = 350) = safe("hideDotBrieflyForTap") {
        val dot = dots.find { it.name.equals(dotName, ignoreCase = true) } ?: return@safe
        val view = dotViews[dot.id] ?: return@safe
        val wasVisible = view.visibility == View.VISIBLE
        view.visibility = View.INVISIBLE
        android.os.Handler(mainLooper).postDelayed({
            safe("restoreDotAfterTap") { if (wasVisible) view.visibility = View.VISIBLE }
        }, durationMs)
    }

    private fun setOverlayVisibility(visibility: Int) {
        panelView?.visibility = visibility
        dotViews.values.forEach { it.visibility = visibility }
    }

    private fun setDotsVisibility(visibility: Int) {
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

    // ---------------- Foreground Notification ----------------

    private fun startForegroundNotif() = safe("startForegroundNotif") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "AI Touch Overlay", NotificationManager.IMPORTANCE_MIN)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Touch Active")
            .setContentText("Tap the floating bubble to open panel")
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
