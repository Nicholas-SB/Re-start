package com.example.custommusicplayer.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
    val currentSongId by playerViewModel.currentSongId.collectAsStateWithLifecycle()
    val isPlaying by playerViewModel.isPlaying.collectAsStateWithLifecycle()

    // The player only knows the song's id; look up the full Song in the library.
    val currentSong = songs.find { it.id == currentSongId }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (currentSong != null) {
                MiniPlayer(
                    song = currentSong,
                    isPlaying = isPlaying,
                    artLoader = libraryViewModel.artLoader,
                    onTogglePlayPause = playerViewModel::togglePlayPause,  // a function reference
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
                currentSongId = currentSongId,
                artLoader = libraryViewModel.artLoader,
                onRefresh = libraryViewModel::rescan,
                onSongClick = playerViewModel::play,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}
