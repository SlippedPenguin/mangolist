package com.slippedpenguin.mangolist.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/*
 * Room database holder. Single instance per process — call sites use
 * `AnimeDatabase.getInstance(context)`.
 *
 * Schema export is OFF for v1 (no migrations yet). Turn it on before
 * shipping v1.0 so future schema changes get caught at PR time:
 *   @Database(entities = [...], version = 3, exportSchema = true)
 * plus `room.schemaLocation` in app/build.gradle.kts to dump to resources.
 *
 * v3 (favourites): adds the `favourite` column via an explicit Migration.
 * Destructive fallback is intentionally NOT used here because tier/elo are
 * local-only fields that AniList cannot recover for us; losing them on a
 * schema bump would silently wipe the user's tier rankings. MIGRATION_2_3
 * preserves every existing row's tier/elo/notes while adding the new
 * column with a default value of 0 (false). fallbackToDestructiveMigration
 * is left wired up as a last-resort safety net for *unknown* migrations,
 * not for known-good ones.
 */
@Database(
    entities = [AnimeEntry::class],
    version = 3,
    exportSchema = false,
)
abstract class AnimeDatabase : RoomDatabase() {

    abstract fun animeDao(): AnimeDao

    companion object {
        private const val DB_NAME = "mangolist.db"

        /**
         * v2 → v3 — favourites column.
         *
         * Adds `favourite INTEGER NOT NULL DEFAULT 0`. Room's primitive
         * Boolean type maps cleanly to SQLite INTEGER without a
         * TypeConverter, so no schema-side cast is needed. Existing rows
         * inherit favourite = false (no behavioural surprise — a "favourite"
         * is opt-in, never opt-out for already-stored entries).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE anime_entries ADD COLUMN favourite INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        @Volatile
        private var INSTANCE: AnimeDatabase? = null

        fun getInstance(context: Context): AnimeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnimeDatabase::class.java,
                    DB_NAME,
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
