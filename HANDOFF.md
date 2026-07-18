# MangoList — Project Handoff

> **Target:** AniHyou-parity Android anime tracker app  
> **Repo:** https://github.com/SlippedPenguin/mangolist  
> **Latest documented release:** [v0.8.5](https://github.com/SlippedPenguin/mangolist/releases/tag/v0.8.5)  
> **Working tree:** contains v0.8+ features not yet tagged as a release (see *v0.8+ deltas* below)  
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
│   │       ├── Navigation.kt        # NavHost + bottom nav (Watch, Add, Tiers, Airing, Profile)
│   │       ├── screens/
│   │       │   ├── ProfileScreen.kt # AniHyou-parity stats (local-computed) + login/sync UI
│   │       │   ├── DetailScreen.kt  # Hero/banner/metadata/synopsis/characters/relations + tracking card
│   │       │   ├── WatchlistScreen.kt # Flat list of all Room entries
│   │       │   ├── AddScreen.kt     # Debounced AniList search → +Add
│   │       │   ├── TiersScreen.kt   # Long-press → tier-picker sheet → VS-mode (3 Elo matches)
│   │       │   └── AiringScreen.kt  # 7-day schedule, grouped by day, countdown
│   │       ├── components/          # AnimeCard, EloBadge, StatusPill (shared)
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
- **Long-press any `AnimeCard`** → `ModalBottomSheet` with the five tier options (plus Cancel) and a per-row hint (median Elo if the target tier has ≥3 entries, else "vs-mode skipped · N in tier" or "(empty · instant rank)").
- Picking a target tier:
  - If the target tier has **≥ 3 opponents**, open `VsModeDialog`: three rounds of head-to-head tapping, each driven by `EloEngine.update`.
  - Each pick highlights the winner for ~900ms then advances.
  - Otherwise (target tier empty or < 3 entries) commit immediately with `elo = INITIAL_ELO`.
- After 3 matches, the root entry gets `tier = target, elo = finalElo`; opponent entries with mutated elos are written back in the same flow (sequential `dao.update` calls — not wrapped in a Room `@Transaction`).

### Airing Schedule
- `AniListClient.getAiringSchedule()` fetches next 7 days of airing anime (max 50 slots)
- `AiringScreen` groups by day (Today / Tomorrow / `EEE, MMM d`), shows a per-card countdown ("in 3d 12h" / "in 12h 30m" / "Airing now"), and ticks every 60s so the countdown stays fresh
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

### High priority

### Medium priority

1. **Favorites / collection view** — a tab or surface for marking favorites across the user's list. Currently no favorites concept.

### Low priority

3. **Push notifications** for airing episodes (FCM / WorkManager scheduling per `AiringSlot.airingAt`).
4. **Manga support** — queries are hardcoded `type: ANIME`. `AnimeEntry` already carries `MANGA`-compatible fields (`format`), so it's mostly a query + UI polish effort.
5. **Play Store release** — needs a release keystore (debug-signed APK is sideload-only today), Play Console listing, listing assets.

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
- **Sync button is gated on `tier != null`** — open question whether this is intentional (see *What's missing → #2*).
- **`saveEntry` does not push `notes`** — known bug, see *What's missing → #3*.
