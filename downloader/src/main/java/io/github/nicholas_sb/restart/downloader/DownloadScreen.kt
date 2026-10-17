package io.github.nicholas_sb.restart.downloader

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * Paste a YouTube or YouTube Music link, press Download: the song goes into the music folder
 * and, if LRCLIB has them, synced lyrics are saved next to it.
 * With [onOpenPlaylistImport], a "Playlist" button leads to [PlaylistImportScreen].
 */
@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
    onOpenPlaylistImport: (() -> Unit)? = null,
) {
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    var link by rememberSaveable { mutableStateOf("") }
    val kind = linkKind(link)
    val isPlaylist = isWholePlaylist(link)
    val askForNotifications = rememberNotificationPermission()

    LazyColumn(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            if (showTitle) {
                Text("Download", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))
            } else {
                Spacer(Modifier.height(12.dp))
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Text("Downloading needs Android 10 or newer.", color = MaterialTheme.colorScheme.error)
                return@item
            }

            LinkField(link, onLinkChange = { link = it }, label = "YouTube or YouTube Music link") {
                when {
                    kind == LinkKind.SPOTIFY -> Text("Spotify links aren't supported yet. Use the song's YouTube Music link.")
                    kind == LinkKind.UNKNOWN && link.isNotBlank() -> Text("That doesn't look like a YouTube link.")
                    isPlaylist -> Text("That's a playlist: use Playlist below.")
                }
            }
            Button(
                onClick = {
                    askForNotifications()
                    viewModel.download(link)
                    link = ""
                },
                enabled = kind == LinkKind.YOUTUBE && !isPlaylist,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Download")
            }
            if (onOpenPlaylistImport != null) {
                OutlinedButton(onClick = onOpenPlaylistImport, modifier = Modifier.fillMaxWidth()) {
                    Text("Playlist")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        items(jobs.filter { it.needsAttention() }, key = { it.id }) { job ->
            DownloadJobCard(job, onFindSameSong = viewModel::findSameSong)
        }
    }
}

/**
 * Paste a YouTube or YouTube Music playlist link and pick where its songs go: a new playlist
 * or one you already have. Each song is downloaded (or reused, if it's already on the phone)
 * and added in the playlist's order.
 */
@Composable
fun PlaylistImportScreen(viewModel: DownloadViewModel, playlists: List<PlaylistChoice>, modifier: Modifier = Modifier) {
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    var link by rememberSaveable { mutableStateOf("") }
    var newName by rememberSaveable { mutableStateOf("") }
    // Which existing playlist to add to; null means "a new playlist".
    var chosenId by rememberSaveable { mutableStateOf<Long?>(null) }
    val chosen = playlists.firstOrNull { it.id == chosenId }  // also null if it was deleted meanwhile
    val playlist = playlistLink(link)
    val askForNotifications = rememberNotificationPermission()

    LazyColumn(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Text("Downloading needs Android 10 or newer.", color = MaterialTheme.colorScheme.error)
                return@item
            }

            LinkField(link, onLinkChange = { link = it }, label = "YouTube or YouTube Music playlist link") {
                when {
                    link.isBlank() || playlist != null -> {}
                    linkKind(link) == LinkKind.YOUTUBE -> Text("That's a single song: use Download.")
                    else -> Text("That doesn't look like a YouTube playlist link.")
                }
            }
            SaveToMenu(playlists, chosen, onChoose = { chosenId = it?.id })
            if (chosen == null) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New playlist name") },
                    placeholder = { Text("Same as on YouTube") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            Button(
                onClick = {
                    val target = if (chosen != null) {
                        ImportTarget.ExistingPlaylist(chosen.id, chosen.name)
                    } else {
                        ImportTarget.NewPlaylist(newName)
                    }
                    askForNotifications()
                    viewModel.importPlaylist(link, target)
                    link = ""
                    newName = ""
                },
                enabled = playlist != null,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
            ) {
                Text("Import")
            }
        }
        items(jobs.filter { it.needsAttention() }, key = { it.id }) { job ->
            DownloadJobCard(job, onFindSameSong = viewModel::findSameSong)
        }
    }
}

/**
 * Only what's worth a look: the song being downloaded, songs that failed, and playlist
 * summaries. Finished songs (new or already on the phone) and a playlist's songs that are
 * still waiting their turn are left out, so the list stays short.
 */
private fun DownloadJob.needsAttention(): Boolean = when (status) {
    is DownloadStatus.Done -> false
    DownloadStatus.Waiting -> playlistName == null
    else -> true
}

/**
 * Returns a function that asks for permission to show notifications (Android 13+), so the
 * download progress can appear there. Downloads work either way.
 */
@Composable
private fun rememberNotificationPermission(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** A link box with a paste button. [hint] shows under it (what's wrong with the link, if anything). */
@Composable
private fun LinkField(link: String, onLinkChange: (String) -> Unit, label: String, hint: @Composable () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    OutlinedTextField(
        value = link,
        onValueChange = onLinkChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = {
                // Reading the clipboard is a suspend call, so run it in a coroutine.
                scope.launch {
                    clipboard.getClipEntry()?.clipData?.getItemAt(0)?.text?.let { onLinkChange(it.toString().trim()) }
                }
            }) {
                Icon(painterResource(R.drawable.ic_paste), contentDescription = "Paste link")
            }
        },
        supportingText = hint,
    )
}

/** A "Save to: …" button that opens a dropdown: a new playlist, or one of yours. */
@Composable
private fun SaveToMenu(playlists: List<PlaylistChoice>, chosen: PlaylistChoice?, onChoose: (PlaylistChoice?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    // The Box anchors the dropdown to the button.
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Save to: ${chosen?.name ?: "New playlist"}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("New playlist") }, onClick = {
                onChoose(null)
                open = false
            })
            playlists.forEach { playlist ->
                DropdownMenuItem(text = { Text(playlist.name) }, onClick = {
                    onChoose(playlist)
                    open = false
                })
            }
        }
    }
}

@Composable
private fun DownloadJobCard(job: DownloadJob, onFindSameSong: (jobId: Int) -> Unit) {
    // Tapping a failed one offers to open it on YouTube (to see what's wrong), or, when we know
    // which song it was, to look for the same song on YouTube Music. Nothing happens by itself.
    val failed = job.status is DownloadStatus.Failed
    val canSearch = failed && job.title != null && job.playlistId != null
    val uriHandler = LocalUriHandler.current
    var showOptions by remember { mutableStateOf(false) }
    fun openOnYoutube() {
        try {
            uriHandler.openUri(job.url)
        } catch (e: Exception) {
            // No app on the phone can open web links: nothing to do.
        }
    }
    // A Box, so the options menu pops up anchored to the card.
    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(CardDefaults.shape)  // so the tap ripple has the card's rounded corners
                .clickable(enabled = failed) { if (canSearch) showOptions = true else openOnYoutube() },
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = job.title ?: job.url,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                job.playlistName?.let { playlistName ->
                    Text("→ $playlistName", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                val status = job.status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (status) {
                            DownloadStatus.Waiting -> "Waiting…"
                            is DownloadStatus.Preparing -> status.message
                            is DownloadStatus.Downloading -> "Downloading… ${status.percent.toInt()}%"
                            DownloadStatus.FindingLyrics -> "Looking for lyrics…"
                            is DownloadStatus.Done -> {
                                val song = if (status.alreadyInLibrary) "Already in your library" else "Done"
                                val lyrics = when (status.lyrics) {
                                    LyricsOutcome.SAVED -> "lyrics saved"
                                    LyricsOutcome.ALREADY_THERE -> "already had lyrics"
                                    LyricsOutcome.NOT_FOUND -> "no lyrics found"
                                    LyricsOutcome.NOT_CHECKED -> null
                                }
                                listOfNotNull(song, lyrics).joinToString(" · ")
                            }
                            is DownloadStatus.Failed -> "Failed: ${status.message}"
                            is DownloadStatus.PlaylistProgress ->
                                if (status.finished < status.total) {
                                    "Into ${status.playlistName} · ${status.finished} of ${status.total} songs"
                                } else {
                                    playlistSummary(status)
                                }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (status is DownloadStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (failed) {
                    Text(
                        if (canSearch) "Tap for options" else "Tap to open on YouTube",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (status) {
                    is DownloadStatus.Downloading ->
                        LinearProgressIndicator(progress = { status.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    is DownloadStatus.PlaylistProgress -> if (status.finished < status.total) {
                        LinearProgressIndicator(progress = { status.finished / status.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                    }
                    is DownloadStatus.Preparing, DownloadStatus.FindingLyrics, DownloadStatus.Waiting ->
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())  // no percentage: animated bar
                    else -> {}
                }
            }
        }
        DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
            DropdownMenuItem(text = { Text("Open on YouTube") }, onClick = {
                showOptions = false
                openOnYoutube()
            })
            DropdownMenuItem(text = { Text("Find the same song") }, onClick = {
                showOptions = false
                onFindSameSong(job.id)
            })
        }
    }
}

/** e.g. "Done · 3 downloaded, 8 already on your phone, 1 failed", then "Added 5 to Gym · 6 were already in it" */
private fun playlistSummary(status: DownloadStatus.PlaylistProgress): String {
    val got = listOfNotNull(
        "${status.downloaded} downloaded".takeIf { status.downloaded > 0 },
        "${status.reused} already on your phone".takeIf { status.reused > 0 },
        "${status.failedTitles.size} failed".takeIf { status.failedTitles.isNotEmpty() },
    ).ifEmpty { listOf("nothing downloaded") }.joinToString(", ")
    val added = "Added ${status.added} to ${status.playlistName}" +
        when (status.alreadyInPlaylist) {
            0 -> ""
            1 -> " · 1 was already in it"
            else -> " · ${status.alreadyInPlaylist} were already in it"
        }
    return "Done · $got\n$added"
}
