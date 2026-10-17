package com.example.custommusicplayer.ui.device

import com.example.custommusicplayer.data.Song
import com.example.custommusicplayer.data.db.PlaylistSummary
import com.example.custommusicplayer.data.db.SongStatsEntity
import com.example.custommusicplayer.ui.FolderChoice
import com.example.custommusicplayer.ui.ForYouState
import com.example.custommusicplayer.ui.PlayerUiState
import com.example.custommusicplayer.ui.PlayerViewModel
import com.example.custommusicplayer.ui.PlaylistDetail
import com.example.custommusicplayer.ui.PlaylistViewModel
import com.example.custommusicplayer.ui.RepeatMode

/**
 * What every menu page lists, and what choosing a row does. No drawing here: RestartApp
 * makes a DeviceMenus with the latest data on each redraw and asks it for titles and rows.
 */
class DeviceMenus(
    private val songs: List<Song>,
    private val songsById: Map<Long, Song>,
    private val songsByFileName: Map<String, Song>,
    private val currentSong: Song?,
    private val playerState: PlayerUiState,
    private val playlists: List<PlaylistSummary>,
    private val shownPlaylist: PlaylistDetail?,  // the playlist the current page is about, once loaded
    private val playlistsWithSong: Set<Long>,    // on "Add to Playlist": playlists that already have the song
    private val navigation: DeviceNavigationViewModel,
    private val player: PlayerViewModel,
    private val playlistActions: PlaylistViewModel,
    private val stats: Map<String, SongStatsEntity>,
    private val settings: DeviceSettings,
    private val musicFolder: String,
    private val folderChoices: List<FolderChoice>,
    private val chooseMusicFolder: (String) -> Unit,
    private val togglePauseForCalls: () -> Unit,
    private val showMessage: (String) -> Unit,
    private val startNameEntry: (page: Page.NameEntry, initialText: String) -> Unit,
    private val shufflePlaylist: (playlistId: Long) -> Unit,
    private val startMix: (song: Song) -> Unit,
    private val downloadSong: (song: Song) -> Unit,
    private val forYou: ForYouState,
    private val refreshForYou: (newPicks: Boolean) -> Unit,
) {

    fun title(page: Page): String = when (page) {
        Page.MainMenu -> "Re-start"
        Page.Music -> "Music"
        Page.Songs -> "Songs"
        Page.ForYou -> "For You"
        Page.Stats -> "Stats"
        Page.Download -> "Download"
        Page.PlaylistImport -> "Import Playlist"
        Page.Extras -> "Extras"
        Page.MusicFolder -> "Music Folder"
        Page.NowPlaying -> "Now Playing"
        Page.NowPlayingOptions -> "Options"
        Page.UpNext -> "Up Next"
        Page.Playlists -> "Playlists"
        is Page.Playlist -> playlistName(page.playlistId)
        is Page.PlaylistOptions -> playlistName(page.playlistId)
        is Page.PlaylistEntryOptions -> "Song Options"
        is Page.SongOptions -> findSong(page.songId)?.title ?: "Song"
        is Page.AddToPlaylist -> "Add to Playlist"
        is Page.NameEntry -> if (page.renamePlaylistId != null) "Rename Playlist" else "New Playlist"
        is Page.ConfirmDelete -> "Delete Playlist?"
    }

    fun entries(page: Page): List<MenuEntry> = when (page) {
        Page.MainMenu -> mainMenu()
        Page.Music -> musicMenu()
        Page.Songs -> songsMenu()
        Page.ForYou -> forYouMenu()
        Page.Stats -> statsMenu()
        Page.Extras -> extrasMenu()
        Page.MusicFolder -> musicFolderMenu()
        Page.NowPlayingOptions -> nowPlayingOptionsMenu()
        Page.UpNext -> upNextMenu()
        Page.Playlists -> playlistsMenu()
        is Page.Playlist -> playlistMenu(page.playlistId)
        is Page.PlaylistOptions -> playlistOptionsMenu(page.playlistId)
        is Page.PlaylistEntryOptions -> entryOptionsMenu(page.playlistId, page.entryId)
        is Page.SongOptions -> songOptionsMenu(page.songId)
        is Page.AddToPlaylist -> addToPlaylistMenu(page.songId)
        is Page.ConfirmDelete -> confirmDeleteMenu(page.playlistId)
        // These draw their own content instead of a list.
        Page.NowPlaying, Page.Download, Page.PlaylistImport, is Page.NameEntry -> emptyList()
    }

    // --- Top level ---

    private fun mainMenu() = buildList {
        add(MenuEntry("Music", opensSubmenu = true) { navigation.open(Page.Music) })
        add(MenuEntry("For You", opensSubmenu = true) { navigation.open(Page.ForYou) })
        // Pinned playlists: one click shuffles them. Hold the center for the playlist's options.
        playlists.filter { it.pinned }.forEach { playlist ->
            add(
                MenuEntry(
                    playlist.name,
                    detail = "Shuffle · ${plural(playlist.songCount, "song")}",
                    onLongPress = { navigation.open(Page.PlaylistOptions(playlist.id)) },
                ) { shufflePlaylist(playlist.id) }
            )
        }
        add(MenuEntry("Extras", opensSubmenu = true) { navigation.open(Page.Extras) })
        if (currentSong != null) {
            add(MenuEntry("Now Playing", detail = currentSong.title, opensSubmenu = true) { navigation.open(Page.NowPlaying) })
        }
    }

    private fun musicMenu() = listOf(
        MenuEntry("Shuffle Songs") { playAll(songs, shuffle = true) },
        MenuEntry("Songs", detail = plural(songs.size, "song"), opensSubmenu = true) { navigation.open(Page.Songs) },
        MenuEntry("Playlists", detail = plural(playlists.size, "playlist"), opensSubmenu = true) { navigation.open(Page.Playlists) },
    )

    /** All songs, with a "Sort" row on top that switches between A–Z and newest first. */
    private fun songsMenu(): List<MenuEntry> {
        if (songs.isEmpty()) return emptyList()  // so the page shows its "No songs" message instead
        val sorted = when (settings.songSort) {
            SongSort.A_TO_Z -> songs  // the scanner already returns them A–Z
            SongSort.RECENTLY_ADDED -> songs.sortedByDescending { it.dateAddedMs }
        }
        return listOf(MenuEntry("Sort", detail = settings.songSort.label) { settings.cycleSongSort() }) +
            songListMenu(sorted)
    }

    /**
     * A list of songs: choosing one plays the whole list from there. Used by Songs and Stats.
     * Holding the center opens the song's options (Start Mix, Add to Playlist).
     */
    private fun songListMenu(list: List<Song>) = buildList {
        list.forEachIndexed { index, song ->
            add(
                MenuEntry(
                    song.title,
                    detail = song.artist,
                    song = song,
                    onLongPress = { navigation.open(Page.SongOptions(song.id)) },
                ) { playAll(list, startIndex = index) }
            )
        }
    }

    // --- Stats ---

    /** Totals first, then the most played songs (choosing one plays that top list). */
    private fun statsMenu(): List<MenuEntry> {
        val all = stats.values
        val mostPlayed = all
            .filter { it.playCount > 0 && it.songFileName in songsByFileName }
            .sortedByDescending { it.playCount }
            .take(10)
        val topSongs = mostPlayed.map { songsByFileName.getValue(it.songFileName) }
        return buildList {
            add(MenuEntry("Listening time", detail = formatListeningTime(all.sumOf { it.listenedMs })) {})
            add(MenuEntry("Plays", detail = all.sumOf { it.playCount }.toString()) {})
            add(MenuEntry("Skips", detail = all.sumOf { it.skipCount }.toString()) {})
            if (mostPlayed.isEmpty()) {
                add(MenuEntry("Most played: nothing yet", detail = "A song counts once you've heard half of it") {})
            }
            mostPlayed.forEachIndexed { index, songStats ->
                val song = topSongs[index]
                add(
                    MenuEntry(
                        "${index + 1}. ${song.title}",
                        detail = "${plural(songStats.playCount, "play")} · ${lastPlayedText(songStats.lastPlayedAt)}",
                        song = song,
                        onLongPress = { navigation.open(Page.AddToPlaylist(song.id)) },
                    ) { playAll(topSongs, startIndex = index) }
                )
            }
        }
    }

    // --- Settings ---

    /** Extras: Download and Stats, then the settings. */
    private fun extrasMenu() = listOf(
        MenuEntry("Download", opensSubmenu = true) { navigation.open(Page.Download) },
        MenuEntry("Stats", opensSubmenu = true) { navigation.open(Page.Stats) },
        MenuEntry("Music folder", detail = musicFolder, opensSubmenu = true) { navigation.open(Page.MusicFolder) },
        MenuEntry("Clicker", detail = if (settings.clickSound) "On · click sound for every wheel step" else "Off") {
            settings.toggleClickSound()
        },
        MenuEntry("Wheel vibration", detail = settings.vibration.label) { settings.cycleVibration() },
        MenuEntry("Pause for calls", detail = if (settings.pauseForCalls) "On · even when the ringer is silent" else "Off") {
            togglePauseForCalls()
        },
        if (settings.batterySaverSupported) {
            MenuEntry("Battery saver", detail = if (settings.batterySaver) "On · the audio chip plays with the screen off" else "Off") {
                settings.toggleBatterySaver()
            }
        } else {
            MenuEntry("Battery saver", detail = "Not supported on this phone") {
                showMessage("This phone doesn't let apps use its audio chip")
            }
        },
        MenuEntry("About", detail = "Re-start · your music, your player") {},
    )

    /** Pick the one folder the library shows (downloads in Music/Re-start always count too). */
    private fun musicFolderMenu() = folderChoices.map { choice ->
        val current = choice.folder == musicFolder
        MenuEntry(
            choice.folder,
            detail = plural(choice.songCount, "song") + if (current) " · current" else "",
        ) {
            if (!current) {
                chooseMusicFolder(choice.folder)
                showMessage("Library: ${choice.folder}")
            }
            navigation.back()
        }
    }

    // --- Now Playing options ---

    private fun nowPlayingOptionsMenu(): List<MenuEntry> {
        val song = currentSong ?: return emptyList()
        return listOf(
            MenuEntry("Shuffle", detail = if (playerState.shuffleEnabled) "On" else "Off") { player.toggleShuffle() },
            MenuEntry(
                "Repeat",
                detail = when (playerState.repeatMode) {
                    RepeatMode.OFF -> "Off"
                    RepeatMode.ALL -> "All"
                    RepeatMode.ONE -> "One"
                },
            ) { player.cycleRepeatMode() },
            MenuEntry("Up Next", detail = plural(playerState.upNext.size, "song"), opensSubmenu = true) { navigation.open(Page.UpNext) },
            MenuEntry("Start Mix", detail = "Songs that go with this one") { startMix(song) },
            // A streamed song has no file to put in a playlist yet: download it first.
            if (song.isStreamed) {
                MenuEntry("Download", detail = "Streaming · save it to your library") { downloadSong(song) }
            } else {
                MenuEntry("Add to Playlist…", opensSubmenu = true) { navigation.open(Page.AddToPlaylist(song.id)) }
            },
        )
    }

    /** The queue after the current song, in play order. Choosing one jumps there. */
    private fun upNextMenu() = playerState.upNext.mapNotNull { entry ->
        val song = songsById[entry.songId] ?: entry.streamedSong ?: return@mapNotNull null
        MenuEntry(song.title, detail = song.artist, song = song) {
            player.skipToQueueItem(entry.queueIndex)
            navigation.backTo(Page.NowPlaying)
        }
    }

    // --- Playlists ---

    private fun playlistsMenu() = buildList {
        add(MenuEntry("New Playlist…") { startNameEntry(Page.NameEntry(), "") })
        playlists.forEach { playlist ->
            add(
                MenuEntry(
                    playlist.name,
                    detail = plural(playlist.songCount, "song"),
                    opensSubmenu = true,
                    onLongPress = { navigation.open(Page.PlaylistOptions(playlist.id)) },
                ) { navigation.open(Page.Playlist(playlist.id)) }
            )
        }
    }

    /** "Shuffle", then the songs, then "Edit Playlist…". */
    private fun playlistMenu(playlistId: Long): List<MenuEntry> {
        val detail = shownPlaylist?.takeIf { it.playlist.id == playlistId } ?: return emptyList()
        val playable = playableSongs(detail)
        return buildList {
            if (playable.isNotEmpty()) add(MenuEntry("Shuffle") { playAll(playable, shuffle = true) })
            if (detail.entries.isEmpty()) {
                add(MenuEntry("No songs yet", detail = "Hold the center on any song to add it") {})
            }
            detail.entries.forEach { entry ->
                val song = songsByFileName[entry.songFileName]
                val openOptions = { navigation.open(Page.PlaylistEntryOptions(playlistId, entry.id)) }
                if (song != null) {
                    add(MenuEntry(song.title, detail = song.artist, song = song, onLongPress = openOptions) {
                        playAll(playable, startIndex = playable.indexOf(song))
                    })
                } else {
                    // The file was deleted or renamed: show it so it can be removed.
                    add(MenuEntry("Missing file", detail = entry.songFileName, onLongPress = openOptions, onSelect = openOptions))
                }
            }
            add(MenuEntry("Edit Playlist…", opensSubmenu = true) { navigation.open(Page.PlaylistOptions(playlistId)) })
        }
    }

    private fun playlistOptionsMenu(playlistId: Long): List<MenuEntry> {
        val name = playlistName(playlistId)
        val playable = shownPlaylist?.takeIf { it.playlist.id == playlistId }?.let(::playableSongs).orEmpty()
        return buildList {
            if (playable.isNotEmpty()) {
                add(MenuEntry("Play") { playAll(playable) })
                add(MenuEntry("Shuffle") { playAll(playable, shuffle = true) })
            }
            val pinned = playlists.firstOrNull { it.id == playlistId }?.pinned == true
            add(MenuEntry(if (pinned) "Unpin from Main Menu" else "Pin to Main Menu") {
                playlistActions.setPinned(playlistId, !pinned)
                showMessage(if (pinned) "Unpinned $name" else "Pinned $name to the main menu")
            })
            add(MenuEntry("Rename…") { startNameEntry(Page.NameEntry(renamePlaylistId = playlistId), name) })
            add(MenuEntry("Delete…") { navigation.open(Page.ConfirmDelete(playlistId)) })
        }
    }

    /** Move up / down / remove one song in a playlist. */
    private fun entryOptionsMenu(playlistId: Long, entryId: Long): List<MenuEntry> {
        val detail = shownPlaylist?.takeIf { it.playlist.id == playlistId } ?: return emptyList()
        val index = detail.entries.indexOfFirst { it.id == entryId }
        if (index == -1) return emptyList()
        val entry = detail.entries[index]
        val song = songsByFileName[entry.songFileName]
        // On the playlist page, songs start after the "Shuffle" row (if there is one).
        val firstSongRow = if (playableSongs(detail).isNotEmpty()) 1 else 0
        val playlistPage = Page.Playlist(playlistId)

        fun moveTo(newIndex: Int) {
            playlistActions.moveEntry(playlistId, index, newIndex)
            navigation.select(playlistPage, firstSongRow + newIndex)  // keep the highlight on the moved song
            navigation.back()
        }

        return buildList {
            if (song != null) add(MenuEntry("Start Mix") { startMix(song) })
            if (index > 0) add(MenuEntry("Move Up") { moveTo(index - 1) })
            if (index < detail.entries.lastIndex) add(MenuEntry("Move Down") { moveTo(index + 1) })
            add(MenuEntry("Remove from Playlist") {
                playlistActions.removeEntry(entry)
                showMessage("Removed")
                navigation.back()
            })
            if (song != null) {
                add(MenuEntry("Add to Another Playlist…", opensSubmenu = true) { navigation.open(Page.AddToPlaylist(song.id)) })
            }
        }
    }

    /** Like YouTube Music's song menu: a mix that starts with this song, or add it to a playlist. */
    private fun songOptionsMenu(songId: Long): List<MenuEntry> {
        val song = findSong(songId) ?: return emptyList()
        return listOf(
            MenuEntry("Start Mix", detail = "Songs that go with this one") { startMix(song) },
            if (song.isStreamed) {
                MenuEntry("Download", detail = "Save it to your library") { downloadSong(song) }
            } else {
                MenuEntry("Add to Playlist…", opensSubmenu = true) { navigation.open(Page.AddToPlaylist(songId)) }
            },
        )
    }

    // --- For You ---

    /** Today's picks: a status row while loading (or if offline), Play All, the songs, New Picks. */
    private fun forYouMenu() = buildList {
        val list = forYou.songs
        when {
            forYou.loading -> add(MenuEntry("Finding new songs…", detail = "From the songs you play most") {})
            forYou.problem != null -> add(MenuEntry(forYou.problem, detail = "Select to try again") { refreshForYou(false) })
        }
        if (list.isNotEmpty()) {
            add(MenuEntry("Play All", detail = "${plural(list.size, "song")} · streaming") { playAll(list) })
            list.forEachIndexed { index, song ->
                add(
                    MenuEntry(
                        song.title,
                        detail = song.artist,
                        song = song,
                        onLongPress = { navigation.open(Page.SongOptions(song.id)) },
                    ) { playAll(list, startIndex = index) }
                )
            }
            if (!forYou.loading) add(MenuEntry("New Picks", detail = "Different songs, right now") { refreshForYou(true) })
        }
    }

    /** A song by id: from the library, or a streamed one we're showing (For You, Now Playing). */
    private fun findSong(id: Long): Song? =
        songsById[id] ?: forYou.songs.firstOrNull { it.id == id } ?: currentSong?.takeIf { it.id == id }

    private fun addToPlaylistMenu(songId: Long): List<MenuEntry> {
        val song = songsById[songId] ?: return emptyList()
        return buildList {
            add(MenuEntry("New Playlist…") { startNameEntry(Page.NameEntry(firstSongId = songId), "") })
            playlists.forEach { playlist ->
                val alreadyIn = playlist.id in playlistsWithSong
                add(MenuEntry(playlist.name, detail = if (alreadyIn) "Already added" else plural(playlist.songCount, "song")) {
                    // Like YouTube Music: a song can only be in a playlist once, no "add anyway".
                    if (alreadyIn) {
                        showMessage("Already in ${playlist.name}")
                    } else {
                        playlistActions.addSong(playlist.id, song)
                        showMessage("Added to ${playlist.name}")
                        navigation.back()
                    }
                })
            }
        }
    }

    private fun confirmDeleteMenu(playlistId: Long): List<MenuEntry> {
        val name = playlistName(playlistId)
        return listOf(
            MenuEntry("Cancel") { navigation.back() },
            MenuEntry("Delete \"$name\"", detail = "The songs stay on your phone") {
                playlistActions.deletePlaylist(playlistId)
                showMessage("Deleted $name")
                navigation.backTo(Page.Playlists)
            },
        )
    }

    // --- Helpers ---

    private fun playAll(list: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (list.isEmpty()) return
        if (shuffle) player.shuffleAll(list) else player.playQueue(list, startIndex)
        navigation.open(Page.NowPlaying)
    }

    private fun playableSongs(detail: PlaylistDetail): List<Song> =
        detail.entries.mapNotNull { songsByFileName[it.songFileName] }

    private fun playlistName(playlistId: Long): String =
        playlists.firstOrNull { it.id == playlistId }?.name ?: "Playlist"

    private fun plural(count: Int, word: String) = "$count ${if (count == 1) word else word + "s"}"

    /** 32 s -> "32s", 12 min -> "12m", 125 min -> "2h 5m" */
    private fun formatListeningTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        return when {
            minutes == 0L -> "${totalSeconds}s"
            minutes < 60 -> "${minutes}m"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
    }

    /** "today", "yesterday", "5 days ago" */
    private fun lastPlayedText(lastPlayedAt: Long?): String {
        if (lastPlayedAt == null) return "never"
        return when (val days = (System.currentTimeMillis() - lastPlayedAt) / (24 * 60 * 60 * 1000L)) {
            0L -> "today"
            1L -> "yesterday"
            else -> "$days days ago"
        }
    }
}
