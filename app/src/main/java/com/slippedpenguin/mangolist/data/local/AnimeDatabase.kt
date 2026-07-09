package com.slippedpenguin.mangolist.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/*
 * Room database holder. Single instance per process — call sites use
 * `AnimeDatabase.getInstance(context)`.
 *
 * Schema export is OFF for v1 (no migrations yet). Turn it on before
 * shipping v1.0 so future schema changes get caught at PR time:
 *   @Database(entities = [...], version = 2, exportSchema = true)
 * plus `room.schemaLocation` in app/build.gradle.kts to dump to resources.
 */
@Database(
    entities = [AnimeEntry::class],
    version = 1,
    exportSchema = false,
)
abstract class AnimeDatabase : RoomDatabase() {

    abstract fun animeDao(): AnimeDao

    companion object {
        private const val DB_NAME = "mangolist.db"

        @Volatile
        private var INSTANCE: AnimeDatabase? = null

        fun getInstance(context: Context): AnimeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnimeDatabase::class.java,
                    DB_NAME,
                ).build().also { INSTANCE = it }
            }
        }
    }
}
