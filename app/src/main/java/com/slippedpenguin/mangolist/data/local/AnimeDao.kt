package com.slippedpenguin.mangolist.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeDao {

    @Query("SELECT * FROM anime_entries ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AnimeEntry>>

    @Query("SELECT * FROM anime_entries ORDER BY updatedAt DESC")
    suspend fun getAll(): List<AnimeEntry>

    @Query("SELECT * FROM anime_entries WHERE anilistId = :id")
    fun observeById(id: Int): Flow<AnimeEntry?>

    @Query("SELECT * FROM anime_entries WHERE anilistId = :id LIMIT 1")
    suspend fun getById(id: Int): AnimeEntry?

    @Query("SELECT * FROM anime_entries WHERE tier = :tier ORDER BY elo DESC")
    fun observeByTier(tier: String): Flow<List<AnimeEntry>>

    @Query("SELECT * FROM anime_entries WHERE tier IS NULL ORDER BY updatedAt DESC")
    fun observeUnranked(): Flow<List<AnimeEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AnimeEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<AnimeEntry>)

    /**
     * Merge a successful AniList pull into the local database using the
     * v1.4.1-proven approach: one bulk read, one bulk write. Preserves
     * local tier/elo via preserveLocalFields on every incoming row.
     */
    @Transaction
    suspend fun mergePullResults(entries: List<AnimeEntry>) {
        val existing = getAll().associateBy { it.anilistId }
        val merged = entries.map { it.preserveLocalFields(existing[it.anilistId]) }
        if (merged.isNotEmpty()) upsertAll(merged)
    }

    /**
     * Mark a push clean only if the row still matches the snapshot sent to
     * AniList. A user can edit the same title while the request is in flight;
     * in that case this returns 0 and leaves the newer edit pending.
     */
    @Query("""
        UPDATE anime_entries
        SET listEntryId = :listEntryId,
            notes = :notes,
            syncedAt = :serverMillis,
            updatedAt = :serverMillis
        WHERE anilistId = :anilistId AND updatedAt = :snapshotUpdatedAt
    """)
    suspend fun markSyncedIfUnchanged(
        anilistId: Int,
        snapshotUpdatedAt: Long,
        listEntryId: Int,
        notes: String,
        serverMillis: Long,
    ): Int

    @Query("UPDATE anime_entries SET favourite = :favourite WHERE anilistId = :anilistId")
    suspend fun updateFavourite(anilistId: Int, favourite: Boolean): Int

    @Update
    suspend fun update(entry: AnimeEntry)

    @Query("DELETE FROM anime_entries WHERE anilistId = :id")
    suspend fun delete(id: Int)

    @Query("SELECT COUNT(*) FROM anime_entries")
    suspend fun count(): Int

    @Query("SELECT MAX(updatedAt) FROM anime_entries")
    suspend fun lastLocalEdit(): Long?

    /**
     * Entries that have local edits not yet pushed to AniList.
     * `syncedAt` is null for entries never synced, or older than the last
     * local edit for entries that were modified after their last push.
     */
    @Query("SELECT * FROM anime_entries WHERE syncedAt IS NULL OR updatedAt > syncedAt")
    suspend fun getPendingSyncs(): List<AnimeEntry>
}
