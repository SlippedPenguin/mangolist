package com.slippedpenguin.mangolist.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.slippedpenguin.mangolist.AnimeApp

/*
 * Profile — login state, viewer stats, sync button. v1.0 wires AniList's
 * implicit-flow / PIN exchange and GetViewer.GetStatistics.Anime.
 */
@Composable
fun ProfileScreen(@Suppress("UNUSED_PARAMETER") navController: NavController) {
    val context = LocalContext.current
    val app = remember { context.applicationContext as AnimeApp }
    val userName by app.tokenStore.userName.collectAsState(initial = null)
    val entryCount by app.database.animeDao().observeAll().collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (userName == null) "Not signed in" else "Hi, $userName",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${entryCount.size} anime in your local list",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Mean score", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("—", style = MaterialTheme.typography.titleLarge)
                Text("Episodes watched", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("—", style = MaterialTheme.typography.titleLarge)
                Text("Minutes watched", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("—", style = MaterialTheme.typography.titleLarge)
            }
        }

        Spacer(Modifier.height(24.dp))

        if (userName == null) {
            Button(
                onClick = { /* v1.0: launch AniList PIN flow */ },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Log in with AniList") }
        } else {
            OutlinedButton(
                onClick = { /* v1.0: pull GetMediaListCollection, reconcile */ },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sync now") }
        }
    }
}
