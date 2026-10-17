package io.github.nicholas_sb.restart.ui.device

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel

/**
 * The screens the player can show. Opening one pushes it on a stack; MENU goes back.
 * Pages that are about one thing carry its id (a `data class`), so e.g. two different
 * playlists are two different pages, each remembering its own highlighted row.
 */
sealed interface Page {
    data object MainMenu : Page
    data object Music : Page
    data object Songs : Page

    /** Type to find songs and playlists. */
    data object Search : Page

    /** New songs from YouTube Music, picked from what you play most. */
    data object ForYou : Page
    data object Stats : Page
    data object Download : Page

    /** Download a whole YouTube playlist into one of yours. */
    data object PlaylistImport : Page
    /** Download, Stats and the settings: everything that isn't playing music. */
    data object Extras : Page
    data object MusicFolder : Page
    data object NowPlaying : Page

    /** Hold the center on Now Playing: shuffle, repeat, up next, add to playlist. */
    data object NowPlayingOptions : Page
    data object UpNext : Page

    data object Playlists : Page
    data class Playlist(val playlistId: Long) : Page

    /** Options for one playlist: play, shuffle, rename, delete. */
    data class PlaylistOptions(val playlistId: Long) : Page

    /** Options for one song inside a playlist: move, remove. */
    data class PlaylistEntryOptions(val playlistId: Long, val entryId: Long) : Page

    /** Hold the center on a song: start a mix from it, or add it to a playlist. */
    data class SongOptions(val songId: Long) : Page

    /** Pick a playlist to add [songId] to. */
    data class AddToPlaylist(val songId: Long) : Page

    /** Typing a name: for a new playlist (optionally starting with a song), or a rename. */
    data class NameEntry(val renamePlaylistId: Long? = null, val firstSongId: Long? = null) : Page

    data class ConfirmDelete(val playlistId: Long) : Page

    /** "Delete this song from your phone and all playlists?" */
    data class ConfirmDeleteSong(val songId: Long) : Page

    /** Stop the music after a while. */
    data object SleepTimer : Page

    /** Back up playlists, stats and settings to a file, or restore them from one. */
    data object Backup : Page

    /** "Replace everything with this backup?" after picking a backup file. */
    data object ConfirmRestore : Page
}

/**
 * Where you are in the menus, and which row is highlighted on each page.
 *
 * It lives in a ViewModel so it survives screen rotation. mutableStateListOf and
 * mutableStateMapOf are a List and a Map that Compose watches, just like mutableStateOf
 * for a single value: changing them redraws whatever reads them.
 */
class DeviceNavigationViewModel : ViewModel() {

    private val stack = mutableStateListOf<Page>(Page.MainMenu)
    private val selections = mutableStateMapOf<Page, Int>()

    val currentPage: Page get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    /** How many pages deep we are (the main menu is 1). Deeper = slide in from the right. */
    val depth: Int get() = stack.size

    fun open(page: Page) {
        stack.remove(page)  // never have the same page twice in the stack
        stack.add(page)
    }

    /** Back one level (the MENU button). Returns false if already at the main menu. */
    fun back(): Boolean {
        if (!canGoBack) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Goes back until [page] is showing (or the main menu, if it isn't in the stack). */
    fun backTo(page: Page) {
        while (canGoBack && currentPage != page) stack.removeAt(stack.lastIndex)
    }

    /** The highlighted row on [page] (remembered when you leave and come back, like the iPod). */
    fun selectedIndex(page: Page): Int = selections[page] ?: 0

    fun select(page: Page, index: Int) {
        selections[page] = index
    }

    /** Moves the highlight by [steps], staying inside a list of [itemCount] rows. */
    fun moveSelection(page: Page, steps: Int, itemCount: Int) {
        if (itemCount == 0) return
        selections[page] = (selectedIndex(page) + steps).coerceIn(0, itemCount - 1)
    }
}
