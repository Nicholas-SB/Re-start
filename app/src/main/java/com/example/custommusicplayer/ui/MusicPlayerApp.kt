package com.example.custommusicplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * The top-level screen: wires the ViewModels to the UI.
 *
 * viewModel() returns the existing ViewModel if there is one (e.g. after rotation),
 * or creates it the first time.
 */
@Composable
fun MusicPlayerApp(
    libraryViewModel: LibraryViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel(),
) {
    // Turn each StateFlow into Compose state, so the UI redraws when it changes.
    val songs by libraryViewModel.songs.collectAsStateWithLifecycle()
    val isScanning by libraryViewModel.isScanning.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val positionMs by playerViewModel.positionMs.collectAsStateWithLifecycle()

    // The player only knows song ids; this map finds the full Song for an id quickly.
    // remember(songs) rebuilds it only when the song list changes.
    val songsById = remember(songs) { songs.associateBy { it.id } }
    val currentSong = playerState.currentSongId?.let { songsById[it] }

    var showNowPlaying by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (currentSong != null) {
                    MiniPlayer(
                        song = currentSong,
                        isPlaying = playerState.isPlaying,
                        artLoader = libraryViewModel.artLoader,
                        onClick = { showNowPlaying = true },
                        onTogglePlayPause = playerViewModel::togglePlayPause,  // a function reference
                        onSkipNext = playerViewModel::skipToNext,
                    )
                }
            },
        ) { innerPadding ->
            AudioPermissionGate(modifier = Modifier.padding(innerPadding)) {
                // Runs once when this part of the UI first appears (i.e. once permission is granted).
                LaunchedEffect(Unit) { libraryViewModel.loadSongsOnce() }

                LibraryScreen(
                    songs = songs,
                    isScanning = isScanning,
                    currentSongId = playerState.currentSongId,
                    artLoader = libraryViewModel.artLoader,
                    onRefresh = libraryViewModel::rescan,
                    onSongClick = { index -> playerViewModel.playQueue(songs, index) },
                    onShuffleAll = { playerViewModel.shuffleAll(songs) },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }

        // The Now Playing screen slides up over everything else.
        AnimatedVisibility(
            visible = showNowPlaying && currentSong != null,
            enter = slideInVertically { fullHeight -> fullHeight },
            exit = slideOutVertically { fullHeight -> fullHeight },
        ) {
            // The system back button/gesture closes it instead of leaving the app.
            BackHandler { showNowPlaying = false }

            // currentSong can briefly be null while the screen slides away.
            currentSong?.let { song ->
                NowPlayingScreen(
                    song = song,
                    state = playerState,
                    positionMs = positionMs,
                    songsById = songsById,
                    artLoader = libraryViewModel.artLoader,
                    onClose = { showNowPlaying = false },
                    onTogglePlayPause = playerViewModel::togglePlayPause,
                    onSkipNext = playerViewModel::skipToNext,
                    onSkipPrevious = playerViewModel::skipToPrevious,
                    onSeek = playerViewModel::seekTo,
                    onToggleShuffle = playerViewModel::toggleShuffle,
                    onCycleRepeat = playerViewModel::cycleRepeatMode,
                    onQueueItemClick = playerViewModel::skipToQueueItem,
                )
            }
        }
    }
}
