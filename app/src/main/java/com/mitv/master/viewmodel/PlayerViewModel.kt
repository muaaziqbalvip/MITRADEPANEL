package com.mitv.master.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mitv.master.data.model.Channel
import com.mitv.master.data.model.SourceType
import com.mitv.master.util.PlayerFactory
import com.mitv.master.util.YoutubeResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    val player: ExoPlayer = PlayerFactory.buildPlayer(context)

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _playbackError.value = error.message
            }
        })
    }

    fun playChannel(channel: Channel) {
        viewModelScope.launch {
            _playbackError.value = null
            val resolvedChannel = if (channel.sourceType == SourceType.YOUTUBE) {
                val direct = YoutubeResolver.resolveDirectUrl(channel.streamUrl)
                if (direct != null) channel.copy(streamUrl = direct) else channel
            } else {
                channel
            }

            val mediaSource = PlayerFactory.buildMediaSource(context, resolvedChannel)
            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
        }
    }

    fun selectAudioTrack(trackIndex: Int) {
        // Track group selection handled via player.trackSelectionParameters
        // Left as an extension point for multi-audio IPTV streams.
    }

    override fun onCleared() {
        super.onCleared()
        player.release()
    }
}
