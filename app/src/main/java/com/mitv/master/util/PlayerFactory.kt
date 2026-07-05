package com.mitv.master.util

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.datasource.DefaultDataSource
import com.mitv.master.data.model.Channel
import com.mitv.master.data.model.SourceType

/**
 * Builds an ExoPlayer instance tuned for IPTV playback on slow/unstable networks:
 * - Small min buffer so playback starts fast
 * - Larger max buffer to absorb network jitter once playing
 * - Aggressive rebuffer-after-stall thresholds
 */
object PlayerFactory {

    fun buildPlayer(context: Context): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 5_000,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
    }

    fun buildMediaSource(context: Context, channel: Channel): MediaSource {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(8_000)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaItem = MediaItem.fromUri(resolvedUrl(channel))

        return when (channel.sourceType) {
            SourceType.M3U8, SourceType.XTREAM -> {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }
            else -> {
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }
        }
    }

    /**
     * For YOUTUBE source type, streamUrl should already be resolved to a direct
     * playable URL upstream (via YoutubeResolver) before reaching the player.
     */
    private fun resolvedUrl(channel: Channel): String = channel.streamUrl
}
