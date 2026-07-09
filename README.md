# 🥭 MangoList

> A personal-use Android anime tracker with adaptive Elo tierlist ranking — built with Jetpack Compose + Material 3, assembled in the cloud, sideloaded onto your phone.

[![Latest Release](https://img.shields.io/github/v/release/SlippedPenguin/mangolist?style=flat-square)](https://github.com/SlippedPenguin/mangolist/releases/latest)
[![Build Status](https://img.shields.io/github/actions/workflow/status/SlippedPenguin/mangolist/release.yml?branch=main&style=flat-square)](https://github.com/SlippedPenguin/mangolist/actions/workflows/release.yml)
[![Android](https://img.shields.io/badge/Android-26%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-1.3.x-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

## Why

I track a lot of anime. Star ratings don't capture how I actually rank shows — that comes from head-to-head comparisons. **MangoList** is a personal-use AniHyou-style tracker with one novel feature: rank the shows you've watched through short head-to-head matches, with Elo scores driving your tierlist automatically. No spreadsheets, no stars-out-of-5 wars, no "is this a 7 or an 8?" debates.

## Features

| Feature | Status | Notes |
|---|---|---|
| Watchlist (local Room DB) | ✅ v0.1 | Offline-first; survives reinstall via Android Auto Backup |
| Adaptive Elo tierlist | ✅ Engine · ⏳ UI next | K-factor 32; tiers S/A/B/C/D; clamps 900–2100 |
| AniList PIN OAuth login | 🔜 v0.2 | No client_id required for personal use |
| Add anime via AniList search | 🔜 v0.2 | Typeahead against `graphql.anilist.co` |
| Bidirectional AniList sync | 🔜 v0.2 | Last-write-wins |
| Airing schedule | 🔜 v0.2 | 7-day rolling window |
| Profile stats | 🔜 v0.2 | Mean score, episodes watched, minutes |

## Tech stack

- **Native Android** — Kotlin 2.0.21 + Jetpack Compose (Material 3 1.3.x, Compose BOM 2024.12.01)
- **Persistence** — Room 2.6.1 (offline cache) + DataStore 1.1.1 (AniList access token)
- **Networking** — Apollo Kotlin 4.0.0 against the [AniList GraphQL v2](https://docs.anilist.co) endpoint
- **Image loading** — Coil 2.7.0
- **Navigation** — androidx.navigation.compose 2.8.5
- **CI** — GitHub Actions (`ubuntu-latest` + gradle/actions/setup-gradle@v4)

## Get the app

The APK is built by GitHub Actions on every tag push — free for public repos.

1. Open the [Releases page](https://github.com/SlippedPenguin/mangolist/releases/latest).
2. Download `app-release.apk` to your Android phone (Chrome, Drive, AirDroid, USB — anything works).
3. Tap the file → "Allow installs from this source" once → **Install**.
4. Launch **MangoList** from your app drawer.

> The APK is debug-signed for sideload only — not Play-Store-ready. Add a proper release keystore before any public distribution.

## Build it yourself

You don't need Android Studio. `git` + the `gh` CLI is enough.

```bash
git clone https://github.com/SlippedPenguin/mangolist.git
cd mangolist
git tag v0.X.Y
git push origin v0.X.Y
```

GitHub Actions will assemble a release APK in ~5 minutes and attach it to the release tag.

For local builds, install JDK 17 + Android SDK cmdline tools (~2 GB total), then:

```bash
gradle assembleDebug         # debug APK, local path: app/build/outputs/apk/debug/
```

The repo intentionally omits the Gradle wrapper jar/script — the workflow installs Gradle directly via `gradle/actions/setup-gradle@v4`. If you want offline wrapper support, run `gradle wrapper` once locally and commit the generated `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`.

## Project layout

```
mangolist/
├── .github/workflows/release.yml    ← cloud APK build on every tag push
├── gradle/libs.versions.toml         ← all dependency versions in one place (version catalog)
├── app/
│   ├── build.gradle.kts              ← module config (Compose + Apollo + Room + DataStore)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── graphql/                  ← AniList operations + auto-fetched schema
│       ├── java/com/slippedpenguin/mangolist/
│       │   ├── AnimeApp.kt           ← Application entry
│       │   ├── MainActivity.kt       ← Compose root
│       │   ├── data/                 ← Room DB, DataStore, Elo engine port
│       │   └── ui/                   ← Compose screens, components, navigation, theme
│       └── res/                      ← icons, themes, backup/data-extraction rules
└── settings.gradle.kts
```

## Architecture notes

- **State** — each screen reads from a single `Flow<T>` via `collectAsState()`. No ViewModels in v0.1, but the seam is in place to slot them in for v0.2.
- **Theming** — Material 3 + Compose tokens live in `ui/theme/`, ported from the prior HTML prototype (accent `#ff3366`, tier rainbow, `Bebas Neue` for tier letters).
- **Tierlist engine** in `data/EloEngine.kt` is a straight Kotlin port of the JS prototype's logic — same K-factor (32), same 900–2100 clamp, same 1500 starting Elo, same S→D median-proximity proposal.
- **AniList integration** is lazy: `ApolloClient` is only constructed when a user-visible action needs it, and the OAuth token is persisted in DataStore.

## Inspirations

- [AniHyou-android](https://github.com/axiel7/AniHyou-android) — base architecture, OAuth flow, screen design.
- [AniList](https://anilist.co) — the source data behind every anime entry you can search for.
- The web prototype this app is derived from: `anime-tracker/index.html` in the parent project.

## License

Personal-use project. Pick a license (MIT recommended) before publishing to anyone else.
