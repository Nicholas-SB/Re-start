package com.example.custommusicplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.custommusicplayer.R
import com.example.custommusicplayer.data.AlbumArtLoader
import com.example.custommusicplayer.data.Song

/** The two tabs in the bottom navigation bar. */
private enum class Tab { SONGS, PLAYLISTS }

/**
 * The top-level screen: wires the ViewModels to the UI and decides which screen is shown.
 *
 * viewModel() returns the existing ViewModel if there is one (e.g. after rotation),
 * or creates it the first time.
 */
@Composable
fun MusicPlayerApp(
    libraryViewModel: LibraryViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel(),
    playlistViewModel: PlaylistViewModel = viewModel(),
) {
    // Turn each StateFlow into Compose state, so the UI redraws when it changes.
    val songs by libraryViewModel.songs.collectAsStateWithLifecycle()
    val isScanning by libraryViewModel.isScanning.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val positionMs by playerViewModel.positionMs.collectAsStateWithLifecycle()
    val playlists by playlistViewModel.playlists.collectAsStateWithLifecycle()

    // The player knows songs by id, playlists by file name. These maps find the full Song fast.
    // remember(songs) rebuilds them only when the song list changes.
    val songsById = remember(songs) { songs.associateBy { it.id } }
    val songsByFileName = remember(songs) { songs.associateBy { it.fileName } }
    val currentSong = playerState.currentSongId?.let { songsById[it] }

    // Navigation state. rememberSaveable keeps it across rotation.
    var tab by rememberSaveable { mutableStateOf(Tab.SONGS) }
    var openPlaylistId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }

    // Dialog state. songToAdd != null means "the Add to playlist dialog is open for this song".
    var songToAdd by remember { mutableStateOf<Song?>(null) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }

    // Back closes an open playlist instead of leaving the app.
    BackHandler(enabled = openPlaylistId != null) { openPlaylistId = null }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                Column {
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
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == Tab.SONGS,
                            onClick = { tab = Tab.SONGS; openPlaylistId = null },
                            icon = { Icon(painterResource(R.drawable.ic_library_music), contentDescription = null) },
                            label = { Text("Songs") },
                        )
                        NavigationBarItem(
                            selected = tab == Tab.PLAYLISTS,
                            onClick = { tab = Tab.PLAYLISTS; openPlaylistId = null },
                            icon = { Icon(painterResource(R.drawable.ic_playlist), contentDescription = null) },
                            label = { Text("Playlists") },
                        )
                    }
                }
            },
        ) { innerPadding ->
            AudioPermissionGate(modifier = Modifier.padding(innerPadding)) {
                // Runs once when this part of the UI first appears (i.e. once permission is granted).
                LaunchedEffect(Unit) { libraryViewModel.loadSongsOnce() }

                val contentModifier = Modifier.padding(innerPadding)
                // Copy to a local val so Kotlin knows it can't change between the check and the use.
                val playlistId = openPlaylistId
                when {
                    playlistId != null -> PlaylistDetailRoute(
                        playlistId = playlistId,
                        playlistViewModel = playlistViewModel,
                        playerViewModel = playerViewModel,
                        songsByFileName = songsByFileName,
                        currentSongId = playerState.currentSongId,
                        artLoader = libraryViewModel.artLoader,
                        onClose = { openPlaylistId = null },
                        modifier = contentModifier,
                    )
                    tab == Tab.SONGS -> LibraryScreen(
                        songs = songs,
                        isScanning = isScanning,
                        currentSongId = playerState.currentSongId,
                        artLoader = libraryViewModel.artLoader,
                        onRefresh = libraryViewModel::rescan,
                        onSongClick = { index -> playerViewModel.playQueue(songs, index) },
                        onShuffleAll = { playerViewModel.shuffleAll(songs) },
                        onAddToPlaylist = { song -> songToAdd = song },
                        modifier = contentModifier,
                    )
                    else -> PlaylistsScreen(
                        playlists = playlists,
                        onPlaylistClick = { id -> openPlaylistId = id },
                        onNewPlaylist = { songToAdd = null; showNewPlaylistDialog = true },
                        modifier = contentModifier,
                    )
                }
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

    // --- Dialogs ---

    val song = songToAdd
    if (song != null && !showNewPlaylistDialog) {
        AddToPlaylistDialog(
            playlists = playlists,
            onPlaylistChosen = { playlistId ->
                playlistViewModel.addSong(playlistId, song)
                songToAdd = null
            },
            onNewPlaylist = { showNewPlaylistDialog = true },  // keeps songToAdd, so it goes in the new playlist
            onDismiss = { songToAdd = null },
        )
    }
    if (showNewPlaylistDialog) {
        PlaylistNameDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = { name ->
                playlistViewModel.createPlaylist(name, firstSong = songToAdd)
                showNewPlaylistDialog = false
                songToAdd = null
            },
            onDismiss = {
                showNewPlaylistDialog = false
                songToAdd = null
            },
        )
    }
}

/** Loads one playlist from the database and shows it. */
@Composable
private fun PlaylistDetailRoute(
    playlistId: Long,
    playlistViewModel: PlaylistViewModel,
    playerViewModel: PlayerViewModel,
    songsByFileName: Map<String, Song>,
    currentSongId: Long?,
    artLoader: AlbumArtLoader,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // remember(playlistId) so we don't start a new database query on every redraw.
    val detail by remember(playlistId) { playlistViewModel.observePlaylist(playlistId) }
        .collectAsStateWithLifecycle(initialValue = null)

    // null while loading; nothing to show yet.
    val loaded = detail ?: return

    PlaylistDetailScreen(
        detail = loaded,
        songsByFileName = songsByFileName,
        currentSongId = currentSongId,
        artLoader = artLoader,
        onBack = onClose,
        onPlay = playerViewModel::playQueue,
        onShuffle = playerViewModel::shuffleAll,
        onRename = { name -> playlistViewModel.renamePlaylist(playlistId, name) },
        onDelete = {
            onClose()
            playlistViewModel.deletePlaylist(playlistId)
        },
        onRemoveEntry = playlistViewModel::removeEntry,
        onMoveEntry = { from, to -> playlistViewModel.moveEntry(playlistId, from, to) },
        modifier = modifier,
    )
}
