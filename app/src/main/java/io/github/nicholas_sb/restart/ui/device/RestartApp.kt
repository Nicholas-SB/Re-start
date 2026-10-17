package io.github.nicholas_sb.restart.ui.device

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.nicholas_sb.restart.data.mixes.RadioMixMaker
import io.github.nicholas_sb.restart.data.mixes.SongMixMaker
import io.github.nicholas_sb.restart.data.AppMusicLibrary
import io.github.nicholas_sb.restart.data.Song
import io.github.nicholas_sb.restart.downloader.DownloadScreen
import io.github.nicholas_sb.restart.downloader.DownloadViewModel
import io.github.nicholas_sb.restart.downloader.PlaylistChoice
import io.github.nicholas_sb.restart.downloader.PlaylistImportScreen
import io.github.nicholas_sb.restart.ui.AudioPermissionGate
import io.github.nicholas_sb.restart.ui.DiscoverViewModel
import io.github.nicholas_sb.restart.ui.LibraryViewModel
import io.github.nicholas_sb.restart.ui.PlayerViewModel
import io.github.nicholas_sb.restart.ui.PlaylistViewModel
import io.github.nicholas_sb.restart.ui.StatsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.flowOf

/**
 * Re-start: the whole app as a little music player. A screen on top shows menus and
 * Now Playing; the click wheel below drives everything.
 *
 * This function owns the "what does the wheel do here?" decisions and the layout.
 * DeviceMenus decides what each menu lists; MenuPage, DeviceNowPlaying, NameEntryPage and
 * the downloader's screen only draw; the ClickWheel only reports gestures.
 */
@Composable
fun RestartApp(
    libraryViewModel: LibraryViewModel = viewModel(),
    playerViewModel: PlayerViewModel = viewModel(),
    playlistViewModel: PlaylistViewModel = viewModel(),
    statsViewModel: StatsViewModel = viewModel(),
    downloadViewModel: DownloadViewModel = viewModel(),
    discoverViewModel: DiscoverViewModel = viewModel(),
    navigation: DeviceNavigationViewModel = viewModel(),
) {
    val songs by libraryViewModel.songs.collectAsStateWithLifecycle()
    val musicFolder by libraryViewModel.musicFolder.collectAsStateWithLifecycle()
    val folderChoices by libraryViewModel.folderChoices.collectAsStateWithLifecycle()
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val positionMs by playerViewModel.positionMs.collectAsStateWithLifecycle()
    val playlists by playlistViewModel.playlists.collectAsStateWithLifecycle()
    val stats by statsViewModel.stats.collectAsStateWithLifecycle()

    val songsById = remember(songs) { songs.associateBy { it.id } }
    val songsByFileName = remember(songs) { songs.associateBy { it.fileName } }
    // A streamed song (in a mix) isn't in the library; the player state carries it instead.
    val currentSong = playerState.currentSongId?.let { songsById[it] } ?: playerState.currentStreamedSong

    val page = navigation.currentPage

    // If the current page is about one playlist, load that playlist's songs from the database.
    val shownPlaylistId: Long? = when (page) {
        is Page.Playlist -> page.playlistId
        is Page.PlaylistOptions -> page.playlistId
        is Page.PlaylistEntryOptions -> page.playlistId
        is Page.ConfirmDelete -> page.playlistId
        else -> null
    }
    val shownPlaylist by remember(shownPlaylistId) {
        shownPlaylistId?.let(playlistViewModel::observePlaylist) ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)

    // On "Add to Playlist": which playlists already contain that song.
    val songBeingAdded = (page as? Page.AddToPlaylist)?.let { songsById[it.songId] }
    val playlistsWithSong by remember(songBeingAdded) {
        songBeingAdded?.let(playlistViewModel::observePlaylistsContaining) ?: flowOf(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())

    // Lyrics start off. rememberSaveable keeps your choice while the app is "open": through the
    // screen turning off, switching apps, rotation, even Android quietly closing the app in the
    // background (it's restored from the saved state). Closing the app yourself (swiping it away)
    // throws the saved state away, so the next start is back to off.
    var showLyrics by rememberSaveable { mutableStateOf(false) }

    // The text being typed on a NameEntry page.
    var nameDraft by rememberSaveable { mutableStateOf("") }

    // A short message ("Added to Chill") shown at the bottom of the screen for a moment.
    var message by remember { mutableStateOf<String?>(null) }
    var messageTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(messageTime) {
        if (messageTime == 0L) return@LaunchedEffect
        delay(1_800)
        message = null
    }
    fun showMessage(text: String) {
        message = text
        messageTime = SystemClock.uptimeMillis()
    }

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

    // Settings, and the vibration/click the wheel makes (which depends on them).
    val settings = remember { DeviceSettings(context) }
    val feedback = remember { WheelFeedback(context, settings) }
    // DisposableEffect: run cleanup when this leaves the screen (here: free the click sounds).
    DisposableEffect(feedback) { onDispose { feedback.release() } }

    // "Pause for calls" needs the phone permission; ask for it when the option is turned on.
    // (It can also be On without the permission, e.g. after settings were copied from another
    // install, so Extras shows that and selecting the row asks again.)
    var hasPhonePermission by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
    }
    val askPhonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPhonePermission = granted
        if (granted) {
            settings.changePauseForCalls(true)
        } else {
            showMessage("Needs the Phone permission (app settings)")
        }
    }
    fun togglePauseForCalls() {
        when {
            !hasPhonePermission -> askPhonePermission.launch(Manifest.permission.READ_PHONE_STATE)
            settings.pauseForCalls -> settings.changePauseForCalls(false)
            else -> settings.changePauseForCalls(true)
        }
    }

    // Scanning (holding ⏭/⏮).
    fun onScan(forward: Boolean, heldMs: Long) {
        // Each tick (6-7 per second) jumps 2 s; after holding 2 s, 6 s per tick.
        val step = if (heldMs < 2_000) 2_000L else 6_000L
        playerViewModel.seekBy(if (forward) step else -step)
    }

    // When the downloader saves a new song, rescan so it shows up right away.
    LaunchedEffect(Unit) {
        downloadViewModel.songDownloaded.collect { libraryViewModel.rescan() }
    }
    // Let playlist imports see our songs and write into our playlists.
    LaunchedEffect(downloadViewModel) {
        downloadViewModel.setLibrary(AppMusicLibrary(context.applicationContext))
    }

    // A pinned playlist on the main menu: load its songs, shuffle them (from a random one).
    val scope = rememberCoroutineScope()
    fun shufflePlaylist(playlistId: Long) {
        scope.launch {
            val playlistSongs = playlistViewModel.songFileNames(playlistId).mapNotNull { songsByFileName[it] }
            if (playlistSongs.isEmpty()) {
                showMessage("No songs in that playlist yet")
                return@launch
            }
            playerViewModel.shuffleAll(playlistSongs)
            navigation.open(Page.NowPlaying)
        }
    }

    // "Start Mix" on a song: its YouTube Music radio (songs you have play from the phone, the
    // rest are streamed). Without internet: a mix from your own library instead.
    val radioMixMaker = remember { RadioMixMaker(context.applicationContext) }
    val libraryMixMaker = remember { SongMixMaker(context.applicationContext) }
    fun startMix(song: Song) {
        playerViewModel.playInOrder(listOf(song))  // the song itself starts right away
        navigation.open(Page.NowPlaying)
        showMessage("Making a mix…")
        scope.launch {
            // Give YouTube 30 s at most (a slow connection), then fall back to the library.
            val radio = try {
                withTimeoutOrNull(30_000) { radioMixMaker.make(song, songs) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val (rest, message) = radio?.let { it to "Mix ready: ${it.size} songs" }
                ?: (libraryMixMaker.make(song, songs, stats).filter { it.id != song.id } to "Offline: a mix from your library")
            if (playerViewModel.continueMix(song, rest)) showMessage(message)
        }
    }

    /** "Download" on a streamed song: save it to the library like any other download. */
    fun downloadSong(song: Song) {
        val videoId = song.streamVideoId ?: return
        downloadViewModel.download("https://music.youtube.com/watch?v=$videoId")
        showMessage("Downloading ${song.title}")
    }

    // For You: make sure today's picks are there whenever the page is open.
    val forYou by discoverViewModel.forYou.collectAsStateWithLifecycle()
    LaunchedEffect(page, songs.isNotEmpty()) {
        if (page == Page.ForYou) discoverViewModel.load(songs, stats)
    }

    // --- What each page shows ---

    val menus = DeviceMenus(
        songs = songs,
        songsById = songsById,
        songsByFileName = songsByFileName,
        currentSong = currentSong,
        playerState = playerState,
        playlists = playlists,
        shownPlaylist = shownPlaylist,
        playlistsWithSong = playlistsWithSong,
        navigation = navigation,
        player = playerViewModel,
        playlistActions = playlistViewModel,
        stats = stats,
        settings = settings,
        musicFolder = musicFolder,
        folderChoices = folderChoices,
        chooseMusicFolder = libraryViewModel::setMusicFolder,
        togglePauseForCalls = ::togglePauseForCalls,
        hasPhonePermission = hasPhonePermission,
        showMessage = ::showMessage,
        startNameEntry = { namePage, initialText ->
            nameDraft = initialText
            navigation.open(namePage)
        },
        shufflePlaylist = ::shufflePlaylist,
        startMix = ::startMix,
        downloadSong = ::downloadSong,
        forYou = forYou,
        refreshForYou = { newPicks -> discoverViewModel.load(songs, stats, newPicks) },
    )
    val entries = menus.entries(page)

    fun saveName(namePage: Page.NameEntry) {
        val name = nameDraft.trim()
        if (name.isEmpty()) return
        val renameId = namePage.renamePlaylistId
        if (renameId != null) {
            playlistViewModel.renamePlaylist(renameId, name)
            showMessage("Renamed to $name")
            navigation.back()
        } else {
            val firstSong = namePage.firstSongId?.let { songsById[it] }
            playlistViewModel.createPlaylist(name, firstSong)
            showMessage(if (firstSong != null) "Added to $name" else "Created $name")
            navigation.back()                       // leave the name page...
            if (firstSong != null) navigation.back()  // ...and the "Add to Playlist" page
        }
    }

    // --- What the wheel does on each page ---

    // Careful: Compose may keep using the wheel callbacks from an earlier redraw (it decides
    // they "didn't change"). So the callbacks must not rely on values captured now, like
    // `page` and `entries` above. Instead they read the *current* values when the wheel is
    // used. rememberUpdatedState gives a holder that always has the latest value.
    val currentEntries by rememberUpdatedState(entries)
    val latestSong by rememberUpdatedState(currentSong)

    fun onRotate(steps: Int) {
        val current = navigation.currentPage
        when (current) {
            Page.NowPlaying -> {
                volume.change(steps)
                volumeFraction = volume.fraction
                lastVolumeChange = SystemClock.uptimeMillis()
            }
            is Page.NameEntry, Page.Download, Page.PlaylistImport -> {}  // nothing to scroll
            else -> {
                val moved = acceleration.apply(steps, SystemClock.uptimeMillis())
                navigation.moveSelection(current, moved, currentEntries.size)
            }
        }
    }

    fun onCenter() {
        when (val current = navigation.currentPage) {
            Page.NowPlaying -> showLyrics = !showLyrics
            is Page.NameEntry -> saveName(current)
            Page.Download, Page.PlaylistImport -> {}  // the download pages are used by touch
            else -> currentEntries.getOrNull(navigation.selectedIndex(current))?.onSelect?.invoke()
        }
    }

    /** Holding the center: options for whatever is highlighted (or playing). */
    fun onCenterHold() {
        when (val current = navigation.currentPage) {
            Page.NowPlaying -> if (latestSong != null) navigation.open(Page.NowPlayingOptions)
            is Page.NameEntry, Page.Download, Page.PlaylistImport -> {}
            else -> currentEntries.getOrNull(navigation.selectedIndex(current))?.onLongPress?.invoke()
        }
    }

    // The phone's back gesture works like MENU.
    BackHandler(enabled = navigation.canGoBack) { navigation.back() }

    StatusBarIcons()

    // While typing, the keyboard takes the wheel's place.
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Box(modifier = Modifier.fillMaxSize()) {
        DeviceTheme(animations = settings.animations) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(DeviceColors.BodyTop, DeviceColors.BodyBottom)))
                    .systemBarsPadding()
                    .imePadding()
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
                        // A faint diagonal glare, like light catching the glass. drawWithContent
                        // draws the screen's contents first, then the glare on top of them.
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.linearGradient(
                                    0f to Color.White.copy(alpha = 0.22f),
                                    0.45f to Color.Transparent,
                                    start = Offset.Zero,
                                    end = Offset(size.width, size.height),
                                )
                            )
                        }
                        .border(3.dp, DeviceColors.ScreenFrame, screenShape)
                        .padding(3.dp),  // keep content inside the frame
                ) {
                    DeviceHeader(
                        title = menus.title(page),
                        isPlaying = if (currentSong != null) playerState.isPlaying else null,
                        shuffle = playerState.shuffleEnabled,
                        repeatMode = playerState.repeatMode,
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AudioPermissionGate(modifier = Modifier.fillMaxSize()) {
                            LaunchedEffect(Unit) { libraryViewModel.loadSongsOnce() }

                            // Each page's rows as last shown, so a page that's sliding away keeps them
                            // (a playlist's songs are only loaded for the page that's open).
                            val lastEntries = remember { mutableMapOf<Page, List<MenuEntry>>() }
                            lastEntries[page] = entries

                            // The list shown by menu pages. A composable lambda: a piece of UI kept in
                            // a variable, so it can be placed wherever a page needs it.
                            val menuList: @Composable (Page) -> Unit = { shownPage ->
                                val shownEntries = lastEntries[shownPage].orEmpty()
                                MenuPage(
                                    entries = shownEntries,
                                    selectedIndex = navigation.selectedIndex(shownPage).coerceAtMost(shownEntries.lastIndex.coerceAtLeast(0)),
                                    onTap = { index ->
                                        navigation.select(shownPage, index)
                                        shownEntries[index].onSelect()
                                    },
                                    onLongTap = { index ->
                                        navigation.select(shownPage, index)
                                        shownEntries[index].onLongPress?.invoke()
                                    },
                                    artLoader = libraryViewModel.artLoader,
                                    emptyText = when (shownPage) {
                                        Page.Songs -> "No songs in $musicFolder.\nPick your music folder in Extras."
                                        is Page.Playlist -> "Loading…"
                                        else -> "Nothing here"
                                    },
                                )
                            }

                            // Opening a page slides it in from the right; MENU slides back to the left.
                            // AnimatedContent keeps the old page on screen until its exit animation ends.
                            val slide = motionSpec<IntOffset>()
                            val animate = settings.animations
                            AnimatedContent(
                                targetState = ShownPage(page, navigation.depth),
                                contentKey = { it.page },
                                transitionSpec = {
                                    if (!animate) {
                                        EnterTransition.None togetherWith ExitTransition.None
                                    } else {
                                        val direction = if (targetState.depth >= initialState.depth) 1 else -1
                                        slideInHorizontally(slide) { width -> direction * width } togetherWith
                                            slideOutHorizontally(slide) { width -> -direction * width }
                                    }
                                },
                                label = "page",
                            ) { (shownPage, _) ->
                                when (shownPage) {
                                    Page.NowPlaying -> DeviceNowPlaying(
                                        song = currentSong,
                                        state = playerState,
                                        positionMs = positionMs,
                                        artLoader = libraryViewModel.artLoader,
                                        lyricsLoader = libraryViewModel.lyricsLoader,
                                        showLyrics = showLyrics,
                                        volumeFraction = volumeFraction,
                                        coverStyle = settings.coverStyle,
                                        onSeek = playerViewModel::seekTo,
                                    )
                                    Page.Download -> DownloadScreen(
                                        viewModel = downloadViewModel,
                                        showTitle = false,
                                        onOpenPlaylistImport = { navigation.open(Page.PlaylistImport) },
                                    )
                                    Page.PlaylistImport -> PlaylistImportScreen(
                                        viewModel = downloadViewModel,
                                        playlists = playlists.map { PlaylistChoice(it.id, it.name) },
                                    )
                                    is Page.NameEntry -> NameEntryPage(
                                        name = nameDraft,
                                        onNameChange = { nameDraft = it },
                                        onSave = { saveName(shownPage) },
                                        onCancel = { navigation.back() },
                                    )
                                    else -> menuList(shownPage)
                                }
                            }
                        }
                        MessageBubble(message, Modifier.align(Alignment.BottomCenter))
                    }
                }

                if (!keyboardOpen) {
                    Spacer(Modifier.height(24.dp))
                    ClickWheel(
                        onRotate = ::onRotate,
                        onCenter = ::onCenter,
                        onCenterHold = ::onCenterHold,
                        onMenu = { navigation.back() },
                        onPrevious = playerViewModel::skipToPrevious,
                        onNext = playerViewModel::skipToNext,
                        onPlayPause = playerViewModel::togglePlayPause,
                        onScan = ::onScan,
                        feedback = feedback,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** The page on screen, and how deep it is in the menus (to know which way to slide). */
private data class ShownPage(val page: Page, val depth: Int)

/** A small mocha pill with a short confirmation, e.g. "Added to Chill". Fades in and out. */
@Composable
private fun MessageBubble(text: String?, modifier: Modifier = Modifier) {
    val fade = motionSpec<Float>()
    // AnimatedContent keeps showing the old text while it fades out, even after it's null.
    AnimatedContent(
        targetState = text,
        transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
        modifier = modifier,
        label = "message",
    ) { shown ->
        if (shown != null) {
            Text(
                shown,
                color = DeviceColors.Screen,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(DeviceColors.ScreenFrame)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Dark status/navigation bar icons, so they show up on the light silver body.
 * SideEffect runs after every successful redraw, to sync something outside Compose.
 */
@Composable
private fun StatusBarIcons() {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }
}
