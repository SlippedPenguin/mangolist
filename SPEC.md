# Spec: MangoList v1.7.0 — Tierlist Overhaul + Daily-Use Hardening

> Mission: make MangoList the app you open after watching. Ranking becomes a
> drag, batch-ranking becomes one tap per judgment, and the daily flows are
> hardened. Approved intent (2026-09-13): outcome = frictionless tierlist +
> hardened daily flows; user = the owner, tracking anime + manga; success =
> rank a whole season without friction on the sideloaded APK; constraint =
> CI-only builds (no local Android toolchain); out of scope = new data
> sources, new accounts, tablet layouts, settings sprawl.

## Capability Map

| Module id | Responsibility | Depends on |
|---|---|---|
| ci-fastloop | Test-on-push GitHub Actions workflow so every change is verified in ~3 min without tagging | — |
| tier-dnd | Drag & drop tierlist: press-hold → drag card between tiers and within a tier; explicit manual ordering (`tierRank`) via Room v5 migration | ci-fastloop |
| rank-h2h | "Rank unranked" head-to-head flow: two cards side-by-side, tap the winner, Elo + tier assigned per judgment (revives `EloEngine.update`/`proposeTier`) | tier-dnd |
| harden | Logout-state audit, sync/merge unit tests, airing banner accent, fix updatedAt-bump inconsistency in tier writes | ci-fastloop |
| release | Docs updated (HANDOFF/README), tag v1.7.0, CI-built APK on Releases | all |

Build order: ci-fastloop → tier-dnd → rank-h2h → harden → release.
(harden is partially parallelizable with rank-h2h.)

## ASSUMPTIONS I'M MAKING (correct me or they stand)

1. **Long-press becomes the drag gesture.** The long-press → bottom-sheet →
   tap flow dies. "Unrank" moves to the card overflow / Detail tracking card.
   This is the friction the user called out.
2. **Manual drag order is the authority** within a tier once you've dragged;
   never-dragged entries keep the v1.5.7 default (personalScore DESC, elo DESC).
3. **H2H targets unranked entries** (and unscored ones benefit most — elo is
   their only order signal). It does not fight score-based ordering of already
   scored entries; it assigns tier + elo, not personalScore.
4. **Room schema v4 → v5**: add nullable `tierRank INTEGER` to
   `anime_entries` (explicit user-approved migration — see Open Questions).
5. **No new dependencies.** Drag & drop is hand-rolled in one file; the pure
   list-model logic is unit-testable without a device.
6. **Tier/elo/tierRank writes never bump `updatedAt`** (they are local-only;
   bumping flags no-op sync pushes). The v1.5.1 auto-rank path already does
   this correctly; the current sheet path does not (fixed in harden).
7. Release ships as **v1.7.0**.

## Objective

- **tier-dnd:** Press-hold any card in Tiers, drag it into any tier row (or
  within its tier), release — it lands there with a springy settle, order
  persists across restart and sync.
- **rank-h2h:** A "Rank unranked" entry point shows two titles side-by-side;
  tap the one you liked more; next pair appears; each judged title lands in a
  proposed tier with a real Elo. Pool exhausted (or Done) ends the session.
- **ci-fastloop:** Every push to `main` runs the unit-test suite on GitHub
  Actions; the check is green before a tag is cut.
- **harden:** Sign-out leaves no stale auth state; the pull/push merge rules
  that caused 5 historical bugs are pinned by unit tests; airing cards get
  their banner accent; tier writes stop flagging phantom syncs.

## Tech Stack

Unchanged: Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01) + Material 3,
Room 2.6.1, DataStore, Apollo 4.0.0, Coil, WorkManager, JUnit 4 +
kotlinx-coroutines-test. No new libraries.

## Commands

No local Android toolchain exists on this machine (Java 8, no Gradle) —
CI is the build and test loop:

```
Fast test loop:   git push origin main   → .github/workflows/test.yml (testDebugUnitTest, ~3 min)
Release build:    git tag v1.7.0 && git push origin v1.7.0 → release.yml (~5 min)
Watch CI:         gh run list --repo SlippedPenguin/mangolist --limit 3
                  gh run watch <run-id>
Grab APK:         gh release download v1.7.0 --repo SlippedPenguin/mangolist
Kotlin syntax:    kotlinc unavailable locally — CI is the gate; keep diffs surgical
```

## Project Structure

```
app/src/main/java/com/slippedpenguin/mangolist/
├── data/
│   ├── EloEngine.kt                  # existing — h2h uses update/pickOpponent/proposeTier
│   └── local/
│       ├── AnimeEntry.kt             # + tierRank: Int? (Room v5)
│       ├── AnimeDao.kt               # observeByTier/observeUnranked sort by tierRank
│       └── AnimeDatabase.kt          # MIGRATION_4_5
├── ui/screens/
│   ├── TiersScreen.kt                # rebuilt: flat drag model, long-press-drag
│   ├── TierDragDrop.kt               # NEW — drag state machine + pure list model (unit-testable)
│   └── RankHeadToHeadScreen.kt       # NEW — h2h session UI
└── (components/AnimeCard.kt          # + optional overflow "Unrank" action)
app/src/test/java/com/slippedpenguin/mangolist/
├── data/EloEngineTest.kt             # existing
├── data/TierListModelTest.kt         # NEW — flat-list move/insert/reorder logic
├── data/RankSessionTest.kt           # NEW — h2h elo/tier commit logic
└── data/local/AnimeEntryTest.kt      # extended: merge tests incl. tierRank preservation
.github/workflows/test.yml            # NEW — test-on-push
```

## Code Style

Follow the existing codebase: KDoc comment blocks explaining *why* above every
non-trivial construct, `val` first, compose functions PascalCase with params
in `modifier =`-last order, version notes inline (`// v1.7: ...`).

```kotlin
// v1.7: tier writes are local-only — never bump updatedAt, or SyncWorker
// drains a no-op push to AniList for every drag (see v1.5.1 auto-rank note).
dao.update(entry.copy(tier = tier, tierRank = rank, elo = elo))
```

Kotlin gotchas that already bit this repo (from HANDOFF.md, still binding):
`\$` in triple-quoted strings → use `${'$'}`; `JsonNull` is not Kotlin null →
safe casts only.

## Testing Strategy

- **Unit (CI gate):** JUnit4 in `app/src/test/`. All pure logic lives in
  testable objects: the flat tier-list model (moves, rank reassignment,
  elo interpolation on insert), the h2h session reducer (winner → elo delta →
  tier proposal → pool exhaustion), `preserveLocalFields` merge rules
  (including `tierRank`), `tierForScore`/`eloForScore` boundaries.
  Existing 2 test files must stay green; new tests target the merge and
  ranking logic that caused historical bugs.
- **Manual (sideload):** the only way to verify gesture UX. Per release:
  drag within tier, drag across tiers, drag to Unranked, h2h session on a
  fresh batch, airplane-mode ranking (all local), sync afterwards leaves
  tiers intact, restart preserves order.

## Boundaries

- **Always:** run the full reasoning through CI tests before tagging; keep
  tier/elo/tierRank writes `updatedAt`-neutral; preserve local fields on
  every sync merge; update HANDOFF.md with any behavior change.
- **Ask first:** Room schema changes (the v5 migration is explicitly
  approved via this spec — further ones re-ask); new dependencies; changing
  CI release workflow semantics; changing the AniList OAuth registration.
- **Never:** push tier/elo/tierRank to AniList (private by design);
  reintroduce destructive Room migrations as primary strategy; commit
  secrets (local.properties is gitignored); bypass the unit-test gate.

## Success Criteria

1. [CI] `test.yml` green on every push; release.yml still green on tag.
2. [CI] Unit suite ≥ 20 tests covering: tier-list model moves (within-tier
   reorder, cross-tier move, insert into empty tier), h2h session (win/lose
   elo math matches EloEngine, tier proposal, pool exhaustion), merge rules
   (tierRank preserved, local-newer wins tracking fields), no updatedAt bump
   from ranking writes.
3. [Manual/sideload] One-gesture ranking: press-hold → drag → drop into any
   tier; order survives restart AND pull-sync.
4. [Manual/sideload] A batch of N unranked titles can be tiered in ~N/2 taps
   via h2h; results appear in the tierlist immediately.
5. [Manual] Sign-out → sign-in leaves no stale userId-gated state; ranking
   data survives the round-trip.
6. [Release] v1.7.0 APK on GitHub Releases; installed on the user's phone.

## Open Questions

- ~~Room v5 `tierRank` migration~~ — surfaced as an explicit gate; approved
  (see interview answer) before implementation.
- None remaining.
