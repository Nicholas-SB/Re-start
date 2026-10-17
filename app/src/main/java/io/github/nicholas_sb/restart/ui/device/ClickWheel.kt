package io.github.nicholas_sb.restart.ui.device

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import io.github.nicholas_sb.restart.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot

/**
 * The click wheel. Slide a finger around the ring to scroll (each step gives a little haptic
 * tick); tap MENU / ⏮ / ⏭ / ⏯ on the ring; hold ⏮ / ⏭ to rewind / fast-forward; press the
 * center button to select, or hold it for options.
 *
 * Like the other UI pieces, it doesn't decide what anything *does*: it just reports
 * what happened through the callbacks.
 */
@Composable
fun ClickWheel(
    onRotate: (steps: Int) -> Unit,
    onCenter: () -> Unit,
    onCenterHold: () -> Unit,
    onMenu: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onScan: (forward: Boolean, heldMs: Long) -> Unit,
    feedback: WheelFeedback,
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
    val scan by rememberUpdatedState(onScan)

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
                            feedback.tick(view)  // vibration and/or click, per the settings
                            rotate(steps)
                        }
                    },
                )
            }
            // Pressing a button on the ring. onPress runs when the finger goes down; inside it,
            // tryAwaitRelease() waits for the finger to lift (true) or the touch to turn into a
            // wheel turn (false). That's what lets ⏮ / ⏭ tell a tap from a hold.
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { press ->
                        val button = ringButtonAt(press.x, press.y, size) ?: return@detectTapGestures
                        if (button == RingButton.MENU || button == RingButton.PLAY_PAUSE) {
                            if (tryAwaitRelease()) {
                                feedback.press(view)
                                if (button == RingButton.MENU) menu() else playPause()
                            }
                            return@detectTapGestures
                        }

                        val forward = button == RingButton.NEXT
                        // Let go within HOLD_TO_SCAN_MS: a normal skip. Still holding: scan.
                        when (withTimeoutOrNull(HOLD_TO_SCAN_MS) { tryAwaitRelease() }) {
                            true -> {
                                feedback.press(view)
                                if (forward) next() else previous()
                            }
                            false -> {}  // the touch became a wheel turn
                            null -> {
                                feedback.hold(view)
                                // Two things at once: tick the scan along, and wait for the finger
                                // to lift. coroutineScope + launch runs the ticking alongside.
                                coroutineScope {
                                    val ticking = launch {
                                        val start = SystemClock.uptimeMillis()
                                        while (isActive) {
                                            scan(forward, SystemClock.uptimeMillis() - start)
                                            delay(SCAN_TICK_MS)
                                        }
                                    }
                                    tryAwaitRelease()
                                    ticking.cancel()
                                }
                            }
                        }
                    },
                )
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
                .combinedClickable(
                    onClick = {
                        feedback.press(view)
                        onCenter()
                    },
                    onLongClick = {
                        feedback.hold(view)
                        onCenterHold()
                    },
                ),
        )
    }
}

/** The center button's size, as a fraction of the whole wheel. */
private const val CENTER_FRACTION = 0.38f

/** How long to hold ⏮ / ⏭ before it scans instead of skipping, and how often it scans. */
private const val HOLD_TO_SCAN_MS = 400L
private const val SCAN_TICK_MS = 150L

private enum class RingButton { MENU, NEXT, PLAY_PAUSE, PREVIOUS }

/** Which ring button is at (x, y), or null for the center button. */
private fun ringButtonAt(x: Float, y: Float, size: IntSize): RingButton? {
    val cx = size.width / 2f
    val cy = size.height / 2f
    if (hypot(x - cx, y - cy) < size.width * CENTER_FRACTION / 2) return null
    val angle = WheelRotationTracker.angleOf(x, y, cx, cy)
    return when {
        angle in -135f..-45f -> RingButton.MENU      // top
        angle in -45f..45f -> RingButton.NEXT        // right
        angle in 45f..135f -> RingButton.PLAY_PAUSE  // bottom
        else -> RingButton.PREVIOUS                  // left
    }
}
