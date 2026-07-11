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
 * Backend returns the simplest possible shape: just a dot name.
 * {"dot": "b"} means tap the dot named "b". {"dot": ""} means do nothing.
 * Also carries a "description" field (the AI's plain-text visual read of
 * the chart) which the app can later send back via sendFeedback() once the
 * trade result (win/loss) is known, so the backend can flag similar losing
 * setups in future analyses.
 */
object BackendClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Kept in memory so the Feedback button can reference the most recent
     *  analysis without the caller needing to thread it through manually. */
    var lastDescription: String? = null
        private set
    var lastDotChosen: String? = null
        private set

    /** Derives the base URL (without /analyze) from whatever the user saved. */
    private fun baseUrl(backendUrl: String): String {
        return backendUrl.removeSuffix("/analyze").removeSuffix("/")
    }

    /**
     * Verifies the PIN against the backend's /verify_pin endpoint.
     * Returns true if valid (or if the server has no PIN configured).
     */
    fun verifyPin(context: Context, pin: String, onResult: (Boolean, String?) -> Unit) {
        val backendUrl = AppStore.loadBackendUrl(context)
        if (backendUrl.isBlank()) {
            onResult(false, "Backend URL set nahi hai")
            return
        }
        val url = baseUrl(backendUrl) + "/verify_pin"

        val body = JSONObject().apply { put("pin", pin) }
        val requestBody = body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    val responseText = response.body?.string() ?: ""
                    val json = try { JSONObject(responseText) } catch (_: Exception) { JSONObject() }
                    val valid = json.optBoolean("valid", false)
                    android.os.Handler(context.mainLooper).post {
                        onResult(valid, if (!valid) "Galat PIN" else null)
                    }
                }
            } catch (e: Exception) {
                android.os.Handler(context.mainLooper).post {
                    onResult(false, "Network error: ${e.message}")
                }
            }
        }.start()
    }

    /** Returns the dot name to tap, or null if no action / an error occurred. */
    fun analyze(context: Context, imageBase64: String, dotsJson: String, prompt: String): String? {
        val backendUrl = AppStore.loadBackendUrl(context)
        val groqKey = AppStore.loadGroqKey(context)
        val pin = AppStore.loadPin(context)

        if (backendUrl.isBlank()) return null

        val body = JSONObject().apply {
            put("image_base64", imageBase64)
            put("dots", JSONArray(dotsJson))
            put("prompt", prompt)
            put("groq_key", groqKey)
            put("pin", pin)
        }

        val requestBody = body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(backendUrl)
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string() ?: return null
                val json = JSONObject(responseText)

                if (!response.isSuccessful) {
                    val errorDetail = json.optString("error", "")
                    val msg = if (errorDetail.isNotBlank())
                        "Backend error ${response.code}: $errorDetail"
                    else
                        "Backend error: ${response.code}"
                    postToast(context, msg)
                    return null
                }

                val dotName = json.optString("dot", "")
                val debugRaw = json.optString("_debug_raw", "")
                lastDescription = json.optString("description", "").ifBlank { null }
                lastDotChosen = dotName.ifBlank { null }

                if (debugRaw.isNotBlank()) {
                    postToast(context, "🤖 AI: $debugRaw")
                }

                if (dotName.isBlank()) {
                    postToast(context, "ℹ️ AI: koi action nahi")
                    return null
                }

                dotName
            }
        } catch (e: Exception) {
            postToast(context, "Network error: ${e.message}")
            null
        }
    }

    /**
     * Reports a trade result (win/loss) for the most recent analysis back
     * to the backend, so it can warn about similar losing chart setups in
     * future analyses. Safe to call even if there's no recent analysis —
     * it'll just show a message and do nothing.
     */
    fun sendFeedback(context: Context, result: String, onDone: (Boolean, String?) -> Unit) {
        val description = lastDescription
        if (description.isNullOrBlank()) {
            onDone(false, "Koi recent analysis nahi mila")
            return
        }

        val backendUrl = AppStore.loadBackendUrl(context)
        val pin = AppStore.loadPin(context)
        val url = baseUrl(backendUrl) + "/feedback"

        val body = JSONObject().apply {
            put("description", description)
            put("result", result)
            put("dot", lastDotChosen ?: "")
            put("pin", pin)
        }
        val requestBody = body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    val ok = response.isSuccessful
                    android.os.Handler(context.mainLooper).post {
                        onDone(ok, if (!ok) "Feedback save nahi hua (${response.code})" else null)
                    }
                }
            } catch (e: Exception) {
                android.os.Handler(context.mainLooper).post {
                    onDone(false, "Network error: ${e.message}")
                }
            }
        }.start()
    }

    /** Fetches simple win/loss stats from the backend, e.g. for display. */
    fun fetchStats(context: Context, onResult: (wins: Int, losses: Int, winRate: Double) -> Unit) {
        val backendUrl = AppStore.loadBackendUrl(context)
        val url = baseUrl(backendUrl) + "/feedback/stats"
        val request = Request.Builder().url(url).get().build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    val responseText = response.body?.string() ?: ""
                    val json = try { JSONObject(responseText) } catch (_: Exception) { JSONObject() }
                    val wins = json.optInt("wins", 0)
                    val losses = json.optInt("losses", 0)
                    val winRate = json.optDouble("win_rate_percent", 0.0)
                    android.os.Handler(context.mainLooper).post {
                        onResult(wins, losses, winRate)
                    }
                }
            } catch (_: Exception) {
                android.os.Handler(context.mainLooper).post { onResult(0, 0, 0.0) }
            }
        }.start()
    }

    private fun postToast(context: Context, msg: String) {
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
}
