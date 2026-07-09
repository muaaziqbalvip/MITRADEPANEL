package com.muslimislam.aitouch

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Performs REAL taps/gestures on screen based on AI decisions.
 * Dot centers are computed from each dot's own sizeDp (not a fixed 22dp),
 * so resized dots still get tapped exactly in their visual center.
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

    fun performActions(actions: List<AiAction>) {
        if (actions.isEmpty()) {
            OverlayService.instance?.restoreVisibilityNow()
            return
        }
        val dots = AppStore.loadDots(this)
        android.os.Handler(mainLooper).post {
            executeSequentially(actions, dots, 0)
        }
    }

    private fun executeSequentially(actions: List<AiAction>, dots: List<TouchDot>, index: Int) {
        if (index >= actions.size) {
            OverlayService.instance?.restoreVisibilityNow()
            return
        }
        val action = actions[index]

        val resolvedPoint = resolvePoint(action, dots)
        val matchedDot = action.dotId?.let { id ->
            dots.find { it.id == id || it.name.equals(id, ignoreCase = true) }
        }

        when (action.type) {
            "tap" -> {
                if (resolvedPoint != null) {
                    performTap(resolvedPoint.first, resolvedPoint.second)
                    showActionToast("✓ Tap: ${matchedDot?.name ?: action.dotId ?: ""}")
                }
            }
            "long_press" -> {
                if (resolvedPoint != null) {
                    performLongPress(resolvedPoint.first, resolvedPoint.second)
                    showActionToast("✓ Long-press: ${matchedDot?.name ?: action.dotId ?: ""}")
                }
            }
            "swipe" -> {
                if (resolvedPoint != null && action.toX != null && action.toY != null) {
                    performSwipe(resolvedPoint.first, resolvedPoint.second, action.toX, action.toY)
                    showActionToast("✓ Swipe: ${matchedDot?.name ?: action.dotId ?: ""}")
                }
            }
            "type_text" -> {
                if (resolvedPoint != null && action.text != null) {
                    performTap(resolvedPoint.first, resolvedPoint.second)
                    showActionToast("✓ Type '${action.text}': ${matchedDot?.name ?: action.dotId ?: ""}")
                    android.os.Handler(mainLooper).postDelayed({ typeTextAtFocus(action.text) }, 400)
                }
            }
            "none" -> { /* AI decided no action needed */ }
        }

        android.os.Handler(mainLooper).postDelayed({
            executeSequentially(actions, dots, index + 1)
        }, 700)
    }

    private fun resolvePoint(action: AiAction, dots: List<TouchDot>): Pair<Float, Float>? {
        if (action.dotId != null) {
            val dot = dots.find { it.id == action.dotId || it.name.equals(action.dotId, ignoreCase = true) }
            if (dot != null) {
                // Use the dot's own current size (it may have been resized by the user)
                // to find its true visual center, converted to real pixels.
                val halfDotPx = (dot.sizeDp / 2f) * resources.displayMetrics.density
                return Pair(dot.x + halfDotPx, dot.y + halfDotPx)
            }
        }
        if (action.x != null && action.y != null) return Pair(action.x, action.y)
        return null
    }

    private fun performTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                showActionToast("⚠️ Gesture cancelled at (${x.toInt()},${y.toInt()})")
            }
        }, null)
        if (!dispatched) {
            showActionToast("❌ Gesture dispatch failed — check Accessibility is ON")
        }
    }

    private fun performLongPress(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 700))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun showActionToast(msg: String) {
        android.os.Handler(mainLooper).post {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun typeTextAtFocus(text: String) {
        val focused = findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
            val arguments = android.os.Bundle()
            arguments.putCharSequence(
                android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
            focused.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } else {
            Toast.makeText(this, "Text field focus nahi mila", Toast.LENGTH_SHORT).show()
        }
    }
}
