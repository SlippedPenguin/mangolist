package com.slippedpenguin.mangolist

import android.app.Application
import com.slippedpenguin.mangolist.data.TokenStore
import com.slippedpenguin.mangolist.data.local.AnimeDatabase

/*
 * Application class — exposed as `applicationContext as AnimeApp` from any
 * Composable. Lazy-initialised singletons are perfect for v1's tiny data
 * surface (single Room DB + small DataStore). Migrations to a DI graph
 * (Hilt/Koin) live in v1.x if we add more dependencies.
 */
class AnimeApp : Application() {

    val database:   AnimeDatabase by lazy { AnimeDatabase.getInstance(this) }
    val tokenStore: TokenStore    by lazy { TokenStore(this) }

    override fun onCreate() {
        super.onCreate()
    }
}
