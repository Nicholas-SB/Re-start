package io.github.nicholas_sb.restart.ui.device

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nicholas_sb.restart.R
import io.github.nicholas_sb.restart.data.AlbumArtLoader
import io.github.nicholas_sb.restart.data.Song
import io.github.nicholas_sb.restart.ui.RepeatMode
import io.github.nicholas_sb.restart.ui.SongArt

/**
 * One row in a menu. [opensSubmenu] shows a "›" like the iPod did for rows that lead deeper.
 * [onLongPress] runs when you hold the center button (or long-press the row): options.
 * [song], if set, shows that song's cover on the left of the row.
 */
data class MenuEntry(
    val label: String,
    val detail: String? = null,
    val opensSubmenu: Boolean = false,
    val song: Song? = null,
    val onLongPress: (() -> Unit)? = null,
    val onSelect: () -> Unit,
)

/**
 * The bar at the top of the screen. Left: play/pause state, plus shuffle and repeat icons
 * while those are on. Middle: the page title.
 */
@Composable
fun DeviceHeader(
    title: String,
    isPlaying: Boolean?,  // null = nothing loaded, so no icons
    shuffle: Boolean,
    repeatMode: RepeatMode,
    sleepTimerOn: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(DeviceColors.HeaderBar)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        if (isPlaying != null) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusIcon(if (isPlaying) R.drawable.ic_play else R.drawable.ic_pause, if (isPlaying) "Playing" else "Paused")
                if (shuffle) StatusIcon(R.drawable.ic_shuffle, "Shuffle on")
                when (repeatMode) {
                    RepeatMode.OFF -> {}
                    RepeatMode.ALL -> StatusIcon(R.drawable.ic_repeat, "Repeat all")
                    RepeatMode.ONE -> StatusIcon(R.drawable.ic_repeat_one, "Repeat one")
                }
                if (sleepTimerOn) StatusIcon(R.drawable.ic_sleep, "Sleep timer on")
            }
        }
        Text(
            title,
            color = DeviceColors.Text,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
        )
    }
}

@Composable
private fun StatusIcon(icon: Int, description: String) {
    Icon(painterResource(icon), contentDescription = description, tint = DeviceColors.Text, modifier = Modifier.size(16.dp))
}

/**
 * A list of [entries] with the [selectedIndex] row highlighted. The wheel moves the highlight;
 * touching a row also works (it selects and opens it).
 */
@Composable
fun MenuPage(
    entries: List<MenuEntry>,
    selectedIndex: Int,
    onTap: (index: Int) -> Unit,
    onLongTap: (index: Int) -> Unit,
    artLoader: AlbumArtLoader,
    modifier: Modifier = Modifier,
    emptyText: String = "Nothing here",
) {
    if (entries.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = DeviceColors.TextSoft)
        }
        return
    }

    // Start with the highlighted row in view (coming back to a long list doesn't jump).
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 3).coerceAtLeast(0))
    val animate = LocalAnimations.current
    // Keep the highlighted row on screen as the wheel moves it: gliding, or jumping if
    // animations are off. (A new turn cancels a glide that's still going and starts another.)
    LaunchedEffect(selectedIndex) {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return@LaunchedEffect
        val firstFullyVisible = visible.first().index
        val lastFullyVisible = visible.last().index - 1  // the last row is often cut off
        val target = when {
            selectedIndex < firstFullyVisible -> selectedIndex
            selectedIndex > lastFullyVisible -> (selectedIndex - (visible.size - 2)).coerceAtLeast(0)
            else -> return@LaunchedEffect
        }
        if (animate) listState.animateScrollToItem(target) else listState.scrollToItem(target)
    }

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        itemsIndexed(entries) { index, entry ->
            MenuRow(
                entry = entry,
                isSelected = index == selectedIndex,
                onClick = { onTap(index) },
                onLongClick = { onLongTap(index) },
                artLoader = artLoader,
            )
        }
    }
}

@Composable
private fun MenuRow(
    entry: MenuEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    artLoader: AlbumArtLoader,
) {
    // The highlight fades in on the new row as it fades out on the old one, so it seems to glide.
    val top by animateColorAsState(if (isSelected) DeviceColors.Highlight else DeviceColors.Screen, motionSpec(120), label = "highlight")
    val bottom by animateColorAsState(if (isSelected) DeviceColors.HighlightDark else DeviceColors.Screen, motionSpec(120), label = "highlight")
    val background = Brush.verticalGradient(listOf(top, bottom))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            // combinedClickable = clickable plus long-press (and double-tap, unused here).
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Song rows show the cover, so you can spot a song without remembering its title.
        if (entry.song != null) {
            SongArt(entry.song, artLoader, Modifier.size(42.dp))
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.label,
                color = DeviceColors.Text,
                fontSize = 18.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.detail != null) {
                Text(
                    entry.detail,
                    color = if (isSelected) DeviceColors.Text else DeviceColors.TextSoft,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (entry.opensSubmenu) {
            Text("›", color = DeviceColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}
