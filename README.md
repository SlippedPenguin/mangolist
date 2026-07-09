# MangoList 🍋

A personal Android anime tracker modeled after [AniHyou-android](https://github.com/axiel7/AniHyou-android), with one unique addition: **adaptive Elo tierlist ranking** (anime in your watchlist are ranked by head-to-head votes instead of a fixed star rating).

Built as plain text in any editor → built into an APK by GitHub Actions in the cloud → sideloaded onto your phone. No Android Studio, no Play Store, no money.

## Features

| Feature | Status | Notes |
|---|---|---|
| Watchlist | ✅ | Stored in local Room database |
| Search & add anime | ⏳ v1.0 | Wired to AniList GraphQL |
| Tierlist ranking (Elo) | ✅ Engine / ⏳ UI | Core logic ported from the JS prototype |
| Bidirectional AniList sync | ⏳ v1.0 | PIN OAuth, last-write-wins |
| Airing schedule | ⏳ v1.0 | 7-day window |
| Profile stats | ⏳ v1.0 | Mean score, episodes watched, minutes |

## Project structure

```
mangolist/
├── .github/workflows/release.yml   ← cloud APK builds on tag push
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── graphql/                  ← AniList operations
│       ├── java/com/slippedpenguin/mangolist/
│       │   ├── AnimeApp.kt
│       │   ├── MainActivity.kt
│       │   ├── data/                 ← Room, DataStore, Elo port
│       │   └── ui/                   ← Compose screens, nav, theme
│       └── res/                      ← icons, themes, strings
├── build.gradle.kts                  ← root
├── gradle/libs.versions.toml         ← version catalog
└── settings.gradle.kts
```

## Cutting a release (getting the APK onto your phone)

1. Push your changes:
   ```bash
   git add .
   git commit -m "feat: something new"
   git push
   ```
2. Tag a version and push the tag:
   ```bash
   git tag v0.1.0
   git push --tags
   ```
3. Wait ~5 minutes. GitHub Actions builds the APK in the cloud (free for public repos).
4. Open the repo on GitHub → **Releases** → tap the new release → download `app-release.apk` to your phone.
5. On your phone: tap the `.apk` → allow "install from this source" once → install.

No money, no Play Store, no computer setup. Repeat for every release.

## Local development (optional)

You can build locally if you want faster feedback, but it's not required:

1. Install JDK 17 + Android SDK cmdline tools (~2 GB total).
2. Run `./gradlew assembleDebug` from the project root.

## License

Personal-use; pick a license before publishing to anyone else.
