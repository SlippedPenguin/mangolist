package com.slippedpenguin.mangolist

import android.app.Application
import com.slippedpenguin.mangolist.data.AniListClient
import com.slippedpenguin.mangolist.data.TokenStore
import com.slippedpenguin.mangolist.data.local.AnimeDatabase
import com.slippedpenguin.mangolist.util.NetworkObserver
import com.slippedpenguin.mangolist.work.SyncWorker

/*
 * Application class — exposed as `applicationContext as AnimeApp` from any
 * Composable. Lazy-initialised singletons are perfect for v1's tiny data
 * surface (single Room DB + small DataStore). Migrations to a DI graph
 * (Hilt/Koin) live in v1.x if we add more dependencies.
 */
class AnimeApp : Application() {

    val database:       AnimeDatabase   by lazy { AnimeDatabase.getInstance(this) }
    val tokenStore:     TokenStore      by lazy { TokenStore(this) }
    val anilistClient:  AniListClient   by lazy { AniListClient(this, networkObserver) }
    val networkObserver: NetworkObserver by lazy { NetworkObserver(this) }

    override fun onCreate() {
        super.onCreate()
        // Drain any local edits that were made while offline or before the
        // app was killed. WorkManager deduplicates this with REPLACE.
        SyncWorker.enqueue(this)
    }
}
