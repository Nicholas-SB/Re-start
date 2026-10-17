package io.github.nicholas_sb.restart.ui.device

import io.github.nicholas_sb.restart.data.history.MonthDetail
import io.github.nicholas_sb.restart.data.history.MonthSummary
import io.github.nicholas_sb.restart.data.streamedSong
import io.github.nicholas_sb.restart.playback.STREAM_KEY_PREFIX
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import io.github.nicholas_sb.restart.data.TextSearch
import io.github.nicholas_sb.restart.playback.SleepTimerCommand
import io.github.nicholas_sb.restart.ui.SleepTimer
import io.github.nicholas_sb.restart.ui.formatTime
import io.github.nicholas_sb.restart.data.backup.Backup
import io.github.nicholas_sb.restart.ui.formatDateTime
import io.github.nicholas_sb.restart.data.Song
import io.github.nicholas_sb.restart.data.db.PlaylistSummary
import io.github.nicholas_sb.restart.data.db.SongStatsEntity
import io.github.nicholas_sb.restart.ui.FolderChoice
import io.github.nicholas_sb.restart.ui.ForYouState
import io.github.nicholas_sb.restart.ui.PlayerUiState
import io.github.nicholas_sb.restart.ui.PlayerViewModel
import io.github.nicholas_sb.restart.ui.PlaylistDetail
import io.github.nicholas_sb.restart.ui.PlaylistViewModel
import io.github.nicholas_sb.restart.ui.RepeatMode

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
    // Functions, so they're only read by the pages that show them (see RestartApp).
    private val stats: () -> Map<String, SongStatsEntity>,
    private val settings: DeviceSettings,
    private val musicFolder: String,
    private val folderChoices: List<FolderChoice>,
    private val chooseMusicFolder: (String) -> Unit,
    private val togglePauseForCalls: () -> Unit,
    private val hasPhonePermission: Boolean,
    private val showMessage: (String) -> Unit,
    private val startNameEntry: (page: Page.NameEntry, initialText: String) -> Unit,
    private val shufflePlaylist: (playlistId: Long) -> Unit,
    private val searchQuery: String,
    private val months: () -> List<MonthSummary>,
    private val monthDetail: () -> MonthDetail?,
    private val sleepTimer: SleepTimer?,
    private val setSleepTimer: (minutes: Int) -> Unit,
    private val backUpNow: () -> Unit,
    private val pickBackupFile: () -> Unit,
    private val pendingRestore: Backup?,
    private val restoreBackup: () -> Unit,
    private val lastBackupAt: Long?,
    private val startMix: (song: Song) -> Unit,
    private val downloadSong: (song: Song) -> Unit,
    private val deleteSong: (song: Song) -> Unit,
    private val forYou: ForYouState,
    private val refreshForYou: (newPicks: Boolean) -> Unit,
) {

    fun title(page: Page): String = when (page) {
        Page.MainMenu -> "Re-start"
        Page.Music -> "Music"
        Page.Songs -> "Songs"
        Page.Search -> "Search"
        Page.ForYou -> "For You"
        Page.Stats -> "Stats"
        Page.History -> "History"
        is Page.Month -> YearMonth.parse(page.month).format(monthTitle)
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
        is Page.ConfirmDeleteSong -> "Delete Song?"
        Page.Backup -> "Backup"
        Page.SleepTimer -> "Sleep Timer"
        Page.ConfirmRestore -> "Restore Backup?"
    }

    fun entries(page: Page): List<MenuEntry> = when (page) {
        Page.MainMenu -> mainMenu()
        Page.Music -> musicMenu()
        Page.Songs -> songsMenu()
        Page.Search -> searchMenu()
        Page.ForYou -> forYouMenu()
        Page.Stats -> statsMenu()
        Page.History -> historyMenu()
        is Page.Month -> monthMenu(YearMonth.parse(page.month))
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
        is Page.ConfirmDeleteSong -> confirmDeleteSongMenu(page.songId)
        Page.Backup -> backupMenu()
        Page.SleepTimer -> sleepTimerMenu()
        Page.ConfirmRestore -> confirmRestoreMenu()
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
        MenuEntry("Search", detail = "Songs, artists, albums, playlists", opensSubmenu = true) { navigation.open(Page.Search) },
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

    /** Playlists whose name matches first, then the matching songs (choosing one plays the results from there). */
    private fun searchMenu(): List<MenuEntry> {
        val words = TextSearch.words(searchQuery)
        if (words.isEmpty()) return emptyList()
        val playlistRows = playlists
            .filter { TextSearch.matches(words, it.name) }
            .map { playlist ->
                MenuEntry(playlist.name, detail = "Playlist · ${plural(playlist.songCount, "song")}", opensSubmenu = true) {
                    navigation.open(Page.Playlist(playlist.id))
                }
            }
        return playlistRows + songListMenu(TextSearch.songs(songs, searchQuery))
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
        val all = stats().values
        val mostPlayed = all
            .filter { it.playCount > 0 && it.songFileName in songsByFileName }
            .sortedByDescending { it.playCount }
            .take(10)
        val topSongs = mostPlayed.map { songsByFileName.getValue(it.songFileName) }
        return buildList {
            add(MenuEntry("History", detail = "Your listening, month by month", opensSubmenu = true) { navigation.open(Page.History) })
            add(MenuEntry("Listening time", detail = formatListeningTime(all.sumOf { it.listenedMs })) {})
            add(MenuEntry("Plays", detail = all.sumOf { it.playCount }.toString()) {})
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

    // --- History ---

    /** Every month with listening, newest first. */
    private fun historyMenu(): List<MenuEntry> =
        months().map { summary ->
            val listened = if (summary.listenedMs > 0) " · ${formatListeningTime(summary.listenedMs)}" else ""
            MenuEntry(summary.month.format(monthTitle), detail = plural(summary.plays, "play") + listened, opensSubmenu = true) {
                navigation.open(Page.Month(summary.month.toString()))
            }
        }

    /** One month: totals, the busiest day, then the top songs (playable) and top artists. */
    private fun monthMenu(month: YearMonth): List<MenuEntry> {
        val detail = monthDetail()?.takeIf { it.month == month } ?: return listOf(MenuEntry("Loading…") {})
        // The top songs as Songs: from the library, or streamed again; songs deleted since are left out.
        val songs = detail.topSongs.map { count ->
            if (count.key.startsWith(STREAM_KEY_PREFIX)) {
                streamedSong(count.key.removePrefix(STREAM_KEY_PREFIX), count.title, count.artist, 0)
            } else {
                songsByFileName[count.key]
            }
        }
        val playable = songs.filterNotNull()
        return buildList {
            add(MenuEntry("Plays", detail = detail.plays.toString()) {})
            add(
                MenuEntry(
                    "Listening time",
                    detail = if (detail.listenedMs > 0) formatListeningTime(detail.listenedMs) else "Not recorded for this month",
                ) {}
            )
            detail.busiestDay?.let { day ->
                add(MenuEntry("Busiest day", detail = "${day.format(dayTitle)} · ${plural(detail.busiestDayPlays, "play")}") {})
            }
            if (detail.topSongs.isNotEmpty()) add(MenuEntry("Top Songs") {})
            detail.topSongs.forEachIndexed { index, count ->
                val song = songs[index]
                val gone = if (song == null) " · not on your phone" else ""
                add(
                    MenuEntry(
                        "${index + 1}. ${count.title}",
                        detail = listOfNotNull(count.artist, plural(count.plays, "play")).joinToString(" · ") + gone,
                        song = song,
                    ) { if (song != null) playAll(playable, startIndex = playable.indexOf(song)) }
                )
            }
            if (detail.topArtists.isNotEmpty()) add(MenuEntry("Top Artists") {})
            detail.topArtists.forEach { artist -> add(MenuEntry(artist.title, detail = plural(artist.plays, "play")) {}) }
        }
    }

    // --- Settings ---

    /** Extras: Download and Stats, then the settings. */
    private fun extrasMenu() = listOf(
        MenuEntry("Download", opensSubmenu = true) { navigation.open(Page.Download) },
        MenuEntry("Stats", opensSubmenu = true) { navigation.open(Page.Stats) },
        MenuEntry("Sleep Timer", detail = sleepTimerText(), opensSubmenu = true) { navigation.open(Page.SleepTimer) },
        MenuEntry("Backup", detail = "Save or restore playlists, stats and settings", opensSubmenu = true) {
            navigation.open(Page.Backup)
        },
        MenuEntry("Music folder", detail = musicFolder, opensSubmenu = true) { navigation.open(Page.MusicFolder) },
        MenuEntry("Animations", detail = if (settings.animations) "On · slides, fades and a spinning disc" else "Off") {
            settings.toggleAnimations()
        },
        MenuEntry("Cover style", detail = when (settings.coverStyle) {
            CoverStyle.DISC -> "Disc · a record that spins while playing"
            CoverStyle.SQUARE -> "Square"
        }) { settings.cycleCoverStyle() },
        MenuEntry("Record change", detail = if (settings.recordChange) "On · a short pause when you skip, like putting on a record" else "Off") {
            settings.toggleRecordChange()
        },
        MenuEntry("Clicker", detail = if (settings.clickSound) "On · click sound for every wheel step" else "Off") {
            settings.toggleClickSound()
        },
        MenuEntry("Wheel vibration", detail = settings.vibration.label) { settings.cycleVibration() },
        MenuEntry(
            "Pause for calls",
            detail = when {
                !settings.pauseForCalls -> "Off"
                hasPhonePermission -> "On · even when the ringer is silent"
                else -> "On · select to allow the Phone permission"
            },
        ) {
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
            MenuEntry("Sleep Timer", detail = sleepTimerText(), opensSubmenu = true) { navigation.open(Page.SleepTimer) },
            // A streamed song has no file to put in a playlist yet: download it first.
            if (song.isStreamed) {
                MenuEntry("Download", detail = "Streaming · save it to your library") { downloadSong(song) }
            } else {
                MenuEntry("Add to Playlist…", opensSubmenu = true) { navigation.open(Page.AddToPlaylist(song.id)) }
            },
        ) + listOfNotNull(deleteEntry(song))
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
                deleteEntry(song)?.let(::add)
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
        ) + listOfNotNull(deleteEntry(song))
    }

    /** "Delete Song…" for songs that are files on the phone (not streamed ones). */
    private fun deleteEntry(song: Song): MenuEntry? =
        if (song.isStreamed) {
            null
        } else {
            MenuEntry("Delete Song…", detail = "From your phone and all playlists", opensSubmenu = true) {
                navigation.open(Page.ConfirmDeleteSong(song.id))
            }
        }

    /** Cancel comes first, so a stray press of the center can't delete anything. */
    private fun confirmDeleteSongMenu(songId: Long): List<MenuEntry> {
        val song = findSong(songId) ?: return emptyList()
        return listOf(
            MenuEntry("Cancel") { navigation.back() },
            MenuEntry("Delete \"${song.title}\"", detail = "Deletes the file and takes it out of every playlist") { deleteSong(song) },
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

    // --- Sleep timer ---

    private fun sleepTimerText(): String = when (val timer = sleepTimer) {
        null -> "Off"
        is SleepTimer.At -> "On · stops at ${formatTime(timer.stopsAtMs)}"
        SleepTimer.EndOfSong -> "On · stops after this song"
    }

    /** Off, a few lengths, or the end of the song. The one that's set is marked. */
    private fun sleepTimerMenu(): List<MenuEntry> {
        fun choice(label: String, minutes: Int, isCurrent: Boolean, detail: String? = null) =
            MenuEntry(label, detail = if (isCurrent) listOfNotNull(detail, "current").joinToString(" · ") else detail) {
                setSleepTimer(minutes)
                showMessage(if (minutes == 0) "Sleep timer off" else "Sleep timer: ${label.lowercase()}")
                navigation.back()
            }
        val timer = sleepTimer
        return listOf(choice("Off", 0, timer == null)) +
            listOf(15, 30, 45, 60, 90).map { minutes ->
                val label = if (minutes < 60) "$minutes minutes" else if (minutes == 60) "1 hour" else "1½ hours"
                choice(label, minutes, isCurrent = false)
            } +
            choice(
                "End of Song", SleepTimerCommand.END_OF_SONG, timer == SleepTimer.EndOfSong,
                detail = "Stops when this song ends",
            ) +
            listOfNotNull(
                // A timer that's counting: show when it stops.
                (timer as? SleepTimer.At)?.let { MenuEntry("Stops at ${formatTime(it.stopsAtMs)}", detail = "Pick another time to change it") {} },
            )
    }

    // --- Backup ---

    /** Save everything to a file you pick, or load it back from one. */
    private fun backupMenu() = listOf(
        MenuEntry("Back Up Now", detail = lastBackupAt?.let { "Last backup: ${formatDateTime(it)}" } ?: "Playlists, stats and settings, to a file") {
            backUpNow()
        },
        MenuEntry("Restore…", detail = "Replace them with a backup file") { pickBackupFile() },
    )

    /** Cancel comes first, so a stray press of the center can't wipe anything. */
    private fun confirmRestoreMenu(): List<MenuEntry> {
        val backup = pendingRestore ?: return emptyList()
        return listOf(
            MenuEntry("Cancel") { navigation.back() },
            MenuEntry("Replace Everything", detail = "With ${backup.summary}, from ${formatDateTime(backup.createdAt)}") {
                restoreBackup()
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

/** "October 2026", in the phone's language. */
private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy")

/** "Sat, Oct 17" */
private val dayTitle = DateTimeFormatter.ofPattern("EEE, MMM d")
