package com.slippedpenguin.mangolist.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.slippedpenguin.mangolist.AnimeApp
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit

/*
 * Background worker that drains local edits to AniList.
 *
 * It pulls every entry whose `updatedAt` is newer than its `syncedAt`
 * (or has never been synced) and pushes each one via AniListClient.saveEntry.
 * On success the entry's `syncedAt`/`updatedAt` are written back with the
 * server timestamp. On failure it asks WorkManager to retry with exponential
 * backoff.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as AnimeApp
        val token = app.tokenStore.accessToken.firstOrNull()
        if (token.isNullOrBlank()) {
            return Result.failure()
        }

        val dao = app.database.animeDao()
        val pending = dao.getPendingSyncs()
        if (pending.isEmpty()) {
            return Result.success()
        }

        var anyFailed = false
        pending.forEach { entry ->
            try {
                val result = app.anilistClient.saveEntry(token, entry)
                if (result != null) {
                    val serverMillis = result.updatedAtSeconds?.let { it * 1000L } ?: System.currentTimeMillis()
                    // Only mark the row as synced if it hasn't been edited
                    // while the network call was in flight. Affected-rows == 0
                    // means a concurrent edit happened; leave it pending.
                    val affected = dao.markSyncedIfUnchanged(
                        anilistId = entry.anilistId,
                        listEntryId = result.id,
                        syncedAt = serverMillis,
                        expectedUpdatedAt = entry.updatedAt,
                    )
                    if (affected == 0) {
                        android.util.Log.w(
                            "SyncWorker",
                            "Concurrent edit detected for anilistId=${entry.anilistId}; leaving pending.",
                        )
                    }
                } else {
                    anyFailed = true
                }
            } catch (e: Exception) {
                android.util.Log.w("SyncWorker", "Failed to push anilistId=${entry.anilistId} title='${entry.title}'", e)
                anyFailed = true
            }
        }

        return if (anyFailed) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "mangolist_auto_sync"

        /**
         * Enqueue a one-time sync that only runs when the network is
         * available. Existing pending work is replaced so rapid edits don't
         * stack multiple workers.
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
