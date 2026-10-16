package com.example.custommusicplayer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

// Start from Material's default sizes and make headings bold, which is most of what makes
// YouTube Music's text feel the way it does. `copy()` makes a modified copy of a style
// (every data class gets copy(), like a builder in one call).
private val defaults = Typography()

val Typography = Typography(
    headlineMedium = defaults.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = defaults.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = defaults.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = defaults.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = defaults.titleSmall.copy(fontWeight = FontWeight.Bold),
)
