package com.example.custommusicplayer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.custommusicplayer.R

/**
 * Milestone 1: pick one audio file with the system file picker and play/pause it.
 *
 * The player lives inside this composable for now. In milestone 3 it moves into a
 * MediaSessionService so music keeps playing in the background.
 */
@Composable
fun PlayerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // remember { } keeps the same ExoPlayer instance across recompositions,
    // instead of building a new one every time the UI redraws.
    val player = remember { ExoPlayer.Builder(context).build() }

    // UI state. When these change, Compose redraws the parts of the UI that read them.
    var hasSong by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf<String?>(null) }
    var artist by remember { mutableStateOf<String?>(null) }

    // Listen to the player, and release it when this screen leaves the composition.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            // ExoPlayer reads the tags embedded in the .m4a (title, artist, cover art, ...).
            override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                title = metadata.title?.toString()
                artist = metadata.artist?.toString()
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Opens Android's file picker. No storage permission is needed: the user grants
    // access to the one file they pick.
    val pickAudioFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            hasSong = true
            title = null
            artist = null
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.play()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title ?: if (hasSong) "Unknown title" else "No song selected",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = artist ?: "",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        FilledIconButton(
            onClick = { if (player.isPlaying) player.pause() else player.play() },
            enabled = hasSong,
            modifier = Modifier.size(72.dp)
        ) {
            Icon(
                painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = { pickAudioFile.launch(arrayOf("audio/*")) }) {
            Text("Pick a song")
        }
    }
}
