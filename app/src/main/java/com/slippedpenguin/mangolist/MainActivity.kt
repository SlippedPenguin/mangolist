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
 * v0.6.2: Switched from implicit OAuth to authorization code grant.
 * AniList redirects back with ?code=... instead of #access_token=...
 * The code is exchanged for an access_token via POST to /api/v2/oauth/token.
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
     * v0.6.2: Parses ?code= from the redirect URI, then exchanges it for
     * an access_token via AniListClient.exchangeCodeForToken(). Falls back
     * to the old fragment parser in case the user's AniList client is
     * somehow still using implicit grant.
     */
    private fun handleAuthRedirect(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        val expectedUri    = BuildConfig.ANILIST_REDIRECT_URI
        val expectedScheme = expectedUri.substringBefore("://")
        // The registered redirect URI may be scheme-only (e.g.
        // "com.slippedpenguin.mangolist"). Only enforce a host check when the
        // configured URI actually has an authority section.
        val hasAuthority   = expectedUri.contains("://")
        val expectedHost   = if (hasAuthority) {
            expectedUri.substringAfter("://").substringBefore("/").substringBefore("?")
        } else {
            ""
        }
        if (data.scheme != expectedScheme) return
        if (hasAuthority && data.host != expectedHost) return

        val app = applicationContext as AnimeApp

        // Authorization code grant (v0.6.2): AniList sends ?code=…
        val code = data.getQueryParameter("code")
        if (!code.isNullOrBlank()) {
            ioScope.launch {
                val token = app.anilistClient.exchangeCodeForToken(code)
                if (token != null) {
                    val viewer = app.anilistClient.getViewer(token)
                    val userId   = viewer?.id ?: 0
                    val userName = viewer?.name
                    app.tokenStore.saveToken(token, userId = userId, userName = userName)
                }
            }
            return
        }

        // Fallback: implicit grant (response_type=token) — token in URL fragment.
        val token = parseAccessToken(data) ?: return
        ioScope.launch {
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
