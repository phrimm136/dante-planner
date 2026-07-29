# Execution Plan — Featured Boss section

## Phase Summary

Six sequential phases. The architectural crux is **Inv 1**: `theme_pack.py` and the
compositor must derive the boss roster from the **same** function, so a boss appears in
the manifest iff its webp was produced. That shared resolver is Phase 1's deliverable and
lives in `featured_boss_panel.py` (which owns the enemy-image domain).

Data/schema lockstep uses **ordering (a)**: regenerate all 116 themePack JSONs (Phase 2)
*before* the schema declares `featuredBosses` required (Phase 5). `ThemePackDetailSchema`
has no `.strict()` and is NOT in any `SaveablePlanner` chain (verified), so the extra field
validates harmlessly in between — `dataIntegrity.test.ts` stays green throughout.

Portrait-key precedence: **prefer `nameID`, fall back to `sdPortrait` when absent**
(spec instruction #1). `sdPortrait` may be int or str — normalize to the filename key.
Stamp this precedence with a do-not-flip comment.

## Phases

### Phase 1: Shared resolver + index fix + all-packs loop (compositor)
- Files: `static/scripts/featured_boss_panel.py`; new `static/scripts/__tests__/` + `conftest.py`
- Deliverables:
  - `_build_unit_index()`: scope scan to `abnormality-unit/` + `enemy/` dirs only (avoids
    the `chance-iap.json` id=1134 collision); value = portrait key with **nameID preferred,
    sdPortrait fallback** (normalize int|str → str key).
  - Export `resolve_pack_bosses(pack_id, unit_index) -> list[tuple[unitId, portraitId]]`
    that returns only entries whose `Portrait/{portraitId}_portrait.png` exists. This is the
    single source the manifest and the compositor both consume.
  - `main()`: iterate all 115 packs (replace hardcoded `["1001","1005","1009"]`); compose +
    save `{packId}_{portraitId}.webp`; log skipped bosses.
- Tests: `static/scripts/__tests__/test_featured_boss_panel.py`
  - `1058 → 8605` resolves via sdPortrait fallback.
  - `id=1134` from `chance-iap.json` does NOT resolve (dir-scoping).
  - both-present record (e.g. 8114) resolves to `nameID` (precedence).
  - `resolve_pack_bosses` returns only entries with an on-disk portrait.
- Depends on: none
- Verify: `python3 scripts/featured_boss_panel.py` (no args, cwd `static/`) populates
  `static/images/featuredBoss/` for all packs; `python3 -m pytest scripts/__tests__/` green.

### Phase 2: Manifest emission (theme_pack.py)
- Files: `static/scripts/theme_pack.py`
- Deliverables: import `resolve_pack_bosses` + `_build_unit_index` from `featured_boss_panel`;
  per pack, write `featuredBosses: [{ unitId, portraitId }]` into `static/data/themePack/{id}.json`.
  Regenerate all 116 files.
- Tests: `test_manifest_matches_disk` — every manifest `portraitId` has a webp in `OUTPUT_DIR`;
  every webp belongs to some manifest (set-equality). `test_rerun_idempotent`.
- Depends on: Phase 1
- Verify: `static/data/themePack/1001.json` has `featuredBosses`; pytest green.

### Phase 3: Pipeline wiring (pipeline.py)
- Files: `static/scripts/pipeline.py`
- Deliverables: append `featured_boss_panel` to the existing `"Theme Pack"` step scripts list,
  after `theme_pack` and `theme_pack_image` (i.e. after theme_pack, before validation).
- Depends on: Phase 1
- Verify: `python3 pipeline.py --dry-run` lists `featured_boss_panel.py` under Theme Pack.

### Phase 4: i18n mine (database.json)
- Files: `static/i18n/{EN,KR,JP,CN}/database.json`
- Deliverables: add `themePack.featuredBoss` mined from
  `raw/LocalizeLimbusCompany/{EN,KR,JP,LLC_zh-CN}/MirrorDungeonUI_4.json`
  (`mirrordungeon_theme_pack_boss` → EN `Featured Boss` / KR `등장 보스` / JP `出現ボス` /
  CN `出现头目`). ⚠️ Append the **same trap-street zero-width suffix** carried by the sibling
  `themePack.*` keys in each file (copy from the adjacent key per file; do not write clean text).
- Depends on: none (independent; sequenced here)
- Verify: each file's `themePack.featuredBoss` equals mined value + sibling suffix.

### Phase 5: Schema (ThemePackSchemas.ts)
- Files: `frontend/src/schemas/ThemePackSchemas.ts`
- Deliverables: add `FeaturedBossSchema = z.object({ unitId: z.number(), portraitId: z.union([z.number(), z.string()]) }).strict()`;
  add `featuredBosses: z.array(FeaturedBossSchema)` (required — ordering (a)) to
  `ThemePackDetailSchema`. Do NOT add to any saveable/planner schema.
- Tests: `frontend/src/schemas/__tests__/ThemePackSchemas.test.ts` (extend) + run
  `dataIntegrity.test.ts`.
- Depends on: Phase 2 (all JSONs carry the field)
- Verify: `yarn --cwd frontend test src/schemas` green; typecheck green.

### Phase 6: Frontend render (assetPaths + component)
- Files: `frontend/src/lib/assetPaths.ts`, `frontend/src/routes/ThemePackDetailPage.tsx`,
  `frontend/src/routes/__tests__/` (FeaturedBoss test)
- Deliverables:
  - `getFeaturedBossImagePath(packId, portraitId)` → `featuredBoss/{packId}_{portraitId}.webp`.
  - `FeaturedBoss` component: `flex flex-wrap gap-3`, one `<img loading="lazy">` per manifest
    entry, no backdrop color; self-hides when roster empty (mirror `ExclusiveEventsSection`).
    Mounted at the TOP of the right column under a `SectionTitle` using
    `t('themePack.featuredBoss')`.
- Tests: `FeaturedBoss` renders one panel per manifest entry, none for empty roster;
  `getFeaturedBossImagePath` builds the right URL.
- Depends on: Phase 5
- Verify: `yarn --cwd frontend test src/routes`; typecheck green.

## Phase Dependencies
- Group A: Phase 1
- Group B (after 1): Phase 2, Phase 3
- Group C (independent): Phase 4
- Group D (after 2): Phase 5
- Group E (after 5): Phase 6

Executed strictly sequentially 1→6 for clean per-phase verification.
