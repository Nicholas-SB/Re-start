package com.example.custommusicplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The app's colors, mapped onto Material 3's "color roles". Compose components pick their
 * colors from these roles, e.g. a Button uses `primary` for its background and `onPrimary`
 * for its text, a Card uses `surfaceContainerHighest`. Change a role here and every
 * component using it follows.
 *
 * Primary is white (with black on top), so main buttons look like YouTube Music's white
 * "Play" pill and round play button.
 */
private val MusicColorScheme = darkColorScheme(
    primary = TextPrimary,
    onPrimary = Background,
    primaryContainer = SurfaceHigh,
    onPrimaryContainer = TextPrimary,
    secondary = TextSecondary,
    onSecondary = Background,
    secondaryContainer = SurfaceMid,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentRed,
    onTertiary = TextPrimary,
    background = Background,
    onBackground = TextPrimary,
    surface = Background,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceMid,
    onSurfaceVariant = TextSecondary,
    surfaceTint = Background,  // no tint when surfaces are "elevated"
    surfaceContainerLowest = Background,
    surfaceContainerLow = SurfaceLow,
    surfaceContainer = SurfaceLow,
    surfaceContainerHigh = SurfaceMid,
    surfaceContainerHighest = SurfaceMid,
    outline = Divider,
    outlineVariant = Divider,
    error = AccentRed,
    onError = TextPrimary,
)

/**
 * Always dark, like YouTube Music. (The template's light theme and "dynamic colors from
 * your wallpaper" are gone; milestone 8 can bring back theme choices later.)
 */
@Composable
fun CustomMusicPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MusicColorScheme,
        typography = Typography,
        content = content,
    )
}
