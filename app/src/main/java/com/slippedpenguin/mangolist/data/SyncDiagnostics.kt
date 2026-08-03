package com.slippedpenguin.mangolist.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * In-app sync diagnostics — the user-facing alternative to `adb logcat`.
 *
 * Every step of the sync path (network check, token load, HTTP POST,
 * GraphQL response, parse count, DB write) is appended to a ring buffer
 * that lives in memory AND is persisted to SharedPreferences, so the log
 * survives process death and screen rotation. ProfileScreen renders it
 * in a "Sync diagnostics" card with a copy-to-clipboard button, letting
 * a non-developer user paste the exact failure back for debugging.
 *
 * All methods are cheap and thread-safe (synchronized), so call sites in
 * coroutines and Room DAOs can log without coordination.
 */
object SyncDiagnostics {

    private const val PREFS_NAME = "sync_diagnostics"
    private const val KEY_LOG = "log"
    private const val KEY_SUMMARY = "summary"
    private const val MAX_LINES = 120

    @Volatile private var prefs: SharedPreferences? = null
    private val buffer = ArrayDeque<String>()
    @Volatile private var lastSummary: String = "No sync attempted yet."

    /** Call once from Application.onCreate before any sync can run. */
    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        lastSummary = p.getString(KEY_SUMMARY, lastSummary) ?: lastSummary
        p.getString(KEY_LOG, null)
            ?.split("\n")
            ?.takeLast(MAX_LINES)
            ?.forEach { buffer.addLast(it) }
    }

    /** Append one step to the log. [detail] is optional and added after an em dash. */
    @Synchronized
    fun log(step: String, detail: String? = null) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val line = if (detail.isNullOrBlank()) "[$time] $step" else "[$time] $step — $detail"
        buffer.addLast(line)
        while (buffer.size > MAX_LINES) buffer.removeFirst()
        persist()
    }

    /** Set the one-line outcome shown at the top of the diagnostics card. */
    @Synchronized
    fun setSummary(summary: String) {
        lastSummary = summary
        prefs?.edit()?.putString(KEY_SUMMARY, summary)?.apply()
    }

    /**
     * Set a combined summary for the usual ANIME + MANGA dual pull. The
     * per-type setSummary calls inside syncUserList run as each call
     * finishes, so without this the final summary would only reflect
     * whichever type completed last — misleading for a dual pull.
     */
    @Synchronized
    fun summarizePull(anime: SyncResult, manga: SyncResult) {
        val animeCount = anime.entries.orEmpty().size
        val mangaCount = manga.entries.orEmpty().size
        val total = animeCount + mangaCount
        val firstErr = anime.error ?: manga.error
        if (firstErr != null) {
            setSummary("Sync FAILED — ${firstErr.take(140)}")
        } else {
            setSummary("Sync OK — $total entries (anime $animeCount, manga $mangaCount)")
        }
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        lastSummary = "No sync attempted yet."
        prefs?.edit()?.clear()?.apply()
    }

    @Synchronized
    fun snapshot(): List<String> = buffer.toList()

    @Synchronized
    fun summary(): String = lastSummary

    /** Full log as a single newline-joined string (used by the Copy button). */
    @Synchronized
    fun text(): String = buffer.joinToString("\n")

    private fun persist() {
        prefs?.edit()?.putString(KEY_LOG, text())?.apply()
    }
}
