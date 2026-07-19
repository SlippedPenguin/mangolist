# MangoList — Project Handoff

> **Target:** AniHyou-parity Android anime tracker app  
> **Repo:** https://github.com/SlippedPenguin/mangolist  
> **Latest documented release:** [v1.2.1](https://github.com/SlippedPenguin/mangolist/releases/tag/v1.2.1)  
> **Working tree:** v1.2.1 bug-fix release (rating sync, HTTP 429, manga watchlist, explore debounce)  
> **Client ID:** 46025  
> **Redirect URI:** `com.slippedpenguin.mangolist://callback`

> **Doc-pinning note:** This doc was rewritten so it matches the actual build.
> The three items previously listed under *"What's missing (high priority)"*
> — two-way sync, Detail-screen status/episode/score/notes management, and
> the Tierlist UI — have shipped since v0.7.4. They're documented under
> *What works now* below, with a quick-reference delta table at the end.
> *What's missing* now lists the *real* open work as of this rewrite.

---

## Project layout

```
mangolist/
├── app/src/main/
│   ├── java/com/slippedpenguin/mangolist/
│   │   ├── AnimeApp.kt              # Application class (Room DB, TokenStore, AniListClient singletons)
│   │   ├── MainActivity.kt          # Single-activity host, OAuth redirect handler, auto-sync on login
│   │   ├── data/
│   │   │   ├── AniListClient.kt     # GraphQL/API wrapper (search, getViewer, syncUserList, saveEntry, getMediaDetails, getAiringSchedule, exchangeCodeForToken)
│   │   │   ├── TokenStore.kt        # DataStore-backed OAuth token + userId + userName persistence
│   │   │   ├── EloEngine.kt         # Tierlist ELO calculation (K=32, INITIAL_ELO=1500, clamp 900–2100)
│   │   │   └── local/
│   │   │       ├── AnimeEntry.kt    # Room entity (all animanga state + tier/elo/sync metadata)
│   │   │       ├── AnimeDao.kt      # Room DAO (observeAll / observeById / observeByTier / upsertAll / update)
│   │   │       └── AnimeDatabase.kt # Room holder (v2 schema, destructive migration)
│   │   └── ui/
│   │       ├── Navigation.kt        # NavHost + bottom nav (Watch, Explore, Tiers, Airing, Profile)
│   │       ├── screens/
│   │       │   ├── ProfileScreen.kt # AniHyou-parity stats (local-computed) + login/sync UI
│   │       │   ├── DetailScreen.kt  # Hero/banner/metadata/synopsis/characters/relations + tracking card
│   │       │   ├── WatchlistScreen.kt # Flat list of all Room entries
│   │       │   ├── ExploreScreen.kt  # Search bar + Popular/Trending/Coming Soon/Top Rated carousels, debounced search
│   │       │   ├── TiersScreen.kt   # Long-press → tier-picker sheet → VS-mode (3 Elo matches)
│   │       │   └── AiringScreen.kt  # 7-day schedule, "On my list"/"All airing" filter tabs, per-card progress badge
│   │       ├── components/          # AnimeCard (list row), AnimePosterCard (carousel poster), EloBadge, StatusPill (shared)
│   │       └── theme/               # MangoTheme (dark-only), tier/status color maps
│   │       └── theme/               # MangoTheme (dark-only), tier/status color maps
│   └── graphql/com/slippedpenguin/mangolist/queries.graphql  # Apollo queries + SaveMediaListEntry mutation
├── .github/workflows/release.yml    # CI: builds APK on tag push, attaches to GitHub Release
├── build.gradle.kts                 # Root build config
├── app/build.gradle.kts             # App build config (AGP 8.7.3, Apollo, Room, etc.)
├── gradle/                          # No gradlew committed; CI uses setup-gradle@v4
└── local.properties                 # AniList client ID/secret/redirect (gitignored)
```

---

## What works now

### OAuth Login
- Authorization code grant flow via Chrome Custom Tabs
- Token exchange at `https://anilist.co/api/v2/oauth/token` (JSON body, client_id as Int)
- Redirect URI: `com.slippedpenguin.mangolist://callback` (scheme+host format)
- Token persisted in DataStore (`token_prefs`)
- `getViewer()` fetches and stores userId + userName

### List Sync (one-way: AniList → local)
- **Auto-sync on login:** After OAuth succeeds, `MainActivity` calls `syncUserList(token, userId)` and upserts into Room
- **Manual sync:** "Sync now" button on ProfileScreen
- Merges with existing entries, preserving local `tier` and `elo` via `preserveLocalFields`
- Filters out custom AniList lists via `isCustomList` flag
- Maps AniList statuses: `CURRENT→watching, PLANNING→plan, COMPLETED→completed, DROPPED→dropped, PAUSED→paused, REPEATING→repeating`
- Maps scores: AniList `MediaList.score` (0–10 Float, 0.5-step increments) × 10 → local `personalScore` (0–100, display ÷10 with one decimal in the score pill)
- Uses hand-rolled `HttpURLConnection` POST (not Apollo) for the sync query
- Returns `SyncResult(entries, error)` so callers can surface the actual error message instead of a generic toast

### Search
- `AniListClient.search(query)` uses Apollo `SearchAnimeQuery`
- `AddScreen` debounces input 350ms before firing
- Results render as `SearchResultRow` (cover + title + year/eps + "+ Add" / "Open" button)
- Tapping `+ Add` upserts into Room only if the row is genuinely new (avoids clobbering existing tier/elo/notes via REPLACE-on-conflict)

### Detail View
- `AniListClient.getMediaDetails(id)` uses Apollo `GetMediaDetailsQuery`
- Shows: hero banner + cover overlay, title (English primary, romaji secondary), metadata pills (Status / Format / Season / Episodes / Duration / Score), genres chip strip, expandable synopsis (HTML stripped via `HtmlCompat`), studios, characters (top 15 with role labels), related anime (clickable, navigates to a new Detail)
- Mapping is done into a stable `MediaDetails` Kotlin class so future codegen migration doesn't ripple to the UI

### Detail Screen — Tracking (Status / Episode / Score / Notes / Tier)
- **`StatusPickerDialog`** — AlertDialog listing the six statuses. Tap a row to commit; the active status gets a "current" marker.
- **`EpisodeRow`** — `−`/`+` counters. Tap `+` past the cap and the entry auto-completes in the same write (`status = "completed"`), so the StatusPill flips immediately. If `−` later drops `currentEp` below the cap while the entry is *still* `status = "completed"` (regardless of whether the completion was set by auto-complete or manually via `StatusPickerDialog`), it auto-flips back to `status = "watching"` — avoids the "stuck completed at one fewer episode" case.
- **`ScorePickerDialog`** — 1–10 star grid; tapping a star sets `personalScore` to that step's value (step × 10), tapping the same active star clears the rating (`null`). Stars fill reactively based on `selected >= step × 10` (no animation). Also has an explicit "Clear rating" button at the bottom.
- **`NotesDialog`** — multi-line `OutlinedTextField`, eight visible lines, trims trailing whitespace on save.
- **`TierPickerDialog`** — same AlertDialog shape for S/A/B/C/D + an "Unranked" reset. Resetting tier resets Elo to `EloEngine.INITIAL_ELO = 1500`.
- **All five sub-edits are LOCAL writes only** (status, episode, score, notes, tier). They don't push to AniList until the user manually presses "Sync to AniList" (see *Two-way sync* below; tier is local-only by design, so it's never pushed). Auto-push on dirty edit is still an open work item.

### Two-way Sync (Push to AniList)
- `AniListClient.saveEntry(token, entry)` is fully wired to the **"Sync to AniList" button** on `DetailScreen.TrackingCard`
- Hand-rolled `HttpURLConnection` POST to the `SaveMediaListEntry` GraphQL mutation (Apollo codegen for mutations proved opaque at kotlinc; same workaround as `syncUserList`)
- Mappings:
  - status → AniList `MediaListStatus` enum (`watching` → `CURRENT`, `plan` → `PLANNING`, etc.)
  - `personalScore / 10.0` → score Float (0–10 scale)
  - `notes` → String (empty string clears notes on AniList)
  - `listEntryId` non-null → update; null → create new
- On success: returns `SaveResult(id, updatedAtSeconds, notes)`; caller writes back `listEntryId`, server `updatedAtSeconds` (×1000), and server `notes` into Room so the next round-trip hits the update path and notes round-trip cleanly
- On failure: surfaces an in-screen feedback chip (auto-dismisses after ~4s)
- **Auto-push:** `SyncWorker` (WorkManager) drains entries whose `updatedAt > syncedAt` (or `syncedAt IS NULL`) whenever the network is available. It is enqueued on app start and after every local edit in `DetailScreen`. Exponential backoff (10s base) on failure.
- The manual "Sync to AniList" button is no longer gated on `tier != null` — unranked entries can now sync progress/score/notes.

### Profile Screen
- Avatar loaded from cached `AnimeViewer.avatarMedium` / `avatarLarge` URL stored in `TokenStore`
- Greeting with `userName` and local list count
- **Local stats card:** Episodes watched, days watched (format-aware duration estimates), community mean score, personal mean score
- **AniList viewer stats card:** anime count, mean score, episodes watched, days watched (fetched live via `AniListClient.getViewer`)
- Status / tier / genre / format / year breakdowns (local)
- Sign-in CTA when not authenticated; "Sync now" and "Sign out" buttons when authenticated

### Tierlist UI
- `TiersScreen` is a vertical rail of five rows (S / A / B / C / D) plus an Unranked bucket. Each row sorted by Elo descending.
- `TierHeader` shows tier letter (tier-tinted), count badge, and live Elo range (`1850–2050` etc.) so the user has a quick sense of where their collection sits.
- **Long-press any `AnimeCard`** → `ModalBottomSheet` with the five tier options (plus Cancel / Unranked) and a per-row hint (`(empty · first in tier)` or `N in tier`).
- **v1.2 simplification:** picking any tier commits immediately with `elo = INITIAL_ELO`. The previous 3-round vs-mode ceremony (`VsModeDialog`) was removed because tier-list felt "kinda wack" — users just want to drop a card in a bucket, not horse-trade through three Elo matches. The `EloEngine.update` API still exists for tests and any future in-place rebalancing.
### Airing Schedule

- `AniListClient.getAiringSchedule()` fetches next 7 days of airing anime (max 50 slots)
- `AiringScreen` groups by day (Today / Tomorrow / `EEE, MMM d`), shows a per-card countdown ("in 3d 12h" / "in 12h 30m" / "Airing now"), and ticks every 60s so the countdown stays fresh
- Top-level tabs: **On my list** (filters slots by animeId present in local Room) and **All airing** (raw schedule). The `On my list` cards surface the user's progress (`Your: 3 / 12`) next to the episode number.
- Tap → `navController.navigate("detail/$animeId")`

### Offline Mode
- `NetworkObserver` exposes a reactive `isOnline: Flow<Boolean>` and a synchronous `isCurrentlyOnline()` check backed by `ConnectivityManager`.
- `AniListClient` short-circuits every network call when offline, returning predictable empty/null results instead of hanging on DNS timeouts.
- `DetailScreen` falls back to a `MediaDetails` built from the cached `AnimeEntry` when `getMediaDetails()` returns null, so locally saved anime remain viewable offline.
- `OfflineBanner` is shown on `DetailScreen`, `AddScreen`, `AiringScreen`, and `ProfileScreen` whenever the device loses connectivity.

### Pull-to-Refresh
- All four top-level surfaces (`WatchlistScreen`, `TiersScreen`, `AiringScreen`, `ProfileScreen`) now wrap their content in Material3 `PullToRefreshBox` (BOM 2024.12.01 / Material3 1.3.x).
- **Watchlist / Tiers** — refresh re-runs `AniListClient.syncUserList(token, userId)`, merges results through `AnimeEntry.preserveLocalFields`, and `upsertAll`s into Room; existing `Flow<List<AnimeEntry>>` keeps observers in sync.
- **Airing** — refresh re-fetches `AniListClient.getAiringSchedule()`; the 60s countdown tick continues to refresh `now` after the load.
- **Profile** — refresh re-fetches `getViewer()` *and* re-runs the user-list sync. `Profile`'s root `Column` was extended with `verticalScroll(rememberScrollState())` so the PTR indicator can intercept gestures on the previously-static layout.
- Refresh is a no-op when the user is signed out — the gesture still fires, but the request bails early.

### CI/CD
- Push a `v*` tag → GitHub Actions builds release APK → uploads to GitHub Release
- Uses Gradle 8.11.1 via `gradle/actions/setup-gradle@v4` (no gradlew committed)

---

## What's missing (next priorities)

> Priority labels reflect user-facing impact for *this* daily-use personal app, not effort:
> - **High** — gaps the user runs into every session, or that block a whole feature category.
> - **Polish** — visible-but-small quality items; ship whenever convenient.

### High (user-blocking gaps)

None remaining as of v1.2.0 — Manga support, Genre/Tag filters, Airing enrichment, and the polish backlog all shipped in v1.1–v1.2. Remaining gaps are pure polish.

### Polish (visible-but-small)

1. **Airing banner-image accent** — `AiringSlot.bannerImage` is now fetched and populated, but `AiringCard` currently renders the cover fallback only. A future polish pass can paint a 4dp-tall banner-accent bar above each card (using `slot.bannerImage` as a tinted brush) so the schedule row visually echoes the detail screen's hero gradient.
2. **In-app logout** — `ProfileScreen` still surfaces a Sign-out CTA but the `TokenStore.clear()` call needs an audit for stale `userId` flows. Tracked under "per-screen polish" since it doesn't surface a crash, only stale state.

---

## Bugs fixed (historical table — pre-v0.8)

| Version | Issue | Root Cause | Fix |
|---------|-------|-----------|-----|
| v0.6.8 | 404 "API route not found" after login | Scheme-only redirect URI treated as relative path by browser | Changed to `com.slippedpenguin.mangolist://callback` |
| v0.7.1 | "Sync failed" with no details | `syncUserList` returned null silently | Added `SyncResult` return type with error messages |
| v0.7.2 | HTTP 400 on sync | Raw string `\$userId` produced literal `\` instead of `$` | Changed to `${'$'}userId` (safe `$` escape in triple-quoted strings) |
| v0.7.4 | "Unexpected JSON off or on" crash | `.jsonPrimitive`/`.jsonObject`/`.jsonArray` extensions throw on `JsonNull` | Replaced ALL with safe casts (`as? JsonPrimitive`, `as? JsonObject`, `as? JsonArray`) |
| v0.8+ (working tree) | Notes not pushed to AniList | `notes` omitted from `saveEntry` mutation variables/query string | Added `notes` to `SaveMediaListEntry`; `saveEntry` now returns `SaveResult(id, updatedAt, notes)` and caller writes back server timestamp |

### Critical Kotlin knowledge for this project

**In Kotlin raw/triple-quoted strings (`"""`), `\$` does NOT escape — it produces a literal backslash.** Use `${'$'}` to emit a literal `$` in triple-quoted strings. In regular strings with `+` concatenation, `\$` works correctly.

**`JsonNull` is NOT Kotlin `null`.** When traversing `JsonElement` trees manually, always use safe casts (`as? JsonPrimitive`, `as? JsonObject`, `as? JsonArray`). The extension properties `.jsonPrimitive`, `.jsonObject`, `.jsonArray` throw on mismatch.

---

## v0.8+ deltas (since v0.7.4)

Quick reference for what's new since the last documented release.

| Area | v0.7.4 | v0.8+ |
|---|---|---|
| Two-way sync | `saveEntry` stub (Apollo codegen refused to compile) | Hand-rolled JSON POST, fully wired to `DetailScreen` "Sync to AniList" button |
| Detail tracking | Plain read-only detail | Status / Episode / Score / Notes / Tier dialogs, auto-complete on episode cap, auto-de-promote on undo, ScorePickerDialog star grid, NotesDialog text editor |
| Tierlist | Flat "tier + Elo" list | `TiersScreen.kt`: long-press → tier-picker sheet → `VsModeDialog` (3 head-to-head Elo matches, 900ms reveal, writes back to both root + opponents) |
| Tier reassignment | Manual set would clobber Elo inconsistently | Tier reset always sets `elo = INITIAL_ELO`; VS-mode rounds adjust elos in-place across root + opponents |
| Sync errors | "Sync failed" toast | `SyncResult.entries / error` → readable message (`HTTP 401`, `HTTP 500: ...`, parsed `GraphQL errors`) |
| JSON parsing | `.jsonPrimitive`/`jsonObject` throws on `JsonNull` | Safe casts throughout (already in v0.7.4, carried forward) |
| ProfileScreen | Placeholder avatar + local stats only | Cached avatar URL, live AniList viewer stats card, sign-out button |
| Auto-push | Manual "Sync to AniList" only | `SyncWorker` drains `updatedAt > syncedAt` entries automatically; Sync button no longer tier-gated |
| Pull-to-refresh | None (Watchlist/Tiers listed local Room data; Airing one-shot; Profile button only) | `PullToRefreshBox` on all 4 top-level screens; room observe-flows auto-refresh; Airing re-runs `getAiringSchedule`; Profile re-runs viewer fetch + list sync |
| Score scale | Hard-formatted as out-of-10 (★ 7.5) | `data/ScoreScale.kt` enum + `ScoreDisplay` object; `FilterChip` toggle on Profile (OUT_OF_10 ↔ OUT_OF_100); Detail/ScorePickerDialog + AnimeCard honor the choice |
| Tierlist readout | "S / Elo 1500" on every card | Tier letter + `"#3 of 8"` rank-within-tier; naked Elo replaced on cards, tier headers, vs-mode cards |
| Sync handlers | Hand-rolled POST with Dalvik UA + 0-timeout + missing `Accept` (Cloudflare 403) | `openPost(url, token?)` helper: 15 s connect / 30 s read timeout, `MangoList/<ver>` User-Agent, `Accept: application/json`, `Connection: close`; applied to syncUserList, saveEntry, exchangeCodeForToken, getAiringSchedule |
| SaveMediaListEntry arg schema | `score:Float` (user’s scoring-format-dependent; rejected for non-POINT_10_DECIMAL AniList accounts) | `scoreRaw:Int` (format-agnostic 0-100 raw value); plus 200-OK `errors[]` detection, non-200 diagnostic context, deleted-data fallback log |
| Top app bar | None (content rendered directly under edge-to-edge status bar) | `MangoNavRoot` Scaffold adds a `TopAppBar` on every bottom-nav route, titled after the current destination |
| Watchlist list filter | Flat dump of every anime in `updatedAt DESC` order, no filtering | `ScrollableTabRow` above the list: All / Watching / Completed / Planning / Dropped / Paused / Repeating, each tab shows its count |
| Per-card sync feedback | None (user couldn’t see if a local edit was queued) | `AnimeCard.showSyncPending = true` renders a small cloud-upload icon next to the title when `syncedAt == null \|\| updatedAt > syncedAt` |
| Per-card edit feedback | None | `AnimeCard.showRelativeTimestamp = true` adds an "Edited 2h ago" line via `android.text.format.DateUtils` |

## v1.0 deltas (since v0.9.0)

| Area | v0.9.0 | v1.0 (working tree) |
|---|---|---|
| `Add` → `Explore` | Bottom-bar `Add` tab was a single AniList search field with `+ Add` rows; route `'add'`, `Icons.Outlined.Add`. | Route renamed `'explore'`, label `Explore`, icon `Icons.Outlined.TravelExplore`. `AddScreen.kt` deleted; `ExploreScreen.kt` (~250 LOC) opens with a sticky search bar plus four AniList carousels stacked underneath: **Popular** (`POPULARITY_DESC`), **Trending** (`TRENDING_DESC`), **Coming Soon** (`NOT_YET_RELEASED` + `START_DATE_DESC`), **Top Rated** (`SCORE_DESC`). When the user types ≥ 2 chars, the carousels hide and the screen swaps to a vertical list of search hits — same `+ Add` flow as before. |
| `AnimePosterCard` component | n/a — `AnimeCard` was the only card component. | New file `ui/components/AnimePosterCard.kt`. 140dp poster block + title beneath (2 lines) + year/format row; average-score badge in top-right renders the AniList raw 0–100 score (no emojis, per v0.9 UI brief). Tap routes to `DetailScreen`. Distinct from `AnimeCard` because carousels need vertical-stacked posters, not the 56×80 cover-thumbnail row that the Watchlist / Tiers use. |
| Carousel fetch concurrency | n/a (single search only). | Four `AniListClient.getXxx()` calls run in parallel inside `ExploreScreen`, via `coroutineScope { awaitAll(async {...}, async {...}, async {...}, async {...}) }`. Sequential fetch is ~800 ms for 4 queries; parallel lands at ~300 ms — the dominant cost is socket RTT to AniList, not per-query work. |
| `AniListClient` Apollo surface | `SearchAnime`, `GetMediaDetails`, `GetViewer`, `GetMediaListCollection`, `SaveMediaListEntry`, `ToggleFavourite`. | Adds four new queries: `GetPopularAnime`, `GetTrendingAnime`, `GetUpcomingAnime`, `GetTopRatedAnime`. All bind `Page.media { ...AnimeCardFields }` for type-safety parity with `search()`. The `Page → AnimeEntry` mapping is centralised in a private `buildDiscoverEntry(...)` helper so each new query is a ~25-line wrapper rather than a ~50-line copy-paste. |
| `AiringScreen` filter | One screen, all-airing slots grouped by day. | Top-level `TabRow` switches between **On my list** and **All airing**. The `On my list` filter observes Room's `observeAll()` flow and intersects `slot.animeId` with the user's local `anilistId` set, so it stays in sync without manual refresh. Card progress badge added: when a slot's anime is in the local list, the card shows e.g. `Your: 3 / 12` next to `Ep 5`, so the user can tell at-a-glance which anime are due for a `+` tap. |
| Airing offline behaviour | Falls back to a v0.5-style placeholder on fetch error; `OfflineBanner` shown. | Same fallback text but rewritten per-tab ("Nothing on your list is airing this week" vs. "No airing schedule available right now") so users can tell at-a-glance whether the empty state is a local-tracked-list issue or a network issue. |

**Code anchors**:
- `ui/screens/ExploreScreen.kt` — new file, full screen impl.
- `ui/components/AnimePosterCard.kt` — new component.
- `ui/screens/AiringScreen.kt` — `AiringMode` enum + `TabRow` + `progressByAnimeId` lookup map.
- `data/AniListClient.kt` — `buildDiscoverEntry` helper + `getPopular` / `getTrending` / `getUpcoming` / `getTopRated`.
- `graphql/com/slippedpenguin/mangolist/queries.graphql` — four new Apollo operations, all bound to `...AnimeCardFields`.

---

## v1.1 deltas (since v1.0)

| Area | v1.0 | v1.1 |
|---|---|---|
| Explore genre filter | Carousels only (Popular / Trending / Coming Soon / Top Rated) plus the search bar | New horizontal `FilterChip` strip below the search bar: an "All" chip plus 18 AniList genre strings (Action, Adventure, Comedy, Drama, Ecchi, Fantasy, Horror, Mahou Shoujo, Mecha, Music, Mystery, Psychological, Romance, Sci-Fi, Slice of Life, Sports, Supernatural, Thriller). Tapping a chip swaps the four carousels for an adaptive grid of `AnimePosterCard` tiles for that genre, fetched via AniList's `Page.media(genre_in: [$genre], sort: POPULARITY_DESC)`. Tap "All" or the active chip again to restore the carousels. The chip strip dims to 40% alpha while the search bar owns the screen, so chip taps during a search are visibly inert. |
| Mode precedence | One mode at a time: search OR carousels | Three modes in clear precedence order: search bar (≥ 2 chars) > selected genre chip > no selection (carousels). The chip strip stays visible in all three modes for fast pivoting. |
| Pull-to-refresh while genre-active | Only refreshed the four carousels; the spinner dismissed before fetches finished in some scenarios | Also re-fetches the currently selected genre alongside the carousels; the spinner is held inside a `try { ... } finally { isRefreshing = false }` on the launched job so the indicator stays until both calls resolve. |
| `AniListClient` Apollo surface | `SearchAnime`, four carousel queries, `GetMediaDetails`, `GetViewer`, `GetMediaListCollection`, `SaveMediaListEntry`, `ToggleFavourite`, `GetAiringSchedule` | Adds `GetAnimeByGenreQuery($genre, $perPage)` bound to `...AnimeCardFields`. `getByGenre(genre, perPage = 25)` reuses the same `buildDiscoverEntry` mapper as the carousel queries; canonicalises the genre to Title Case; short-circuits on blank strings; wrapped in `withNetwork(emptyList()) { withContext(Dispatchers.IO) { ... } }` matching the v1.0.4 patterns. |

## v1.2 deltas (since v1.1)

| Area | v1.1 | v1.2 |
|---|---|---|
| Manga support | Every list-style Apollo query hardcoded `type: ANIME`; no UI surface for manga. | Every list-style query (search, four carousels, genre grid, media details) now takes `$type: MediaType = ANIME`. New `GetMangaReleases` query for the manga carousel. Room schema bumped v3→v4 with `MIGRATION_3_4` adding `mediaType TEXT NOT NULL DEFAULT 'ANIME'`. `AnimeEntry.mediaType: String = "ANIME"` discriminator. `parseMediaListEntry` switches to `chapters`/`volumes` for manga; `currentEp` retains the dominant "chapters" convention for progress. `MainActivity.handleAuthRedirect` and `TiersScreen` pull both ANIME and MANGA on sync and merge into one Room table (anilistId is globally unique across types so no PK clash). |
| Explore media-type toggle | Always anime. | `SingleChoiceSegmentedButtonRow` above the search bar: Anime / Manga. Toggling re-fetches all four carousels with the chosen `type`. Manga mode also surfaces a "Releasing manga" carousel (`getMangaReleases`, hits `Page.media(type: MANGA, status: RELEASING, sort: [START_DATE_DESC])`). Search placeholder adapts ("Search AniList manga…"). Search-result rows show "X ch" instead of "X ep" for manga. |
| Watchlist media-type filter | Anime + manga rows mixed under one status tab. | `All / Anime / Manga` chip strip above the status tabs. Filter is AND-ed with the existing status filter so the same tab counts narrow further by media type. |
| Detail-screen manga-aware metadata | Always "Episodes X". | `MetadataFlowRow` reads `details.mediaType` + `details.format`. Anime → "Episodes"; MANGA → "Chapters"; NOVEL → "Volumes". `chapters`/`volumes` fall back to `episodes` when AniList hasn't filled them in. |
| Tierlist (vs-mode removed) | Long-press → 3-round `VsModeDialog` head-to-head driven by `EloEngine.update`. | Long-press → `ModalBottomSheet` with five `TextButton`s (S/A/B/C/D + Unranked + Cancel). Picking any tier commits immediately with `elo = INITIAL_ELO`. The user flagged tier-list as "kinda wack" — the 3-round ceremony was the wack part. `EloEngine.update` is still wired (used by tests; any future in-place rebalancing can reuse it). |
| Airing enrichment | Per-card only `cover + title + Ep X + countdown`. | `getNextAiringFor` + `getAiringSchedule` hand-rolled JSON queries now fetch `averageScore status bannerImage` on the media node. `AiringSlot` data class gains `averageScore / anilistStatus / bannerImage`. `AiringCard` renders a tinted avg-score badge next to `Ep N` when `slot.averageScore != null && > 0`. Banner-image accent is fetched but not yet painted (deferred polish — see *What's missing*). |
| Score scale default | `OUT_OF_10` | `OUT_OF_100`. The DataStore-persisted scale tag still wins for returning users; only fresh installs and never-toggled users inherit the new default. |
| Schema migration | Room v3 (favourites). | Room v4. `MIGRATION_3_4` runs `ALTER TABLE anime_entries ADD COLUMN mediaType TEXT NOT NULL DEFAULT 'ANIME'`. Existing rows get the ANIME literal so the post-migration DB is indistinguishable from one written by v1.2 code on day one. Tier / Elo / favourites / notes / score on every existing row are preserved. `fallbackToDestructiveMigration()` left wired as a safety net for unknown future versions only. |

**Code anchors for v1.2:**
- `data/AniListClient.kt` — `toAniListType` helper, `type` parameter on every carousel/search/details method, new `getMangaReleases`, extended `AiringSlot`.
- `data/local/AnimeDatabase.kt` — `MIGRATION_3_4`.
- `data/local/AnimeEntry.kt` — `mediaType` field + `preserveLocalFields` preservation.
- `data/ScoreScale.kt` — `Default = OUT_OF_100`.
- `ui/screens/ExploreScreen.kt` — `MediaTypeSegmentedControl` + manga-only "Releasing manga" carousel.
- `ui/screens/DetailScreen.kt` — `MetadataFlowRow` media-type-aware unit label.
- `ui/screens/WatchlistScreen.kt` — media-type chip strip above status tabs.
- `ui/screens/TiersScreen.kt` — vs-mode removed; `ModalBottomSheet` with 5 tier buttons commits at INITIAL_ELO.
- `ui/screens/AiringScreen.kt` — `AiringCard` avg-score badge.
- `MainActivity.kt` — auth-redirect handler pulls both ANIME and MANGA lists.
- `graphql/com/slippedpenguin/mangolist/queries.graphql` — `$type: MediaType = ANIME` on every list-style query; new `GetMangaReleases`.

---

## v0.9+ deltas (since v0.8.5)

| Area | v0.8.5 | v0.9.0 |
|---|---|---|
| Favorites | No concept (lists held by status + tier / Elo only) | New `AnimeEntry.favourite: Boolean = false` field; Room v2→v3 migration that adds the column with default false (preserves tier/elo, which are local-only); round-trips with AniList's `MediaList.favourite` field via the hand-rolled `syncUserList` (read on pull sync) and `saveEntry` (write on push) |
| Detail-screen favorite toggle | None | New `IconButton` in `DetailScreen.TrackingCard`'s status row — filled yellow star when favorited, outlined otherwise. Toggling writes the entry + enqueues `SyncWorker.enqueue(app)` so the change pushes to AniList in the background |
| Watchlist’s “Favorites” tab | None | Eighth tab in `WatchlistScreen`'s `ScrollableTabRow`, filtering by `entry.favourite`; the `FAVORITES_KEY = "__favorites__"` sentinel keeps it orthogonal to the six status filters so the filter handler can branch cleanly |
| Per-card favorite indicator | None | `AnimeCard.showFavorite = true` renders a small filled `Star` next to the title (ordering: title → favourite star → cloud-upload icon when both apply) |
| SaveMediaListEntry response surface | `{id updatedAt notes}` | `{id updatedAt notes favourite}`; `SaveResult` data class gains `favourite: Boolean?` so `SyncWorker` can write back what AniList stored |

**Code anchors** for navigating the v0.8+ work:
- `AniListClient.saveEntry` — the hand-rolled POST in `data/AniListClient.kt` (around the "Push a single AnimeEntry edit back to AniList" doc-comment).
- `TiersScreen.startVsMode` / `pickInMatch` / `cancelVsMode` — long-press → sheet → VS-mode flow.
- `DetailScreen.TrackingCard` and its four sub-dialogs (`StatusPickerDialog`, `TierPickerDialog`, `NotesDialog`, `ScorePickerDialog`) — all in `ui/screens/DetailScreen.kt`.
- `EpisodeRow` `onPlus` / `onMinus` — the auto-complete and auto-de-promote logic.
- `SyncedResult` and the merge logic in `MainActivity.handleAuthRedirect` (using `entry.preserveLocalFields(existing)` to keep tier/elo across pull syncs).

---

## Release workflow

```bash
# 1. Make changes in mangolist/
# 2. Commit
git add -A
git commit -m "feat/fix(vX.Y.Z): description"
git push origin main

# 3. Tag (triggers CI build)
git tag vX.Y.Z
git push origin vX.Y.Z

# 4. Monitor CI
gh run list --repo SlippedPenguin/mangolist --workflow=release.yml --limit 1 --json status,conclusion,url

# 5. Once green, release is at:
# https://github.com/Slippedpenguin/mangolist/releases/tag/vX.Y.Z
```

**Important:** The CI injects `anilist.client.secret` from GitHub Secrets. The local `local.properties` has a **different** client secret for the user's AniList dev app. Both APKs (local debug build and CI release build) work because they use different AniList client registrations.

---

## Known gotchas

- **No local gradlew script.** Builds only work in CI (via `setup-gradle@v4`) or in Android Studio (which generates the wrapper). Don't try `./gradlew` from CLI.
- **Apollo codegen is fragile for mutations.** `SaveMediaListEntry` uses hand-rolled JSON POST; only queries (`search`, `getViewer`, `getMediaDetails`) use Apollo. The `syncUserList` and `saveEntry` paths also use hand-rolled POST to avoid fragment-spread codegen issues.
- **`exchangeCodeForToken` sends `client_id` as Int**, not String. AniList's Laravel Passport rejects the string form.
- **User must update AniList Developer settings** to match `com.slippedpenguin.mangolist://callback` — if they change it back to the scheme-only form, login will break again.
- **`AnimeEntry.preserveLocalFields(existing)`** keeps tier/elo during sync — always use this when upserting synced data.
