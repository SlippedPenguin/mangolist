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
 *   @Database(entities = [...], version = 4, exportSchema = true)
 * plus `room.schemaLocation` in app/build.gradle.kts to dump to resources.
 *
 * v3 (favourites): adds the `favourite` column via an explicit Migration.
 * Destructive fallback is intentionally NOT used here because tier/elo are
 * local-only fields that AniList cannot recover for us; losing them on a
 * schema bump would silently wipe the user's tier rankings.
 *
 * v4 (manga): adds the `mediaType` column (NOT NULL DEFAULT 'ANIME') so
 * the single `anime_entries` table holds both anime and manga. Same
 * destructive-Migration caveat — tier/elo on every v3 row must be
 * preserved. Both MIGRATION_2_3 and MIGRATION_3_4 are explicit ALTER
 * statements; the `fallbackToDestructiveMigration()` is left wired up as
 * a last-resort safety net for *unknown* future migrations, not for
 * known-good ones.
 */
@Database(
    entities = [AnimeEntry::class],
    version = 5,
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

        /**
         * v3 → v4 — mediaType column (manga support).
         *
         * Adds `mediaType TEXT NOT NULL DEFAULT 'ANIME'`. Every existing
         * row gets the literal "ANIME" so the post-migration database is
         * indistinguishable from one written by v1.2 code on day one.
         * Tier/elo/notes/score for every existing row are untouched — the
         * `preserveLocalFields` merge in MainActivity still wins for any
         * row whose incoming payload disagrees with the local copy.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE anime_entries ADD COLUMN mediaType TEXT NOT NULL DEFAULT 'ANIME'"
                )
            }
        }

        /**
         * v4 → v5 — manga chapter/volume counts.
         *
         * Adds `chapters` and `volumes` nullable INTEGER columns. Existing
         * rows keep NULL for both; the app falls back to `episodes` when
         * these are absent, preserving v1.2 behaviour for already-synced
         * entries until the next pull refreshes them.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE anime_entries ADD COLUMN chapters INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE anime_entries ADD COLUMN volumes INTEGER"
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
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
