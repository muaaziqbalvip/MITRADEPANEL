package com.muslimislam.aitouch

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Foreground service that holds the MediaProjection permission and, on demand,
 * captures a single screenshot, then hands it off to BackendClient which sends
 * it to the HF Space (Groq vision brain) along with dots + saved prompt.
 */
class ScreenCaptureService : Service() {

    companion object {
        const val CHANNEL_ID = "ai_touch_capture"
        const val NOTIF_ID = 1002
        const val ACTION_START = "action_start"
        const val ACTION_CAPTURE_AND_ANALYZE = "action_capture_and_analyze"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_DOTS_JSON = "dots_json"
        const val EXTRA_PROMPT = "prompt"

        var isReady = false
        private var projection: MediaProjection? = null
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotif()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity_RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (data != null) {
                    val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    projection = mpm.getMediaProjection(resultCode, data)
                    isReady = true
                }
            }
            ACTION_CAPTURE_AND_ANALYZE -> {
                val dotsJson = intent.getStringExtra(EXTRA_DOTS_JSON) ?: "[]"
                val prompt = intent.getStringExtra(EXTRA_PROMPT) ?: ""
                captureAndSend(dotsJson, prompt)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Avoid pulling in android.app.Activity just for this constant
    private val Activity_RESULT_CANCELED = 0

    private fun captureAndSend(dotsJson: String, prompt: String) {
        val proj = projection ?: return
        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = proj.createVirtualDisplay(
            "AITouchCapture",
            width, height, density,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        // Small delay to let a frame render
        android.os.Handler(mainLooper).postDelayed({
            val image = imageReader?.acquireLatestImage()
            if (image != null) {
                val bitmap = imageToBitmap(image, width, height)
                image.close()
                val base64 = bitmapToBase64(bitmap)

                CoroutineScope(Dispatchers.IO).launch {
                    val result = BackendClient.analyze(
                        context = this@ScreenCaptureService,
                        imageBase64 = base64,
                        dotsJson = dotsJson,
                        prompt = prompt
                    )
                    result?.let { actions ->
                        TapAccessibilityService.instance?.performActions(actions)
                    }
                }
            }
            virtualDisplay?.release()
            imageReader?.close()
        }, 300)
    }

    private fun imageToBitmap(image: android.media.Image, width: Int, height: Int): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        return android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
    }

    private fun startForegroundNotif() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "AI Touch Screen Capture", NotificationManager.IMPORTANCE_MIN
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Touch — Screen Capture Ready")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        isReady = false
    }
}
