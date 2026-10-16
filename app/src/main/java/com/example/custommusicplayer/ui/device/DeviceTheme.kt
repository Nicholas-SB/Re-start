package com.example.custommusicplayer.ui.device

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Re-start's palette: a warm silver body, a cream screen with mocha text, and a muted sage
 * highlight bar. All in one place, so it's easy to tweak.
 */
object DeviceColors {
    // The player's body (behind everything), a soft brushed-silver gradient.
    val BodyTop = Color(0xFFEFECE6)
    val BodyBottom = Color(0xFFD3CEC6)

    // The screen.
    val Screen = Color(0xFFF7F0E4)         // cream
    val ScreenFrame = Color(0xFF5E4A3B)    // mocha
    val HeaderBar = Color(0xFFEADFCD)      // slightly darker cream for the title bar
    val Text = Color(0xFF3F3129)           // dark mocha
    val TextSoft = Color(0xFF8C7767)       // light mocha, for secondary text
    val Highlight = Color(0xFFA7BBA1)      // muted sage, the selection bar
    val HighlightDark = Color(0xFF7F9879)  // deeper sage, for progress bars

    // The click wheel.
    val WheelTop = Color(0xFFFBF7F0)
    val WheelBottom = Color(0xFFECE4D8)
    val WheelEdge = Color(0xFFD5CABB)
    val WheelLabel = Color(0xFF8C7767)
    val CenterTop = Color(0xFFE6E3DE)      // silver center button
    val CenterBottom = Color(0xFFC9C4BC)
}

/**
 * Material components inside the screen (texts, buttons, the permission prompt, the lyrics
 * panel) take their colors from MaterialTheme, so give them a matching cream-and-mocha scheme.
 */
private val ScreenColorScheme = lightColorScheme(
    primary = DeviceColors.ScreenFrame,
    onPrimary = DeviceColors.Screen,
    secondary = DeviceColors.HighlightDark,
    onSecondary = DeviceColors.Text,
    secondaryContainer = DeviceColors.Highlight,
    onSecondaryContainer = DeviceColors.Text,
    background = DeviceColors.Screen,
    onBackground = DeviceColors.Text,
    surface = DeviceColors.Screen,
    onSurface = DeviceColors.Text,
    surfaceVariant = DeviceColors.HeaderBar,
    onSurfaceVariant = DeviceColors.TextSoft,
    // Cards (e.g. on the Download page) and dialogs use the "surfaceContainer" roles.
    surfaceContainerLowest = DeviceColors.Screen,
    surfaceContainerLow = DeviceColors.Screen,
    surfaceContainer = DeviceColors.HeaderBar,
    surfaceContainerHigh = DeviceColors.HeaderBar,
    surfaceContainerHighest = DeviceColors.HeaderBar,
    outline = DeviceColors.TextSoft,
)

@Composable
fun DeviceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ScreenColorScheme, content = content)
}
