package com.example.custommusicplayer.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.custommusicplayer.R

/**
 * One row in a menu. [opensSubmenu] shows a "›" like the iPod did for rows that lead deeper.
 * [onLongPress] runs when you hold the center button (or long-press the row): options.
 */
data class MenuEntry(
    val label: String,
    val detail: String? = null,
    val opensSubmenu: Boolean = false,
    val onLongPress: (() -> Unit)? = null,
    val onSelect: () -> Unit,
)

/** The bar at the top of the screen: play state (left), page title (middle), battery (right). */
@Composable
fun DeviceHeader(title: String, isPlaying: Boolean?, battery: BatteryLevel?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(DeviceColors.HeaderBar)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        // null = nothing loaded, so no icon.
        if (isPlaying != null) {
            Icon(
                painterResource(if (isPlaying) R.drawable.ic_play else R.drawable.ic_pause),
                contentDescription = if (isPlaying) "Playing" else "Paused",
                tint = DeviceColors.Text,
                modifier = Modifier.align(Alignment.CenterStart).size(16.dp),
            )
        }
        Text(
            title,
            color = DeviceColors.Text,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
        )
        if (battery != null) {
            BatteryIcon(battery, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
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
    modifier: Modifier = Modifier,
    emptyText: String = "Nothing here",
) {
    if (entries.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = DeviceColors.TextSoft)
        }
        return
    }

    val listState = rememberLazyListState()
    // Keep the highlighted row on screen as the wheel moves it.
    LaunchedEffect(selectedIndex) {
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return@LaunchedEffect
        val firstFullyVisible = visible.first().index
        val lastFullyVisible = visible.last().index - 1  // the last row is often cut off
        when {
            selectedIndex < firstFullyVisible -> listState.scrollToItem(selectedIndex)
            selectedIndex > lastFullyVisible -> listState.scrollToItem((selectedIndex - (visible.size - 2)).coerceAtLeast(0))
        }
    }

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        itemsIndexed(entries) { index, entry ->
            MenuRow(
                entry = entry,
                isSelected = index == selectedIndex,
                onClick = { onTap(index) },
                onLongClick = { onLongTap(index) },
            )
        }
    }
}

@Composable
private fun MenuRow(entry: MenuEntry, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val background = if (isSelected) {
        Brush.verticalGradient(listOf(DeviceColors.Highlight, DeviceColors.HighlightDark))
    } else {
        Brush.verticalGradient(listOf(DeviceColors.Screen, DeviceColors.Screen))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            // combinedClickable = clickable plus long-press (and double-tap, unused here).
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
