package com.example.custommusicplayer.downloader

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * Paste a YouTube or YouTube Music link, press Download: the song goes into the music folder
 * and, if LRCLIB has them, synced lyrics are saved next to it.
 */
@Composable
fun DownloadScreen(viewModel: DownloadViewModel, modifier: Modifier = Modifier) {
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    var link by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val kind = linkKind(link)

    LazyColumn(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Text("Download", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Text("Downloading needs Android 10 or newer.", color = MaterialTheme.colorScheme.error)
                return@item
            }

            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text("YouTube or YouTube Music link") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        // Reading the clipboard is a suspend call, so run it in a coroutine.
                        scope.launch {
                            clipboard.getClipEntry()?.clipData?.getItemAt(0)?.text?.let { link = it.toString().trim() }
                        }
                    }) {
                        Icon(painterResource(R.drawable.ic_paste), contentDescription = "Paste link")
                    }
                },
                supportingText = {
                    when (kind) {
                        LinkKind.SPOTIFY -> Text("Spotify links aren't supported yet. Use the song's YouTube Music link.")
                        LinkKind.UNKNOWN -> if (link.isNotBlank()) Text("That doesn't look like a YouTube link.")
                        LinkKind.YOUTUBE -> {}
                    }
                },
            )
            Button(
                onClick = {
                    viewModel.download(link)
                    link = ""
                },
                enabled = kind == LinkKind.YOUTUBE,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
            ) {
                Text("Download")
            }
        }
        items(jobs, key = { it.id }) { job ->
            DownloadJobCard(job)
        }
    }
}

@Composable
private fun DownloadJobCard(job: DownloadJob) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = job.title ?: job.url,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
                            }
                            "$song · $lyrics"
                        }
                        is DownloadStatus.Failed -> "Failed: ${status.message}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status is DownloadStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (status) {
                is DownloadStatus.Downloading ->
                    LinearProgressIndicator(progress = { status.percent / 100f }, modifier = Modifier.fillMaxWidth())
                is DownloadStatus.Preparing, DownloadStatus.FindingLyrics, DownloadStatus.Waiting ->
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())  // no percentage: animated bar
                else -> {}
            }
        }
    }
}
