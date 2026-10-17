package io.github.nicholas_sb.restart.ui.device

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Whether animations are on (Extras → Animations).
 *
 * A CompositionLocal is a value handed down the whole UI tree without passing it through every
 * function's parameters, a bit like a global that's scoped to part of the screen. RestartApp
 * provides it; anything inside reads it with `LocalAnimations.current`.
 */
val LocalAnimations = compositionLocalOf { true }

/** How long the page slide and most fades take. */
const val MOTION_MS = 220

/** A smooth change when animations are on; an instant one when they're off. */
@Composable
fun <T> motionSpec(durationMs: Int = MOTION_MS): FiniteAnimationSpec<T> =
    if (LocalAnimations.current) tween(durationMs, easing = FastOutSlowInEasing) else snap()
