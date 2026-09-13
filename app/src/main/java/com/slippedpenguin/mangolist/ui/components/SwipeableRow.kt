package com.slippedpenguin.mangolist.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slippedpenguin.mangolist.ui.theme.Accent
import com.slippedpenguin.mangolist.ui.theme.Accent2
import com.slippedpenguin.mangolist.ui.theme.TextMuted
import com.slippedpenguin.mangolist.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.abs

/*
 * SwipeableRow — v1.9 one-tap tracking (the ManGo headliner, requested by
 * the user). Wrap any watchlist card to get:
 *
 *   - swipe RIGHT → "+1" (episode/chapter/volume increment)
 *   - swipe LEFT  → "−1"
 *
 * While dragging, the action lane is revealed behind the card. Releasing
 * past the trigger threshold (60% of the half-width — generous enough that
 * vertical scrolling never fires it) fires the callback with a haptic tick
 * and springs the card back.
 *
 * Gesture coexistence with the parent LazyColumn: detectHorizontalDrag-
 * Gestures only claims the gesture after horizontal motion passes touch
 * slop, so vertical scroll keeps working normally.
 */
@Composable
fun SwipeableRow(
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    modifier: Modifier = Modifier,
    plusLabel: String = "+1",
    minusLabel: String = "\u22121",
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offsetX = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var rowWidthPx by remember { mutableStateOf(1) }

    val half = (rowWidthPx / 2f).coerceAtLeast(1f)
    val trigger = half * 0.6f
    val currentOffset = offsetX.value

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidthPx = it.width.coerceAtLeast(1) }
            .clip(RoundedCornerShape(16.dp)),
    ) {
        // ---- Action lanes (behind the card) --------------------------------
        // Swipe RIGHT exposes the LEFT edge: the "+1" lane in brand gradient.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(with(density) { currentOffset.coerceAtLeast(0f).toDp() })
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(listOf(Accent, Accent2)),
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (currentOffset > 24f) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 18.dp),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = Color(0xFF0A0A14),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = plusLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF0A0A14),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
        // Swipe LEFT exposes the RIGHT edge: the "−1" lane, muted.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(with(density) { (-currentOffset).coerceAtLeast(0f).toDp() })
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (currentOffset < -24f) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 18.dp),
                ) {
                    Text(
                        text = minusLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                    )
                    Icon(
                        Icons.Filled.Remove,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(20.dp),
                    )
                }
            }
        }

        // ---- The card itself, translated by the drag ------------------------
        Box(
            modifier = Modifier
                .graphicsLayer { translationX = currentOffset }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            val passed = abs(offsetX.value) >= trigger
                            if (passed) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                if (offsetX.value > 0) onSwipeRight() else onSwipeLeft()
                            }
                            scope.launch {
                                offsetX.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch { offsetX.animateTo(0f) }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        val from = offsetX.value
                        val to = (from + dragAmount).coerceIn(-half, half)
                        scope.launch { offsetX.snapTo(to) }
                    }
                },
        ) {
            content()
        }
    }
}
