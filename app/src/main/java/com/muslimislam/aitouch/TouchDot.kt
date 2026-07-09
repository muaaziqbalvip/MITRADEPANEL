package com.muslimislam.aitouch

import org.json.JSONObject

/**
 * Represents a single "touching dot" the user places on screen.
 * - name: short label shown INSIDE the dot (e.g. "s", "b") — also what the
 *   AI uses to refer to this dot when deciding actions
 * - x, y: absolute screen coordinates (pixels) of the dot's top-left corner
 * - sizeDp: diameter of the dot in dp — user can resize bigger/smaller
 * - locked: if true, dot cannot be dragged/resized anymore (fixed in place)
 */
data class TouchDot(
    var id: String,
    var name: String,
    var x: Float,
    var y: Float,
    var locked: Boolean = false,
    var sizeDp: Float = 48f
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("x", x.toDouble())
        o.put("y", y.toDouble())
        o.put("locked", locked)
        o.put("sizeDp", sizeDp.toDouble())
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): TouchDot {
            return TouchDot(
                id = o.getString("id"),
                name = o.getString("name"),
                x = o.getDouble("x").toFloat(),
                y = o.getDouble("y").toFloat(),
                locked = o.optBoolean("locked", false),
                sizeDp = o.optDouble("sizeDp", 48.0).toFloat()
            )
        }
    }
}
