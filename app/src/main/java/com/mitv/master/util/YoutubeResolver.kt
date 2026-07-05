package com.mitv.master.util

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves a YouTube watch/video URL into a direct playable stream URL
 * so it can be fed into ExoPlayer alongside regular M3U/Xtream channels.
 *
 * Must call YoutubeDL.getInstance().init(context) once at app startup
 * (done in MitvApplication).
 */
object YoutubeResolver {

    suspend fun resolveDirectUrl(youtubeUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(youtubeUrl).apply {
                addOption("-f", "best[ext=mp4]/best")
                addOption("-g") // print direct URL only, don't download
            }
            val response = YoutubeDL.getInstance().execute(request)
            response.out.trim().lines().firstOrNull { it.startsWith("http") }
        } catch (e: Exception) {
            null
        }
    }
}
