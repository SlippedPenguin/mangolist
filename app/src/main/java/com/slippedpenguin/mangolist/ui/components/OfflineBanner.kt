package com.slippedpenguin.mangolist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.slippedpenguin.mangolist.AnimeApp
import com.slippedpenguin.mangolist.ui.theme.Accent

/**
 * Sticky offline banner shown at the top of network-dependent screens.
 *
 * The banner observes [AnimeApp.networkObserver] and animates in/out when
 * the device loses/regains connectivity. It is safe to place inside scrollable
 * content because it is a fixed-height composable that simply shows/hides.
 */
@Composable
fun OfflineBanner() {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val isOnline by app.networkObserver.isOnline.collectAsState(initial = true)

    AnimatedVisibility(
        visible = !isOnline,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Accent.copy(alpha = 0.85f))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No internet connection",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
