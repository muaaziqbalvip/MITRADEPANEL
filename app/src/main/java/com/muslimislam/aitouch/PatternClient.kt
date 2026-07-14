package com.muslimislam.aitouch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Talks to the SEPARATE Pattern Analyzer HF Space (pure image matching,
 * no AI). Completely independent from BackendClient.kt, which talks to
 * the main AI trading-decision backend.
 */
object PatternClient {

    const val PATTERN_ANALYZER_URL = "https://muaaznamtosonahoga1-miaibot.hf.space"

    data class PatternMatchResult(
        val matchedReference: String,
        val similarityPercent: Double,
        val outcomeHint: String,
        val annotatedImage: Bitmap?,
        val nextCandlesImage: Bitmap?
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun base64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Sends a screenshot to the Pattern Analyzer and returns the best match,
     * or null if something failed (a Toast explains what).
     */
    fun analyzePattern(context: Context, screenshot: Bitmap): PatternMatchResult? {
        val url = PATTERN_ANALYZER_URL.trimEnd('/') + "/analyze_pattern"

        val body = JSONObject().apply {
            put("image_base64", bitmapToBase64(screenshot))
        }

        val requestBody = body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string() ?: return null
                val json = JSONObject(responseText)

                if (!response.isSuccessful || json.has("error")) {
                    val errorDetail = json.optString("error", "Backend error ${response.code}")
                    postToast(context, "❌ Pattern Analyzer: $errorDetail")
                    return null
                }

                val matchedRef = json.optString("matched_reference", "")
                if (matchedRef.isBlank()) {
                    postToast(context, "❌ Reference library khali hai")
                    return null
                }

                val annotatedB64 = json.optString("annotated_image_base64", "")
                val nextB64 = json.optString("next_candles_image_base64", "")

                PatternMatchResult(
                    matchedReference = matchedRef,
                    similarityPercent = json.optDouble("similarity_percent", 0.0),
                    outcomeHint = json.optString("outcome_hint", "unknown"),
                    annotatedImage = if (annotatedB64.isNotBlank()) base64ToBitmap(annotatedB64) else null,
                    nextCandlesImage = if (nextB64.isNotBlank()) base64ToBitmap(nextB64) else null
                )
            }
        } catch (e: Exception) {
            postToast(context, "Network error: ${e.message}")
            null
        }
    }

    /**
     * Uploads a new reference chart to grow the library. outcome: "win",
     * "loss", or "" (unknown). Calls onDone(success, message) on completion.
     */
    fun uploadReference(
        context: Context,
        screenshot: Bitmap,
        filename: String,
        pin: String,
        outcome: String,
        onDone: (Boolean, String?) -> Unit
    ) {
        val url = PATTERN_ANALYZER_URL.trimEnd('/') + "/upload_reference"

        val stream = ByteArrayOutputStream()
        screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val imageBytes = stream.toByteArray()

        val requestBody = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("pin", pin)
            .addFormDataPart("outcome", outcome)
            .addFormDataPart(
                "file", filename,
                imageBytes.toRequestBody("image/png".toMediaType())
            )
            .build()

        val request = Request.Builder().url(url).post(requestBody).build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    val responseText = response.body?.string() ?: ""
                    val json = try { JSONObject(responseText) } catch (_: Exception) { JSONObject() }
                    val ok = response.isSuccessful
                    android.os.Handler(context.mainLooper).post {
                        onDone(ok, if (!ok) json.optString("error", "Upload failed") else null)
                    }
                }
            } catch (e: Exception) {
                android.os.Handler(context.mainLooper).post {
                    onDone(false, "Network error: ${e.message}")
                }
            }
        }.start()
    }

    private fun postToast(context: Context, msg: String) {
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
}
