package com.slippedpenguin.mangolist

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.slippedpenguin.mangolist.ui.MangoNavRoot
import com.slippedpenguin.mangolist.ui.theme.MangoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/*
 * Single-activity host — Compose handles all navigation. Edge-to-edge lets
 * status/nav bars draw over the app surface so the bottom NavigationBar
 * matches the JS prototype's "floats over content" feel.
 *
 * Also handles the AniList OAuth redirect: after the user authorises in
 * Chrome Custom Tab, the browser hops back into the app via
 *   com.slippedpenguin.mangolist://callback#access_token=...
 * The token lives in the URL fragment, which Android's Uri API doesn't
 * surface through getQueryParameter — we split on `&` / `=` manually.
 */
class MainActivity : ComponentActivity() {

    // Activity-scoped IO routine — small fire-and-forget writes only.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Cold-start case: app opened fresh, Chrome Custom Tab fired straight
        // at us with the redirect Intent as this activity's start intent.
        handleAuthRedirect(intent)
        setContent {
            MangoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MangoNavRoot()
                }
            }
        }
    }

    /*
     * Warm-redirect case: this activity instance is already alive and Chrome
     * Custom Tab redirects back into it. android:launchMode="singleTop" on
     * MainActivity guarantees we land here instead of a fresh onCreate.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthRedirect(intent)
    }

    /*
     * Anchors on ANILIST_REDIRECT_URI from BuildConfig so the build-time
     * configuration is the single source of truth for the deep-link target.
     * Currently parses the access_token off the URL fragment and saves it
     * with placeholder userId=0 / userName=null — Commit D's GetViewer call
     * backfills both from the token.
     */
    private fun handleAuthRedirect(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        val expectedUri    = BuildConfig.ANILIST_REDIRECT_URI
        val expectedScheme = expectedUri.substringBefore("://")
        val expectedHost   = expectedUri.substringAfter("://").substringBefore("/")
        if (data.scheme != expectedScheme || data.host != expectedHost) return
        val token = parseAccessToken(data) ?: return
        val app = applicationContext as AnimeApp
        ioScope.launch {
            // Backfill viewer info via GetViewer. May fail (bad token, network
            // drop) — fall back to placeholder userId=0 so the token still
            // saves either way. Profile header will then populate on the next
            // collectAsState when this completes.
            val viewer = app.anilistClient.getViewer(token)
            val userId   = viewer?.id ?: 0
            val userName = viewer?.name
            app.tokenStore.saveToken(token, userId = userId, userName = userName)
        }
    }

    private fun parseAccessToken(uri: Uri): String? {
        val fragment = uri.fragment ?: return null
        return fragment
            .split('&')
            .mapNotNull { pair ->
                val eq = pair.indexOf('=')
                if (eq <= 0) null
                else pair.substring(0, eq) to Uri.decode(pair.substring(eq + 1))
            }
            .firstOrNull { it.first == "access_token" }
            ?.second
    }
}
