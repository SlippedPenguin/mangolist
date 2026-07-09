package com.slippedpenguin.mangolist.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeDao {

    @Query("SELECT * FROM anime_entries ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AnimeEntry>>

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

    @Update
    suspend fun update(entry: AnimeEntry)

    @Query("DELETE FROM anime_entries WHERE anilistId = :id")
    suspend fun delete(id: Int)

    @Query("SELECT COUNT(*) FROM anime_entries")
    suspend fun count(): Int

    @Query("SELECT MAX(updatedAt) FROM anime_entries")
    suspend fun lastLocalEdit(): Long?
}
