# Todo — MangoList v1.7.0

Plan: `tasks/plan.md` · Spec: `SPEC.md` — ✅ SHIPPED 2026-09-13 (v1.7.0 on GitHub Releases)

## Phase 1: Foundation
- [x] Task 1: Fast CI loop (`test.yml`, test-on-push)
- [x] Task 2: Room v6 `tierRank` + DAO sort + merge unit tests

## Phase 2: Core Features
- [x] Task 3: `TierListModel` pure logic + tests
- [x] Task 4: `TiersScreen` drag & drop rebuild (remove long-press sheet)
- [x] Task 5: `RankSession` reducer + `RankHeadToHeadScreen` + tests

## Phase 3: Hardening + Release
- [x] Task 6: Logout audit (scoped clear + worker cancel) + airing banner accent
- [x] Task 7: Docs — HANDOFF/README v1.7 deltas
- [x] Task 8: Tag v1.7.0 → CI APK (3.39 MB, signed) → sideload QA checklist

## Checkpoints
- [x] Foundation: test.yml green on main; migration + model tests pass
- [x] Core: CI green, full suite passes (20 new tests), tag candidate ready
- [x] Complete: v1.7.0 published + QA checklist delivered

## v1.8.0 — full UI redesign (shipped 2026-09-13)
- OLED palette (true-black ladder, dual accent, brand gradient) — Color.kt/Theme.kt
- Bebas Neue display + Inter UI via downloadable Google Fonts (res/font, certs in values)
- Floating glass dock bottom nav; generic top bar removed; Bebas screen titles
- Home rebuilt: Bebas hero, inline stat row, Continue strip, gradient tier banner
- Shared components restyled (AnimeCard, AnimePosterCard, CenteredPillTabs, LibraryHeader)
- Dock-clearance bottom padding on all scrolling screens
- Drag tierlist kept as-is per user decision (clunky on phone; not removed)
- 3 CI iterations (certs path, nested-comment, Brush overload) then green; APK published
