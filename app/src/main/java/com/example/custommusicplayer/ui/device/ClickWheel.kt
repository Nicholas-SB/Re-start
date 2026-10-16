package com.example.custommusicplayer.ui.device

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.custommusicplayer.R
import kotlin.math.hypot

/**
 * The click wheel. Slide a finger around the ring to scroll (each step gives a little haptic
 * tick); tap MENU / ⏮ / ⏭ / ⏯ on the ring; press the center button to select.
 *
 * Like the other UI pieces, it doesn't decide what anything *does*: it just reports
 * what happened through the callbacks.
 */
@Composable
fun ClickWheel(
    onRotate: (steps: Int) -> Unit,
    onCenter: () -> Unit,
    onMenu: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val tracker = remember { WheelRotationTracker() }

    // The gesture handlers below are set up once (pointerInput(Unit)) but must always call the
    // *latest* callbacks. rememberUpdatedState keeps a reference that's updated on every redraw.
    val rotate by rememberUpdatedState(onRotate)
    val menu by rememberUpdatedState(onMenu)
    val previous by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    val playPause by rememberUpdatedState(onPlayPause)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(elevation = 6.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(DeviceColors.WheelTop, DeviceColors.WheelBottom)))
            .border(1.dp, DeviceColors.WheelEdge, CircleShape)
            // Sliding around the ring.
            .pointerInput(Unit) {
                val center = { size.width / 2f to size.height / 2f }
                detectDragGestures(
                    onDragStart = { start ->
                        val (cx, cy) = center()
                        tracker.start(WheelRotationTracker.angleOf(start.x, start.y, cx, cy))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val (cx, cy) = center()
                        val steps = tracker.moveTo(WheelRotationTracker.angleOf(change.position.x, change.position.y, cx, cy))
                        if (steps != 0) {
                            // A short, crisp vibration: Android's effect for a ticking dial.
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            rotate(steps)
                        }
                    },
                )
            }
            // Tapping a button on the ring. Which button depends on where the tap is.
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    if (hypot(tap.x - cx, tap.y - cy) < size.width * CENTER_FRACTION / 2) return@detectTapGestures
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val angle = WheelRotationTracker.angleOf(tap.x, tap.y, cx, cy)
                    when {
                        angle in -135f..-45f -> menu()    // top
                        angle in -45f..45f -> next()      // right
                        angle in 45f..135f -> playPause() // bottom
                        else -> previous()                // left
                    }
                }
            },
    ) {
        val labelColor = DeviceColors.WheelLabel
        Text(
            "MENU",
            color = labelColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp),
        )
        Icon(
            painterResource(R.drawable.ic_skip_previous), contentDescription = "Previous", tint = labelColor,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp).size(28.dp),
        )
        Icon(
            painterResource(R.drawable.ic_skip_next), contentDescription = "Next", tint = labelColor,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp).size(28.dp),
        )
        Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)) {
            Icon(painterResource(R.drawable.ic_play), contentDescription = "Play or pause", tint = labelColor, modifier = Modifier.size(22.dp))
            Icon(painterResource(R.drawable.ic_pause), contentDescription = null, tint = labelColor, modifier = Modifier.size(22.dp))
        }

        // The center button: a silver circle on top of the ring.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize(CENTER_FRACTION)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(DeviceColors.CenterTop, DeviceColors.CenterBottom)))
                .border(1.dp, DeviceColors.WheelEdge, CircleShape)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onCenter()
                },
        )
    }
}

/** The center button's size, as a fraction of the whole wheel. */
private const val CENTER_FRACTION = 0.38f
