package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/*
 * Airing — stubbed. v1.0 wires GetAiringSchedule (airingAt_greater = now,
 * airingAt_lesser = now + 7 days) into a 7-day timeline with relative
 * "In 3d 12h" countdown. The placeholder keeps the tab live so the scaffold
 * compiles and the nav graph stays valid.
 */
@Composable
fun AiringScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = "Airing this week",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Coming soon — the v1.0 build pulls the next 7 days from AniList and shows a relative countdown per row.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
    }
}
