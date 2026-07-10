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
 * The backend also keeps its own history server-side to improve future
 * decisions, and verifies an access PIN set as an HF Space secret.
 */
object BackendClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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

    private fun postToast(context: Context, msg: String) {
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
}
