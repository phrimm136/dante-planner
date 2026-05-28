# Task: Featured Boss section on Theme Pack detail pages

Replicate the in-game Mirror Dungeon "등장 보스 / Featured Boss" panel at the top of each Theme Pack detail page: a row of per-boss panels (masked portrait inside a copper frame), one per entry in the pack's `showBossIds`.

## Status of prior work

The Python **image compositor already exists and is verified**: `static/scripts/featured_boss_panel.py` (geometry decoded from the level7 scene 2026-05-27; see memory `project_unity_sprite_atlas_extraction.md`). It bakes mask-stencil + portrait + hollow copper frame into a single webp per boss. This spec covers what remains: an index fix, pipeline wiring, the themepack data manifest, the Zod schema, i18n mining, and the frontend rendering. **Do not rebuild the compositor or re-decode geometry.**

## Decisions

- **`showBossIds` is the display source, not `bossPool`** — the game stores presentation (`uiConfigs.showBossIds`) separately from gameplay (`mapGenOption.bossPool`, the battle-encounter id). All 115 packs carry `showBossIds`; `bossPool` is irrelevant to this feature.
- **Per-enemy portrait, not SD appearance** — `Portrait/{key}_portrait.png` is the only render-ready per-enemy single file. Full-body SD art is a Unity atlas+prefab graph requiring a separate extractor (out of scope; see memory).
- **Pre-composited single webp, NOT live two-file CSS layering** — ⚠️ OVERTURNS the brainstorm "live layering / two files" decision. The implemented compositor bakes mask+portrait+frame into one image because the in-game panel uses a Unity `Mask` stencil that cannot be faithfully reproduced with CSS `clip-path`. The frontend renders one `<img>` per boss.
- **No panel backdrop at all** — ⚠️ OVERTURNS the brainstorm "B2: dedicated background sprite" decision AND the later "procedural flat color" assumption. Verified by decoding `m_Color` from level7: the panel container is `(1,1,1,α=0)` transparent and `[Image]Boss` is white/null-sprite. The dark-teal in the screenshot is the parent screen behind a transparent panel. The composited webps render on the detail page's existing background, like every sibling section. Nothing to extract, no color to introduce.
- **Output path `static/images/featuredBoss/{packId}_{portraitId}.webp`** — ⚠️ OVERTURNS the brainstorm "`static/images/portrait/{nameID}.webp`" path. The compositor output is a pack-scoped panel image (portrait varies per pack only by which bosses appear), already implemented at this path.
- **Extend `_build_unit_index` with an `sdPortrait` fallback and scope it to unit files** — because abnormality boss units have no `nameID` (they key portraits by `sdPortrait`), and `rglob('*.json')` over all static-data collides on non-unique ids (`id=1134` exists in both `abnormality-unit-a1c8p1.json` and `chance-iap.json`).
- **i18n label mined from raw, not authored** — `mirrordungeon_theme_pack_boss` ships in all 4 languages; authoring our own copy would drift on the next game update.
- **New script owns the enemy-image domain** — `featured_boss_panel.py` is separate from `theme_pack_image.py` (themepack-card art) per the "each script owns its domain" rule.

## Description

1. **Index fix**: extend `featured_boss_panel.py::_build_unit_index` to (a) scan only `*unit*`/`*enemy*` static-data files (avoids the `chance-iap.json` collision), and (b) fall back to `sdPortrait` when `nameID` is absent. `sdPortrait` may be `int` or `str` — normalize to the portrait filename key. Recovers ~34 abnormality bosses (verified on disk).
2. **Pipeline wiring**: replace the hardcoded `["1001","1005","1009"]` default in `main()` with iteration over all 115 packs; register the script as a step in `static/scripts/pipeline.py` (after `theme_pack`, before `validation`).
3. **Manifest**: extend `theme_pack.py` to emit, per pack, the resolved featured-boss roster so the frontend can build image URLs without re-deriving the resolution chain.
4. **Schema**: add the manifest field to `ThemePackSchemas.ts` and every parent schema it composes into (Zod strips unknown fields — see memory `feedback_zod_schema_fields`).
5. **i18n**: mine `mirrordungeon_theme_pack_boss` from `raw/LocalizeLimbusCompany/{EN,KR,JP,LLC_zh-CN}/MirrorDungeonUI_4.json` into `static/i18n/{EN,KR,JP,CN}/database.json` under `themePack.featuredBoss`.
6. **Frontend**: a `FeaturedBoss` section at the top of the right column of `ThemePackDetailPage.tsx`, rendering N panels in a flex-wrap grid on the page's existing background (no backdrop color), each panel an `<img>` from `getFeaturedBossImagePath(packId, portraitId)`. Skip bosses with no composited image (graceful degradation).

## Data Model Catalog

Scanned from `raw/.../static-data/mirrordungeon-theme-floor/mirrordungeon-theme-floor-t{1..6}.json` (2026-05-27):

```
themepacks total: 115 (t1:27 t2:27 t3:6 t4:21 t5:14 t6:20)
uiConfigs.showBossIds present: 115 / 115 (100%)
showBossIds length distribution:
  1 boss  → 37 packs        4 bosses → 8 packs
  2 bosses→ 24 packs        5 bosses → 1 pack
  3 bosses→ 38 packs        6 bosses → 5 packs
                            8 bosses → 1 pack (1022)
                           16 bosses → 1 pack (1507)  ← max, layout edge case
```

Boss-unit resolution (290 refs over 115 packs; 177 unique unitIds):
```
ref → nameID record:                99 unique   (regular enemies, enemy-*.json)
ref → sdPortrait-only record:       42 unique   (abnormality units, abnormality-*-unit.json)
  of which portrait PNG exists:     34          (8 art not shipped → skip)
ref → unresolved (battle-only/etc): remainder   (no unit record → skip)
```
unitId namespaces seen in showBossIds: `1xxx` (abnormality), `8xxx` (abnormality/enemy), `70xxx`/`71xxx`/`90xxx`/`91xxx` (enemy).

Portrait key field by record family:
```
enemy-*.json            → nameID   (int)        portrait: Portrait/{nameID}_portrait.png
abnormality-*-unit.json → sdPortrait (int|str)  portrait: Portrait/{sdPortrait}_portrait.png
```

## Normalization Layer

| Cleanup | Where | Why |
|---|---|---|
| Resolve `showBossIds` → portrait key (nameID/sdPortrait) | Pipeline (`theme_pack.py` + `featured_boss_panel.py`) | Build-time join; frontend gets a flat roster |
| Compose mask+portrait+frame into one webp | Pipeline (`featured_boss_panel.py`) | Stencil can't be done in CSS; bake once |
| Skip unresolved / portrait-missing bosses | Pipeline | Frontend receives only renderable entries |
| webp encode (q85, method6, lossless=false) | Pipeline (matches `png_to_webp.py`) | Consistent asset format |
| Section label text | Pipeline (i18n mine) → `themePack.featuredBoss` | No runtime mapping tables |
| Grid layout, spacing, lazy-load (no backdrop color — panel is transparent) | Frontend (CSS) | Pure rendering concern |

Rule: frontend receives a clean `featuredBosses` roster + ready-made webp URLs. No runtime resolution.

## Rendering Mode Enumeration

```
1. Single boss            (1001 → [71001])           one panel
2. Few bosses (2–4)       (1002 → [8001,8002,8094])  flex row, no wrap on desktop
3. Many bosses (6–8)      (1022 → 8 bosses)           wraps to 2 rows
4. Max bosses (16)        (1507 → 16 bosses)          multi-row wrap, scroll/density check
5. Partial roster         pack with some unresolved   render resolvable, silently drop rest
6. Empty after resolution (hypothetical: all skip)    section hidden entirely (no empty header)
```

## Reference Per Mode

Mode 1 matches the user's screenshot: dark-teal section header "등장 보스" above one framed boss thumbnail. Modes 2–4 are the same panel repeated in a `flex flex-wrap gap-3` grid. Mode 6: if `featuredBosses` is empty, render nothing (mirror how `ExclusiveEventsSection` self-hides when its list is empty, `ThemePackDetailPage.tsx`).

## Scope (read for context)

- `static/scripts/featured_boss_panel.py` — the implemented compositor (DO NOT rewrite the geometry/compositing)
- `static/scripts/theme_pack.py` — themepack data emitter (extend for manifest)
- `static/scripts/theme_pack_image.py` — sibling image script (pattern reference only)
- `static/scripts/pipeline.py` — pipeline orchestration (add step)
- `static/scripts/lang_config.py` — `RAW_ASSETS_DIR`, `RAW_JSON_DIR`, `IMAGES_DIR` paths
- `static/scripts/png_to_webp.py` — webp encode params
- `frontend/src/schemas/ThemePackSchemas.ts` — Zod schema (and its parent composition chain)
- `frontend/src/routes/ThemePackDetailPage.tsx` — render target; `SpecificEgoGifts`/`SectionTitle`/`ExclusiveEventsSection` are the patterns to mirror
- `frontend/src/lib/assetPaths.ts` — asset URL helpers (~line 574 `getThemePackImagePath`)
- `static/i18n/{EN,KR,JP,CN}/database.json` — i18n destination; existing `themePack.*` keys
- `raw/LocalizeLimbusCompany/{EN,KR,JP,LLC_zh-CN}/MirrorDungeonUI_4.json` — i18n source

## Target (create or modify)

| File | Action |
|---|---|
| `static/scripts/featured_boss_panel.py` | MODIFY — `_build_unit_index` (scope + sdPortrait fallback); `main()` iterate all packs |
| `static/scripts/theme_pack.py` | MODIFY — emit `featuredBosses` roster per pack |
| `static/scripts/pipeline.py` | MODIFY — register `featured_boss_panel` step |
| `frontend/src/schemas/ThemePackSchemas.ts` | MODIFY — add `featuredBosses` + propagate to parent schemas |
| `static/i18n/{EN,KR,JP,CN}/database.json` | MODIFY — add `themePack.featuredBoss` |
| `frontend/src/lib/assetPaths.ts` | MODIFY — add `getFeaturedBossImagePath(packId, portraitId)` |
| `frontend/src/routes/ThemePackDetailPage.tsx` | MODIFY — add `FeaturedBoss` component + mount at top of right column |
| `static/data/themePack/{packId}.json` | REGENERATE — pipeline output (not hand-edited) |
| `static/images/featuredBoss/*.webp` | REGENERATE — pipeline output |

## Impact Analysis

- **`ThemePackSchemas.ts`**: HIGH — a strict field added to a schema composed into `SaveablePlannerSchema` could discard saved planners on load (memory `saveable_planner_schema_blast_radius`). `featuredBosses` is read-only pack metadata, not planner state — confirm it is NOT part of any saveable composition; if it is, make it `.optional()` or `.catch([])`.
- **`theme_pack.py` output**: MEDIUM — changes every `static/data/themePack/{packId}.json`; consumers re-validate against the new schema.
- **`pipeline.py`**: LOW — additive step.
- **Ripple**: frontend bundle gains a component + image requests; the 16-boss pack adds 16 image fetches on one page.

## Risk Assessment

- **Edge cases**:
  - Pack 1507 (16 bosses) — grid wrap, page weight, Suspense fallback sizing.
  - `sdPortrait` as string vs int — filename key normalization.
  - Global id collision (`1134`) — index must be unit-file-scoped.
  - Boss resolvable but portrait art not shipped (8 cases) — compositor already skips; frontend must not request a 404 URL → only list bosses that produced a webp.
  - Pack where every boss is unresolved — section must hide, not render an empty header.
- **Performance**: 16 webp requests on pack 1507; panels are small (~306×360 px at scale 2) so total weight is modest, but consider `loading="lazy"`.
- **Security**: none (static assets, no user input).

## Boundaries & Invariants

- **Ownership boundary**: `featuredBosses` is pipeline-owned, read-only pack metadata. The frontend never writes it; it is not planner/save state.
- **Invariant 1**: every `portraitId` listed in a pack's `featuredBosses` manifest has a corresponding `static/images/featuredBoss/{packId}_{portraitId}.webp` on disk (no manifest entry without an image).
- **Invariant 2**: the frontend issues an image request only for manifest entries (no derived/guessed URLs).
- **Invariant 3**: adding `featuredBosses` never causes a previously-valid themepack JSON or saved planner to fail schema validation.
- **Invariant 4**: composited geometry constants in `featured_boss_panel.py` are never rounded/reformatted (they are exact in-scene RectTransform values — `static/CLAUDE.md` security rule + trap-street memory).

## Failure Modes

| Invariant | Trigger (how it breaks) | Response | Test |
|---|---|---|---|
| Inv 1 | Compositor skips a boss (no portrait) but `theme_pack.py` still lists it | Manifest is built from the SAME resolution+existence check the compositor uses; a skipped boss is omitted from the manifest | `test_manifest_matches_disk` |
| Inv 1 | Re-run after game update removes a portrait | Pipeline regenerates manifest each run from current disk state; stale entries can't persist | `test_manifest_regenerated_from_disk` |
| Inv 2 | Frontend builds URL from `showBossIds` directly | Component iterates `featuredBosses` only, never `showBossIds` | FE test `FeaturedBoss.renders-only-manifest` |
| Inv 3 | `featuredBosses` added as required field into a saveable schema | Field lives on pack-data schema only; if shared, marked `.optional()`/`.catch([])` | `test_saveable_planner_unaffected` |
| Inv 4 | A formatter/edit rounds `PANEL_W=153.26` etc. | Constants carry a do-not-round comment; review check | manual review + grep guard |
| (pipeline) | Script dies mid-run (partial webp set) | Each webp write is independent + idempotent (overwrite by name); re-run completes the set | `test_rerun_idempotent` |

### Visualized Failure

Worst row — Inv 1 drift between manifest and disk:

1. Game update ships; a boss's portrait PNG is removed from `raw`.
2. `featured_boss_panel.py` runs: `compose_panel` returns `None` for that boss, no webp written.
3. If `theme_pack.py` listed that boss from `showBossIds` *independently*, the manifest now names a webp that doesn't exist → frontend `<img>` 404s, broken panel rendered.
4. **Response**: `theme_pack.py` builds `featuredBosses` by calling the SAME resolve-and-check helper the compositor uses (shared function), so a boss is in the manifest **iff** its webp was produced. The drift in step 3 cannot occur.

## Done When

- [ ] `_build_unit_index` scoped to unit files + `sdPortrait` fallback (int/str); abnormality bosses resolve.
- [ ] `python3 scripts/featured_boss_panel.py` (no args) generates panels for all 115 packs; logs skipped bosses.
- [ ] `featured_boss_panel` registered in `pipeline.py`.
- [ ] Each `static/data/themePack/{packId}.json` contains a `featuredBosses` roster matching the webps on disk.
- [ ] `ThemePackSchemas.ts` validates the new field; no existing themepack JSON or saved planner fails to load.
- [ ] `themePack.featuredBoss` present in all 4 `database.json` files, values mined from raw (`등장 보스` / `Featured Boss` / `出現ボス` / `出现头目`).
- [ ] Theme pack detail page shows the Featured Boss section at the top of the right column with N framed boss panels on the page background (no extra backdrop).
- [ ] Pack 1507 (16 bosses) lays out without overflow; pack with all-unresolved bosses hides the section.
- [ ] Frontend typecheck passes; all existing FE + pipeline tests pass.

## Test Plan

### Test Runner
- Frontend: `vitest` — `yarn --cwd frontend test src/...` (tests in `__tests__/` subdirs per memory `feedback_tests_convention`).
- Pipeline: pytest if present under `static/scripts`; otherwise add `static/scripts/__tests__/test_featured_boss_panel.py`. Confirm the runner — flag in ambiguities if none exists.
- Redirect all build/test output to `/tmp/<prefix>-<session-id>-<suffix>.log` (memory convention).

### Tests to Write
- [ ] `_build_unit_index` resolves a known abnormality unit via `sdPortrait` (e.g. 1058→8605): pipeline test.
- [ ] `_build_unit_index` does NOT resolve `id=1134` from `chance-iap.json` (collision): pipeline test.
- [ ] `test_manifest_matches_disk`: every `featuredBosses` portraitId has a webp; every webp is in some manifest.
- [ ] `test_rerun_idempotent`: running the compositor twice yields identical output set.
- [ ] `FeaturedBoss` renders one panel per manifest entry, none for empty roster: `frontend/src/routes/__tests__/`.
- [ ] `getFeaturedBossImagePath` builds `featuredBoss/{packId}_{portraitId}.webp`.
- [ ] Every `Test` cell in Failure Modes is realized.

## Verification

### Manual
1. Run `python3 scripts/featured_boss_panel.py` from `static/`; confirm `static/images/featuredBoss/` populates and skip-logs are sane.
2. Run the data pipeline step; inspect `static/data/themePack/1001.json` for `featuredBosses`.
3. Load the dev frontend; open Theme Pack 1001 detail — verify the single framed boss panel under a "Featured Boss" header at the top of the right column.
4. Open pack 1507 — verify 16 panels wrap cleanly.
5. Switch language to KR — verify header reads `등장 보스`.

### Edge Cases
- [ ] Pack 1507 (16 bosses): no horizontal overflow, lazy-loads.
- [ ] Abnormality-boss pack (sdPortrait path): panels render.
- [ ] Pack with unresolved bosses: only resolvable panels show; no broken `<img>`.
- [ ] Saved planner from before this change still loads.

## Resolved decisions (were ambiguities)

1. **Manifest field shape** — RESOLVED: `featuredBosses: [{ unitId: number, portraitId: number | string }]`. Keeps `unitId` for a future `/enemy/$id` link; `portraitId` is the filename key (nameID or sdPortrait).
2. **Pipeline test framework** — RESOLVED: none exists; create one. Add `static/scripts/__tests__/` with pytest; first test file `static/scripts/__tests__/test_featured_boss_panel.py`. Provide a `conftest.py`/`pytest.ini` as needed so `python3 -m pytest scripts/__tests__/` runs from `static/`.
3. **Panel backdrop** — RESOLVED VIA DECODE, premise corrected. Decoding `m_Color` from level7 shows the panel container `MirrorDungeonThemeEnemyInfoUI` is `(1,1,1,α=0)` fully transparent, and `[Image]Boss` is `(1,1,1,1)` white with null sprite. The panel owns **no backdrop color** — the dark-teal in the screenshot belongs to the parent Mirror Dungeon screen showing through. Therefore: the composited boss webps (transparent outside the copper frame) render directly on the detail page's existing background, like every sibling section. **No dedicated backdrop CSS color is extracted or introduced.** If a deliberate dark band behind the section is later wanted, that is a fresh design decision, not an extractable game value.
