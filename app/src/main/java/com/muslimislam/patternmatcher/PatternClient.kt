package com.muslimislam.patternmatcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Talks to the Pattern Analyzer backend (pure image matching, no AI).
 * This app is fully standalone — no connection to any other app.
 */
object PatternClient {

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
     * Sends the image to the backend on a background thread and calls
     * onResult(result, errorMessage) on the main thread when done.
     * Exactly one of (result, errorMessage) will be non-null.
     */
    fun analyzePattern(
        context: Context,
        backendUrl: String,
        image: Bitmap,
        onResult: (PatternMatchResult?, String?) -> Unit
    ) {
        val url = backendUrl.trimEnd('/') + "/analyze_pattern"

        val body = JSONObject().apply {
            put("image_base64", bitmapToBase64(image))
        }
        val requestBody = body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()

        Thread {
            try {
                client.newCall(request).execute().use { response ->
                    val responseText = response.body?.string()
                    if (responseText == null) {
                        postResult(context, onResult, null, "Khali response mila")
                        return@use
                    }
                    val json = JSONObject(responseText)

                    if (!response.isSuccessful || json.has("error")) {
                        val errorDetail = json.optString("error", "Backend error ${response.code}")
                        postResult(context, onResult, null, errorDetail)
                        return@use
                    }

                    val matchedRef = json.optString("matched_reference", "")
                    if (matchedRef.isBlank()) {
                        postResult(context, onResult, null, "Reference library khali hai")
                        return@use
                    }

                    val annotatedB64 = json.optString("annotated_image_base64", "")
                    val nextB64 = json.optString("next_candles_image_base64", "")

                    val result = PatternMatchResult(
                        matchedReference = matchedRef,
                        similarityPercent = json.optDouble("similarity_percent", 0.0),
                        outcomeHint = json.optString("outcome_hint", "unknown"),
                        annotatedImage = if (annotatedB64.isNotBlank()) base64ToBitmap(annotatedB64) else null,
                        nextCandlesImage = if (nextB64.isNotBlank()) base64ToBitmap(nextB64) else null
                    )
                    postResult(context, onResult, result, null)
                }
            } catch (e: Exception) {
                postResult(context, onResult, null, "Network error: ${e.message}")
            }
        }.start()
    }

    private fun postResult(
        context: Context,
        onResult: (PatternMatchResult?, String?) -> Unit,
        result: PatternMatchResult?,
        error: String?
    ) {
        android.os.Handler(context.mainLooper).post {
            onResult(result, error)
        }
    }
}
