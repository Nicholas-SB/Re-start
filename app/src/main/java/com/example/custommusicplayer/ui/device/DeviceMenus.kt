package com.example.custommusicplayer.ui.device

import com.example.custommusicplayer.data.Song
import com.example.custommusicplayer.data.db.PlaylistSummary
import com.example.custommusicplayer.ui.PlayerViewModel
import com.example.custommusicplayer.ui.PlaylistDetail
import com.example.custommusicplayer.ui.PlaylistViewModel

/**
 * What every menu page lists, and what choosing a row does. No drawing here: RestartApp
 * makes a DeviceMenus with the latest data on each redraw and asks it for titles and rows.
 */
class DeviceMenus(
    private val songs: List<Song>,
    private val songsById: Map<Long, Song>,
    private val songsByFileName: Map<String, Song>,
    private val currentSong: Song?,
    private val playlists: List<PlaylistSummary>,
    private val shownPlaylist: PlaylistDetail?,  // the playlist the current page is about, once loaded
    private val navigation: DeviceNavigationViewModel,
    private val player: PlayerViewModel,
    private val playlistActions: PlaylistViewModel,
    private val showMessage: (String) -> Unit,
    private val startNameEntry: (page: Page.NameEntry, initialText: String) -> Unit,
    private val openClassicView: () -> Unit,
) {

    fun title(page: Page): String = when (page) {
        Page.MainMenu -> "Re-start"
        Page.Music -> "Music"
        Page.Songs -> "Songs"
        Page.NowPlaying -> "Now Playing"
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
        Page.Songs -> songsMenu()
        Page.Playlists -> playlistsMenu()
        is Page.Playlist -> playlistMenu(page.playlistId)
        is Page.PlaylistOptions -> playlistOptionsMenu(page.playlistId)
        is Page.PlaylistEntryOptions -> entryOptionsMenu(page.playlistId, page.entryId)
        is Page.AddToPlaylist -> addToPlaylistMenu(page.songId)
        is Page.ConfirmDelete -> confirmDeleteMenu(page.playlistId)
        // These two draw their own content instead of a list.
        Page.NowPlaying, is Page.NameEntry -> emptyList()
    }

    // --- Top level ---

    private fun mainMenu() = buildList {
        add(MenuEntry("Music", opensSubmenu = true) { navigation.open(Page.Music) })
        add(MenuEntry("Shuffle Songs") { playAll(songs, shuffle = true) })
        if (currentSong != null) {
            add(MenuEntry("Now Playing", opensSubmenu = true) { navigation.open(Page.NowPlaying) })
        }
        add(MenuEntry("Classic view", detail = "Mixes, stats, downloads", opensSubmenu = true, onSelect = openClassicView))
    }

    private fun musicMenu() = listOf(
        MenuEntry("Songs", detail = plural(songs.size, "song"), opensSubmenu = true) { navigation.open(Page.Songs) },
        MenuEntry("Playlists", detail = plural(playlists.size, "playlist"), opensSubmenu = true) { navigation.open(Page.Playlists) },
    )

    private fun songsMenu() = songs.mapIndexed { index, song ->
        MenuEntry(
            song.title,
            detail = song.artist,
            onLongPress = { navigation.open(Page.AddToPlaylist(song.id)) },
        ) { playAll(songs, startIndex = index) }
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
}
