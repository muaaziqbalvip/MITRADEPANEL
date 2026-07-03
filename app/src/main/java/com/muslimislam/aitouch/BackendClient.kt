package com.muslimislam.aitouch

import android.content.Context
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * A single AI-decided action to perform on screen.
 * type: "tap" | "long_press" | "type_text" | "swipe" | "none"
 */
data class AiAction(
    val type: String,
    val dotId: String? = null,
    val x: Float? = null,
    val y: Float? = null,
    val text: String? = null,
    val toX: Float? = null,
    val toY: Float? = null
)

object BackendClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Sends screenshot (base64 JPEG), current dots, and the saved one-time prompt
     * to the HF Space backend. Expects a JSON response describing which actions
     * to perform, referencing dots by id/name or by raw coordinates.
     *
     * Expected backend response shape:
     * {
     *   "actions": [
     *     {"type": "tap", "dot_id": "xxxx"},
     *     {"type": "type_text", "dot_id": "yyyy", "text": "MashaAllah"}
     *   ]
     * }
     */
    fun analyze(context: Context, imageBase64: String, dotsJson: String, prompt: String): List<AiAction>? {
        val backendUrl = AppStore.loadBackendUrl(context)
        val groqKey = AppStore.loadGroqKey(context)

        if (backendUrl.isBlank()) return null

        val body = JSONObject().apply {
            put("image_base64", imageBase64)
            put("dots", JSONArray(dotsJson))
            put("prompt", prompt)
            put("groq_key", groqKey)
        }

        val requestBody = body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(backendUrl)
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    postToast(context, "Backend error: ${response.code}")
                    return null
                }
                val responseText = response.body?.string() ?: return null
                parseActions(responseText)
            }
        } catch (e: Exception) {
            postToast(context, "Network error: ${e.message}")
            null
        }
    }

    private fun parseActions(json: String): List<AiAction> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("actions") ?: return emptyList()
        val list = mutableListOf<AiAction>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                AiAction(
                    type = o.optString("type", "none"),
                    dotId = o.optString("dot_id", null),
                    x = if (o.has("x")) o.getDouble("x").toFloat() else null,
                    y = if (o.has("y")) o.getDouble("y").toFloat() else null,
                    text = o.optString("text", null),
                    toX = if (o.has("to_x")) o.getDouble("to_x").toFloat() else null,
                    toY = if (o.has("to_y")) o.getDouble("to_y").toFloat() else null
                )
            )
        }
        return list
    }

    private fun postToast(context: Context, msg: String) {
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
}
