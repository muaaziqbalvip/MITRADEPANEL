package com.muslimislam.aitouch

import org.json.JSONObject

/**
 * Represents a single "touching dot" the user places on screen.
 * - name: user-given label (AI will refer to this name to decide actions)
 * - x, y: absolute screen coordinates (pixels)
 * - locked: if true, dot cannot be dragged anymore (fixed in place)
 */
data class TouchDot(
    var id: String,
    var name: String,
    var x: Float,
    var y: Float,
    var locked: Boolean = false
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("x", x)
        o.put("y", y)
        o.put("locked", locked)
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): TouchDot {
            return TouchDot(
                id = o.getString("id"),
                name = o.getString("name"),
                x = o.getDouble("x").toFloat(),
                y = o.getDouble("y").toFloat(),
                locked = o.optBoolean("locked", false)
            )
        }
    }
}
