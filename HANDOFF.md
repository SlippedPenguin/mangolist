# MangoList — Project Handoff

> **Target:** AniHyou-parity Android anime tracker app  
> **Repo:** https://github.com/SlippedPenguin/mangolist  
> **Latest release:** [v0.7.4](https://github.com/SlippedPenguin/mangolist/releases/tag/v0.7.4)  
> **Client ID:** 46025  
> **Redirect URI:** `com.slippedpenguin.mangolist://callback`

---

## Project layout

```
mangolist/
├── app/src/main/
│   ├── java/com/slippedpenguin/mangolist/
│   │   ├── AnimeApp.kt              # Application class (Room DB, TokenStore, AniListClient singletons)
│   │   ├── MainActivity.kt          # Single-activity host, OAuth redirect handler, auto-sync on login
│   │   ├── data/
│   │   │   ├── AniListClient.kt     # GraphQL/API wrapper (search, getViewer, syncUserList, saveEntry, etc.)
│   │   │   ├── TokenStore.kt        # DataStore-backed OAuth token + userId + userName persistence
│   │   │   ├── EloEngine.kt         # Tierlist ELO calculation
│   │   │   └── local/
│   │   │       ├── AnimeEntry.kt    # Room entity (all animanga state)
│   │   │       └── AnimeDao.kt      # Room DAO (upsertAll, getAll, observeAll, etc.)
│   │   └── ui/
│   │       ├── Navigation.kt        # NavHost + bottom nav (Search, Tierlist, Profile, Airing)
│   │       ├── screens/
│   │       │   ├── ProfileScreen.kt # Stats, login/sync UI
│   │       │   ├── DetailScreen.kt  # Rich anime detail view
│   │       │   ├── SearchScreen.kt  # Search + results
│   │       │   ├── TierlistScreen.kt
│   │       │   └── AiringScreen.kt  # Weekly airing schedule
│   │       ├── components/          # Shared UI widgets
│   │       └── theme/               # MangoTheme, colors, typography
│   └── graphql/                     # .graphql queries + fragments (Apollo codegen)
│       └── com/slippedpenguin/mangolist/queries.graphql
├── .github/workflows/release.yml    # CI: builds APK on tag push, attaches to GitHub Release
├── build.gradle.kts                 # Root build config
├── app/build.gradle.kts             # App build config (AGP 8.7.3, Apollo, Room, etc.)
├── gradle/                          # No gradlew committed; CI uses setup-gradle@v4
└── local.properties                 # AniList client ID/secret/redirect (gitignored)
```

---

## What works now (as of v0.7.4)

### OAuth Login
- Authorization code grant flow via Chrome Custom Tabs
- Token exchange at `https://anilist.co/api/v2/oauth/token` (JSON body, client_id as int)
- Redirect URI: `com.slippedpenguin.mangolist://callback` (scheme+host format)
- Token persisted in DataStore (`token_prefs`)
- `getViewer()` fetches and stores userId + userName

### List Sync (one-way: AniList → local)
- **Auto-sync on login:** After OAuth succeeds, `MainActivity` calls `syncUserList(token, userId)` and upserts into Room
- **Manual sync:** "Sync now" button on ProfileScreen
- Merges with existing entries, preserving local `tier` and `elo`
- Filters out custom AniList lists
- Maps AniList statuses: `CURRENT→watching, PLANNING→plan, COMPLETED→completed, etc.`
- Maps scores: AniList 0-100 → local 0-100
- Uses hand-rolled `HttpURLConnection` POST (not Apollo) for the sync query

### Search
- `AniListClient.search(query)` uses Apollo `SearchAnimeQuery`
- Results displayed in SearchScreen

### Detail View
- `AniListClient.getMediaDetails(id)` uses Apollo `GetMediaDetailsQuery`
- Shows banner, synopsis, studios, characters, relations

### Profile Stats
- Local stats computed from Room entries (episodes watched, days watched, mean scores, status/tier/genre/format/year breakdowns)

### Airing Schedule
- `AniListClient.getAiringSchedule()` fetches next 7 days of airing anime

### CI/CD
- Push a `v*` tag → GitHub Actions builds release APK → uploads to GitHub Release
- Uses Gradle 8.11.1 via `gradle/actions/setup-gradle@v4` (no gradlew committed)

---

## Bugs fixed this session

| Version | Issue | Root Cause | Fix |
|---------|-------|-----------|-----|
| v0.6.8 | 404 "API route not found" after login | Scheme-only redirect URI treated as relative path by browser | Changed to `com.slippedpenguin.mangolist://callback` |
| v0.7.1 | "Sync failed" with no details | `syncUserList` returned null silently | Added `SyncResult` return type with error messages |
| v0.7.2 | HTTP 400 on sync | Raw string `\$userId` produced literal `\` instead of `$` | Changed to `${'$'}userId` (safe `$` escape in triple-quoted strings) |
| v0.7.4 | "Unexpected JSON off or on" crash | `.jsonPrimitive`/`.jsonObject`/`.jsonArray` extensions throw on `JsonNull` | Replaced ALL with safe casts (`as? JsonPrimitive`, `as? JsonObject`, `as? JsonArray`) |

### Critical Kotlin knowledge for this project

**In Kotlin raw/triple-quoted strings (`"""`), `\$` does NOT escape — it produces a literal backslash.** Use `${'$'}` to emit a literal `$` in triple-quoted strings. In regular strings with `+` concatenation, `\$` works correctly.

**`JsonNull` is NOT Kotlin `null`.** When traversing `JsonElement` trees manually, always use safe casts (`as? JsonPrimitive`, `as? JsonObject`, `as? JsonArray`). The extension properties `.jsonPrimitive`, `.jsonObject`, `.jsonArray` throw on mismatch.

---

## What's missing (next priorities)

### High priority — AniHyou parity features

1. **Two-way sync (push to AniList):**
   - `saveEntry()` in `AniListClient.kt` already exists and works (hand-rolled POST to `SaveMediaListEntry` mutation)
   - But it's **not wired to any UI action** — DetailScreen's sync button calls it but the result is unused
   - Need: when user changes status, episode progress, or score in the app → call `saveEntry()` → update local DB with the returned `listEntryId`
   - Need: mark entry as dirty when user edits locally, push on save

2. **Anime status management on DetailScreen:**
   - Dropdown/picker for status (Plan to Watch, Watching, Completed, Dropped, Paused, Repeating)
   - Increment/decrement episode counter
   - Score slider (0-10 stars)
   - Notes field
   - "Finish" action: sets `currentEp = episodes`, `status = completed`, pushes to AniList

3. **Tierlist UI:**
   - Current `TierlistScreen.kt` exists but needs fleshing out
   - Drag-and-drop between tiers (S/A/B/C/D)
   - Visual tier rows with anime cards
   - Persist tier changes locally

4. **ProfileScreen polish:**
   - Show cached viewer avatar (data is available from `getViewer` but not displayed)
   - Show AniList stats (anime count, mean score, episodes watched) from the viewer response
   - Sign-out / clear-token button

### Medium priority

5. **Favorites / collection view**
6. **Offline mode** (show cached data when no network)
7. **Pull-to-refresh** on all list screens

### Low priority

8. **Push notifications** for airing episodes
9. **Manga support** (currently ANIME-only)
10. **Play Store release** (needs release keystore, not debug)

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
# https://github.com/SlippedPenguin/mangolist/releases/tag/vX.Y.Z
```

**Important:** The CI injects `anilist.client.secret` from GitHub Secrets. The local `local.properties` has a **different** client secret for the user's AniList dev app. Both APKs (local debug build and CI release build) work because they use different AniList client registrations.

---

## Known gotchas

- **No local gradlew script.** Builds only work in CI (via `setup-gradle@v4`) or in Android Studio (which generates the wrapper). Don't try `./gradlew` from CLI.
- **Apollo codegen is fragile for mutations.** `SaveMediaListEntry` uses hand-rolled JSON POST; only queries (`search`, `getViewer`, `getMediaDetails`) use Apollo. The `syncUserList` query also uses hand-rolled POST to avoid fragment-spread codegen issues.
- **`exchangeCodeForToken` sends `client_id` as Int**, not String. AniList's Laravel Passport rejects the string form.
- **User must update AniList Developer settings** to match `com.slippedpenguin.mangolist://callback` — if they change it back to the scheme-only form, login will break again.
- **`AnimeEntry.preserveLocalFields(existing)`** keeps tier/elo during sync — always use this when upserting synced data.
