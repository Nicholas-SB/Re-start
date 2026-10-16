package com.example.custommusicplayer.ui.device

import com.example.custommusicplayer.data.MusicScanner
import com.example.custommusicplayer.data.Song
import com.example.custommusicplayer.data.db.PlaylistSummary
import com.example.custommusicplayer.data.db.SongStatsEntity
import com.example.custommusicplayer.data.mixes.Mix
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
    private val navigation: DeviceNavigationViewModel,
    private val player: PlayerViewModel,
    private val playlistActions: PlaylistViewModel,
    private val mixes: List<Mix>,
    private val stats: Map<String, SongStatsEntity>,
    private val settings: DeviceSettings,
    private val showMessage: (String) -> Unit,
    private val startNameEntry: (page: Page.NameEntry, initialText: String) -> Unit,
) {

    fun title(page: Page): String = when (page) {
        Page.MainMenu -> "Re-start"
        Page.Music -> "Music"
        Page.Songs -> "Songs"
        Page.Artists -> "Artists"
        is Page.Artist -> page.name
        Page.Albums -> "Albums"
        is Page.Album -> page.name
        Page.Mixes -> "Mixes"
        is Page.Mix -> mixes.firstOrNull { it.id == page.mixId }?.title ?: "Mix"
        Page.Stats -> "Stats"
        Page.Download -> "Download"
        Page.Settings -> "Settings"
        Page.NowPlaying -> "Now Playing"
        Page.NowPlayingOptions -> "Options"
        Page.UpNext -> "Up Next"
        Page.Playlists -> "Playlists"
        is Page.Playlist -> playlistName(page.playlistId)
        is Page.PlaylistOptions -> playlistName(page.playlistId)
        is Page.PlaylistEntryOptions -> "Song Options"
        is Page.AddToPlaylist -> "Add to Playlist"
        is Page.NameEntry -> if (page.renamePlaylistId != null) "Rename Playlist" else "New Playlist"
        is Page.ConfirmDelete -> "Delete Playlist?"
    }

    fun entries(page: Page): List<MenuEntry> = when (page) {
        Page.MainMenu -> mainMenu()
        Page.Music -> musicMenu()
        Page.Songs -> songListMenu(songs)
        Page.Artists -> artistsMenu()
        is Page.Artist -> songListMenu(songs.filter { it.artist == page.name }, withShuffle = true)
        Page.Albums -> albumsMenu()
        is Page.Album -> songListMenu(songs.filter { it.album == page.name }, withShuffle = true)
        Page.Mixes -> mixesMenu()
        is Page.Mix -> mixMenu(page.mixId)
        Page.Stats -> statsMenu()
        Page.Settings -> settingsMenu()
        Page.NowPlayingOptions -> nowPlayingOptionsMenu()
        Page.UpNext -> upNextMenu()
        Page.Playlists -> playlistsMenu()
        is Page.Playlist -> playlistMenu(page.playlistId)
        is Page.PlaylistOptions -> playlistOptionsMenu(page.playlistId)
        is Page.PlaylistEntryOptions -> entryOptionsMenu(page.playlistId, page.entryId)
        is Page.AddToPlaylist -> addToPlaylistMenu(page.songId)
        is Page.ConfirmDelete -> confirmDeleteMenu(page.playlistId)
        // These draw their own content instead of a list.
        Page.NowPlaying, Page.Download, is Page.NameEntry -> emptyList()
    }

    // --- Top level ---

    private fun mainMenu() = buildList {
        add(MenuEntry("Music", opensSubmenu = true) { navigation.open(Page.Music) })
        add(MenuEntry("Mixes", opensSubmenu = true) { navigation.open(Page.Mixes) })
        add(MenuEntry("Shuffle Songs") { playAll(songs, shuffle = true) })
        add(MenuEntry("Stats", opensSubmenu = true) { navigation.open(Page.Stats) })
        add(MenuEntry("Download", opensSubmenu = true) { navigation.open(Page.Download) })
        add(MenuEntry("Settings", opensSubmenu = true) { navigation.open(Page.Settings) })
        if (currentSong != null) {
            add(MenuEntry("Now Playing", detail = currentSong.title, opensSubmenu = true) { navigation.open(Page.NowPlaying) })
        }
    }

    private fun musicMenu() = listOf(
        MenuEntry("Songs", detail = plural(songs.size, "song"), opensSubmenu = true) { navigation.open(Page.Songs) },
        MenuEntry("Artists", detail = plural(songs.map { it.artist }.distinct().size, "artist"), opensSubmenu = true) {
            navigation.open(Page.Artists)
        },
        MenuEntry("Albums", detail = plural(songs.map { it.album }.distinct().size, "album"), opensSubmenu = true) {
            navigation.open(Page.Albums)
        },
        MenuEntry("Playlists", detail = plural(playlists.size, "playlist"), opensSubmenu = true) { navigation.open(Page.Playlists) },
    )

    /**
     * A list of songs: choosing one plays the whole list from there. Used by Songs, an
     * artist, an album and a mix. Holding the center offers "Add to Playlist".
     */
    private fun songListMenu(list: List<Song>, withShuffle: Boolean = false) = buildList {
        if (withShuffle && list.size > 1) add(MenuEntry("Shuffle") { playAll(list, shuffle = true) })
        list.forEachIndexed { index, song ->
            add(
                MenuEntry(
                    song.title,
                    detail = song.artist,
                    onLongPress = { navigation.open(Page.AddToPlaylist(song.id)) },
                ) { playAll(list, startIndex = index) }
            )
        }
    }

    // --- Artists and albums ---

    /** Every artist, A to Z (ignoring upper/lower case). */
    private fun artistsMenu() = songs
        .groupBy { it.artist }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        .map { (artist, artistSongs) ->
            MenuEntry(artist, detail = plural(artistSongs.size, "song"), opensSubmenu = true) { navigation.open(Page.Artist(artist)) }
        }

    /** Every album, A to Z, with songs that have no album tag collected at the end. */
    private fun albumsMenu(): List<MenuEntry> {
        val byAlbum = songs.groupBy { it.album }
        val named = (byAlbum - MusicScanner.UNKNOWN_ALBUM).toSortedMap(String.CASE_INSENSITIVE_ORDER)
        val unknown = byAlbum[MusicScanner.UNKNOWN_ALBUM]
        return buildList {
            named.forEach { (album, albumSongs) ->
                val artists = albumSongs.map { it.artist }.distinct()
                add(MenuEntry(album, detail = artists.singleOrNull() ?: "Various artists", opensSubmenu = true) {
                    navigation.open(Page.Album(album))
                })
            }
            if (unknown != null) {
                add(MenuEntry(MusicScanner.UNKNOWN_ALBUM, detail = plural(unknown.size, "song"), opensSubmenu = true) {
                    navigation.open(Page.Album(MusicScanner.UNKNOWN_ALBUM))
                })
            }
        }
    }

    // --- Mixes and stats ---

    private fun mixesMenu() = mixes.map { mix ->
        MenuEntry(mix.title, detail = mix.description, opensSubmenu = true) { navigation.open(Page.Mix(mix.id)) }
    }

    /** "Play Mix", then its songs. */
    private fun mixMenu(mixId: String): List<MenuEntry> {
        val mix = mixes.firstOrNull { it.id == mixId } ?: return emptyList()
        val mixSongs = mix.songFileNames.mapNotNull { songsByFileName[it] }
        return listOf(MenuEntry("Play Mix", detail = plural(mixSongs.size, "song")) { playAll(mixSongs) }) +
            songListMenu(mixSongs)
    }

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
                        onLongPress = { navigation.open(Page.AddToPlaylist(song.id)) },
                    ) { playAll(topSongs, startIndex = index) }
                )
            }
        }
    }

    // --- Settings ---

    private fun settingsMenu() = listOf(
        MenuEntry("Clicker", detail = if (settings.clickSound) "On" else "Off") { settings.toggleClickSound() },
        MenuEntry("Wheel vibration", detail = if (settings.vibration) "On" else "Off") { settings.toggleVibration() },
        MenuEntry("About", detail = "Re-start · your music, your player") {},
    )

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
            MenuEntry("Add to Playlist…", opensSubmenu = true) { navigation.open(Page.AddToPlaylist(song.id)) },
        )
    }

    /** The queue after the current song, in play order. Choosing one jumps there. */
    private fun upNextMenu() = playerState.upNext.mapNotNull { entry ->
        val song = songsById[entry.songId] ?: return@mapNotNull null
        MenuEntry(song.title, detail = song.artist) {
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
                    add(MenuEntry(song.title, detail = song.artist, onLongPress = openOptions) {
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

    private fun addToPlaylistMenu(songId: Long): List<MenuEntry> {
        val song = songsById[songId] ?: return emptyList()
        return buildList {
            add(MenuEntry("New Playlist…") { startNameEntry(Page.NameEntry(firstSongId = songId), "") })
            playlists.forEach { playlist ->
                add(MenuEntry(playlist.name, detail = plural(playlist.songCount, "song")) {
                    playlistActions.addSong(playlist.id, song)
                    showMessage("Added to ${playlist.name}")
                    navigation.back()
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
