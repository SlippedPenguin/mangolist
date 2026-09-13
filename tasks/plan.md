# Implementation Plan: MangoList v1.7.0 — Tierlist Overhaul

Spec: `SPEC.md` (approved 2026-09-13). Tasks tracked in `tasks/todo.md`.

## Architecture Decisions

- **Flat drag model** (`ui/screens/TierListModel.kt`): one ordered flat list
  of entries grouped by tier; `move(entryId, toTier, targetIndex)` returns
  the updated rows (tier, tierRank, elo). Pure Kotlin, no Compose imports —
  unit-tested in CI, the composable is a thin shell over it.
- **Room v4→v5 migration** adds nullable `tierRank INTEGER`. NULL = never
  manually ordered. DAO sort within a tier: manually-ordered rows first (by
  tierRank ASC), then the v1.5.7 default (personalScore DESC, elo DESC) for
  the rest. `ALTER TABLE ADD COLUMN` with NULL default is additive — no
  data rewrite, existing rows untouched.
- **Long-press = drag gesture** (`detectDragGesturesAfterLongPress`). The
  long-press→bottom-sheet→tap flow is removed; Unrank happens by dragging to
  the Unranked bucket or via the card overflow. While dragging, the list
  renders as a flat reorderable column with tier headers as drop zones.
- **Elo on drop:** landing in tier T sets `elo = medianElo(T)` (or
  INITIAL_ELO when T is empty) — consistent with h2h math, and `tierRank`
  owns display order so no Elo ceremony is visible.
- **updatedAt-neutrality:** every ranking write (drag, h2h, unrank, auto-rank)
  leaves `updatedAt` untouched. Tier/elo/tierRank are local-only; bumping
  would flag no-op pushes to AniList (the current sheet path has this bug —
  the rebuild fixes it and a unit test pins it).
- **H2H as a reducer** (`RankSession`): pool of unranked entries; current
  candidate = pool head; opponent = `EloEngine.pickOpponent` (closest Elo);
  tap winner → `EloEngine.update` for elo, `EloEngine.proposeTier` for tier
  on the candidate; winner leaves the pool; session ends on pool exhaustion
  or Done. Pure class, unit-tested.
- **Fast CI loop** (`.github/workflows/test.yml`): on push to `main`, JDK 17
  + Gradle 8.11.1 (same pins as release.yml), write `local.properties`
  (public client id, placeholder secret — unit tests don't hit the network),
  run `testDebugUnitTest`. ~3 min. A tag is only cut after this is green.

## Task List

### Phase 1: Foundation
- [ ] Task 1: test.yml fast CI loop (verify: push → green run)
- [ ] Task 2: Room v5 `tierRank` + DAO sort + merge-rule unit tests
  (verify: CI unit suite green incl. new tests)

### Checkpoint: Foundation
- [ ] test.yml green on main; migration + model tests pass

### Phase 2: Core Features
- [ ] Task 3: `TierListModel` (pure move/insert/reorder/unrank logic) + tests
- [ ] Task 4: `TiersScreen` rebuild on the drag model (sheet removed)
- [ ] Task 5: `RankSession` reducer + `RankHeadToHeadScreen` + tests

### Checkpoint: Core Features
- [ ] CI green; full suite ≥ 20 tests; tag candidate ready for manual QA

### Phase 3: Hardening + Release
- [ ] Task 6: logout-state audit; airing banner accent polish
- [ ] Task 7: HANDOFF.md/README v1.7 deltas
- [ ] Task 8: tag v1.7.0 → CI APK → sideload QA checklist to user

### Checkpoint: Complete
- [ ] v1.7.0 APK published; manual QA checklist delivered

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Drag UX can't be verified without a device | High | All logic in unit-tested pure model; gesture shell kept thin; iterate via fast test.yml + patch tags (v1.7.1…) if on-device QA finds issues |
| Room migration corrupts user data | High | Additive nullable column; no destructive migration; merge tests pin tier/elo/tierRank preservation |
| CI-only loop slows iteration | Medium | test.yml ~3 min; every task lands green before the next starts |
| Drag conflicts with LazyColumn scroll | Medium | Long-press threshold + drag-over-item pattern (proven in Compose list DnD); drop zones = tier sections, not per-row gaps |
| H2H picks poor opponents early | Low | pickOpponent starts from median-tier entries; pool ordering by updatedAt keeps sessions fresh |

## Open Questions

- None remaining (Room v5 approved via spec interview).
