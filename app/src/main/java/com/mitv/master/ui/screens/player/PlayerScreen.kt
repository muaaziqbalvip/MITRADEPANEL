package com.mitv.master.ui.screens.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import com.mitv.master.data.model.Channel
import com.mitv.master.viewmodel.PlayerViewModel

@Composable
fun PlayerScreen(
    channel: Channel,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val isBuffering by viewModel.isBuffering.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()

    androidx.compose.runtime.LaunchedEffect(channel.id) {
        viewModel.playChannel(channel)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.player
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        playbackError?.let {
            Text(
                text = "Playback error: $it",
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
