package com.muslimislam.aitouch

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Performs a REAL tap on screen at the given dot's position.
 * Dot center is computed from the dot's own sizeDp (supports resized dots).
 */
class TapAccessibilityService : AccessibilityService() {

    companion object {
        var instance: TapAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** Looks up the dot by name and taps its center. Restores the overlay afterward. */
    fun tapDotByName(dotName: String) {
        val dots = AppStore.loadDots(this)
        val dot = dots.find { it.name.equals(dotName, ignoreCase = true) }

        if (dot == null) {
            showActionToast("❌ Dot '$dotName' nahi mila")
            OverlayService.instance?.restoreVisibilityNow()
            return
        }

        val halfDotPx = (dot.sizeDp / 2f) * resources.displayMetrics.density
        val x = dot.x + halfDotPx
        val y = dot.y + halfDotPx

        android.os.Handler(mainLooper).post {
            performTap(x, y)
            showActionToast("✓ ${dot.name}")

            // Give the tap time to actually register before showing the
            // overlay again — otherwise the dots' own windows can steal it.
            android.os.Handler(mainLooper).postDelayed({
                OverlayService.instance?.restoreVisibilityNow()
            }, 500)
        }
    }

    private fun performTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                showActionToast("⚠️ Tap cancelled")
            }
        }, null)
        if (!dispatched) {
            showActionToast("❌ Tap dispatch failed — check Accessibility is ON")
        }
    }

    private fun showActionToast(msg: String) {
        android.os.Handler(mainLooper).post {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
