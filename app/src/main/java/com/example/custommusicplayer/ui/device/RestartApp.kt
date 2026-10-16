package com.example.custommusicplayer.ui.device

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.custommusicplayer.downloader.DownloadViewModel
import com.example.custommusicplayer.ui.AudioPermissionGate
import com.example.custommusicplayer.ui.LibraryViewModel
import com.example.custommusicplayer.ui.MusicPlayerApp
import com.example.custommusicplayer.ui.PlayerViewModel
import com.example.custommusicplayer.ui.theme.CustomMusicPlayerTheme
import kotlinx.coroutines.delay

/**
 * Re-start: the whole app as a little music player. A screen on top shows menus and
 * Now Playing; the click wheel below drives everything.
 *
 * This function owns the "what does the wheel do here?" decisions. The screen pieces
 * (MenuPage, DeviceNowPlaying) only draw, and the ClickWheel only reports gestures.
 */
@Composable
fun RestartApp(
    libraryViewModel: LibraryViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel(),
    downloadViewModel: DownloadViewModel = viewModel(),
    navigation: DeviceNavigationViewModel = viewModel(),
) {
    val songs by libraryViewModel.songs.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val positionMs by playerViewModel.positionMs.collectAsStateWithLifecycle()

    val songsById = remember(songs) { songs.associateBy { it.id } }
    val currentSong = playerState.currentSongId?.let { songsById[it] }

    // Temporary: the previous (YouTube Music-style) UI, for features not moved over yet.
    var showClassic by rememberSaveable { mutableStateOf(false) }
    var showLyrics by rememberSaveable { mutableStateOf(true) }

    // Volume bar: shown for a moment after each wheel turn on Now Playing.
    val context = LocalContext.current
    val volume = remember { VolumeControl(context) }
    var volumeFraction by remember { mutableStateOf<Float?>(null) }
    var lastVolumeChange by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lastVolumeChange) {
        if (lastVolumeChange == 0L) return@LaunchedEffect
        delay(1_500)  // a new change restarts this effect, so the bar stays while you keep turning
        volumeFraction = null
    }

    val acceleration = remember { WheelAcceleration() }

    // When the downloader saves a new song, rescan so it shows up right away.
    LaunchedEffect(Unit) {
        downloadViewModel.songDownloaded.collect { libraryViewModel.rescan() }
    }

    // --- What each page shows ---

    val page = navigation.currentPage
    val entries: List<MenuEntry> = when (page) {
        Page.MainMenu -> buildList {
            add(MenuEntry("Music", opensSubmenu = true) { navigation.open(Page.Music) })
            add(MenuEntry("Shuffle Songs") {
                playerViewModel.shuffleAll(songs)
                navigation.open(Page.NowPlaying)
            })
            if (currentSong != null) {
                add(MenuEntry("Now Playing", opensSubmenu = true) { navigation.open(Page.NowPlaying) })
            }
            add(MenuEntry("Classic view", detail = "Playlists, mixes, downloads", opensSubmenu = true) { showClassic = true })
        }
        Page.Music -> listOf(
            MenuEntry("Songs", detail = "${songs.size} ${if (songs.size == 1) "song" else "songs"}", opensSubmenu = true) {
                navigation.open(Page.Songs)
            },
        )
        Page.Songs -> songs.mapIndexed { index, song ->
            MenuEntry(song.title, detail = song.artist) {
                playerViewModel.playQueue(songs, index)
                navigation.open(Page.NowPlaying)
            }
        }
        Page.NowPlaying -> emptyList()
    }
    val title = when (page) {
        Page.MainMenu -> "Re-start"
        Page.Music -> "Music"
        Page.Songs -> "Songs"
        Page.NowPlaying -> "Now Playing"
    }

    // --- What the wheel does on each page ---

    // Careful: Compose may keep using the wheel callbacks from an earlier redraw (it decides
    // they "didn't change"). So the callbacks must not rely on values captured now, like
    // `page` and `entries` above. Instead they read the *current* page and entries when the
    // wheel actually turns. rememberUpdatedState gives a holder that always has the latest list.
    val currentEntries by rememberUpdatedState(entries)

    fun onRotate(steps: Int) {
        val page = navigation.currentPage
        if (page == Page.NowPlaying) {
            volume.change(steps)
            volumeFraction = volume.fraction
            lastVolumeChange = SystemClock.uptimeMillis()
        } else {
            val moved = acceleration.apply(steps, SystemClock.uptimeMillis())
            navigation.moveSelection(page, moved, currentEntries.size)
        }
    }

    fun onCenter() {
        val page = navigation.currentPage
        if (page == Page.NowPlaying) {
            showLyrics = !showLyrics
        } else {
            currentEntries.getOrNull(navigation.selectedIndex(page))?.onSelect?.invoke()
        }
    }

    // The phone's back gesture works like MENU.
    BackHandler(enabled = navigation.canGoBack && !showClassic) { navigation.back() }

    StatusBarIcons(darkIcons = !showClassic)

    Box(modifier = Modifier.fillMaxSize()) {
        DeviceTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(DeviceColors.BodyTop, DeviceColors.BodyBottom)))
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The screen: a cream panel in a mocha frame.
                val screenShape = RoundedCornerShape(10.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(screenShape)
                        .background(DeviceColors.Screen)
                        .border(3.dp, DeviceColors.ScreenFrame, screenShape)
                        .padding(3.dp),  // keep content inside the frame
                ) {
                    DeviceHeader(title = title, isPlaying = if (currentSong != null) playerState.isPlaying else null)
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AudioPermissionGate(modifier = Modifier.fillMaxSize()) {
                            LaunchedEffect(Unit) { libraryViewModel.loadSongsOnce() }

                            if (page == Page.NowPlaying) {
                                DeviceNowPlaying(
                                    song = currentSong,
                                    state = playerState,
                                    positionMs = positionMs,
                                    artLoader = libraryViewModel.artLoader,
                                    lyricsLoader = libraryViewModel.lyricsLoader,
                                    showLyrics = showLyrics,
                                    volumeFraction = volumeFraction,
                                    onSeek = playerViewModel::seekTo,
                                )
                            } else {
                                MenuPage(
                                    entries = entries,
                                    selectedIndex = navigation.selectedIndex(page).coerceAtMost(entries.lastIndex.coerceAtLeast(0)),
                                    onTap = { index ->
                                        navigation.select(page, index)
                                        entries[index].onSelect()
                                    },
                                    emptyText = if (page == Page.Songs) "No songs yet" else "Nothing here",
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                ClickWheel(
                    onRotate = ::onRotate,
                    onCenter = ::onCenter,
                    onMenu = { navigation.back() },
                    onPrevious = playerViewModel::skipToPrevious,
                    onNext = playerViewModel::skipToNext,
                    onPlayPause = playerViewModel::togglePlayPause,
                    modifier = Modifier.fillMaxWidth(0.8f),
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        if (showClassic) {
            // Registered before the classic UI's own back handlers, so those win while they're
            // active (e.g. closing its Now Playing first); otherwise back returns to the device.
            BackHandler { showClassic = false }
            CustomMusicPlayerTheme { MusicPlayerApp() }
        }
    }
}

/**
 * Dark status/navigation bar icons on our light body, light ones on the dark classic UI.
 * SideEffect runs after every successful redraw, to sync something outside Compose.
 */
@Composable
private fun StatusBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
    }
}
