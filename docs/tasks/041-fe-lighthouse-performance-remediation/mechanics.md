# Mechanics — FE Performance Remediation

Companion to `requirements.md`. Transcribed from the 2026-07-18 diagnosis session (48-run Lighthouse sweep against the production build of `dev`). Sections marked **binding** are contract; §1's table is the **reference baseline** all budget verifications compare against.

## §1 Measured baseline (reference — regression anchor)

Conditions: `vite build` output served by `vite preview` on :4173, backend proxied via nginx :80, Chromium headless, Lighthouse defaults (mobile = simulated slow-4G throttling; desktop = `--preset=desktop`). `moderation` measured logged-out; `/planner/md/$id{,/edit}` excluded (IndexedDB-dependent).

**Caveat (discovered 2026-07-18, after the sweep):** the sweep's build carried `.env`'s `VITE_API_BASE_URL=http://localhost` (absolute origin), so browser-context API fetches from the `:4173` preview origin were CORS-blocked and API-backed routes rendered error/empty states. Shell-, font-, and asset-bound findings are unaffected.

**Re-sweep with live data (2026-07-18, backend CORS allowlist extended to `:4173`, stock build) — these rows supersede §1 for the API-backed routes:**

| Route | Mobile score/FCP/LCP/CLS/KB | Desktop score/LCP | vs tainted row |
|---|---|---|---|
| home | 38 / 9.1s / 11.3s / 0.38 / 1877 | 71 / 2.0s | unchanged — validated |
| gesellschaft | 56 / 9.1s / 9.6s / **0.00** / 1493 | 85 / 1.7s | **CLS 0.63 was a CORS artifact**; with live data the footer shift does not occur here |
| gesellschaft-detail | 56 / 9.6s / **20.0s** / 0.00 / **4861** | 72 / 3.4s | far heavier than the tainted row (10.7s/1600KB) — live plan data pulls full deck art |
| planner-md | 53 / 9.2s / 9.9s / 0.12 / 1452 | 85 / 1.8s | unchanged |

Consequences: home's 0.38 is the real footer-shift evidence for R5 (Decision 9); gesellschaft-detail joins the browse pages as an LCP-worst route and is a primary R3/R7 beneficiary.

Mobile (score / FCP / LCP / CLS / transfer):

| Route | Score | FCP | LCP | CLS | KB |
|---|---|---|---|---|---|
| gesellschaft | 34 | 9.1s | 9.6s | 0.63 | 1440 |
| home | 38 | 9.1s | 11.2s | 0.38 | 1875 |
| ego-gift-detail | 49 | 9.0s | 10.1s | 0.17 | 1510 |
| moderation | 49 | 8.9s | 9.4s | 0.17 | 1409 |
| planner-md | 53 | 9.2s | 9.8s | 0.12 | 1453 |
| planner-md-new | 54 | 9.5s | 19.5s | 0.00 | 4337 |
| identity | 55 | 9.1s | 18.4s | 0.00 | 4408 |
| ab-event | 56 | 9.1s | 12.5s | 0.00 | 13399 |
| deck-builder | 56 | 9.2s | 19.3s | 0.00 | 3941 |
| ego | 56 | 9.0s | 22.1s | 0.00 | 8584 |
| theme-pack | 56 | 9.0s | 13.7s | 0.00 | 12069 |
| theme-pack-detail | 56 | 9.1s | 18.5s | 0.04 | 4667 |
| (remaining routes) | 56-57 | 8.9-9.2s | 9.2-12.8s | ≤0.02 | 1405-2763 |

Desktop: scores 69-87, FCP uniform 1.6-1.7s; worst LCP ego 5.2s. TBT ≤ 150ms everywhere on both form factors — compute is a non-factor (Decision 1).

## §1b Plan-loading baseline (Playwright, 2026-07-18 — reference)

Functional loading timings for the plan routes, complementary to §1 and NOT comparable to it (unthrottled localhost, warm HTTP cache, Playwright Chromium, corrected same-origin build per §6). n=10 full reloads per target after 1 discarded warm-up; TTFB 2-7ms throughout; `content` = first moment the route's content probe passes (list: a plan **card** link — the probe must match the uuid pattern, NOT `a[href^="/planner/md/"]`, which also matches the "Create New" nav link and fires on the shell; an earlier run made exactly that mistake and read ~120ms for the list). Local plan detail measured against a UI-created plan in IndexedDB — the seeded-storage method §1 lacked. A first measurement ran under machine-wide OOM pressure; this table is from the re-run on a healthy machine (detail-route deltas vs the tainted run were within ±10%, list corrected per the probe note).

Production build via `vite preview` (the reference):

| Target | content p50 / p95 | FCP p50 / p95 | LCP p50 / p95 | DCL p50 |
|---|---|---|---|---|
| `/planner/md` (list) | 442 / 453 ms | 140 / 155 ms | 140 / 155 ms | 74 ms |
| `/planner/md/$id` (local detail) | 449 / 483 ms | 158 / 224 ms | 386 / 560 ms¹ | 74 ms |
| `/planner/md/gesellschaft/$id` (published detail) | 482 / 512 ms | 170 / 218 ms | 592 / 623 ms | 83 ms |

`yarn dev` (:5173, same probes — dev-server overhead reference, not a production figure):

| Target | content p50 / p95 | FCP p50 / p95 | LCP p50 / p95 | DCL p50 |
|---|---|---|---|---|
| list | 996 / 1094 ms | 548 / 633 ms | 696 / 765 ms | 474 ms |
| local detail | 1234 / 1271 ms | 640 / 658 ms | 856 / 1138 ms¹ | 559 ms |
| published detail | 1238 / 1345 ms | 636 / 676 ms | 1328 / 1503 ms¹ | 559 ms |

¹ Bimodal on detail routes: LCP flips between the text block and a card image finishing later — the LCP element is unstable there.

Interpretation: prod-build content lands ~440-510ms with DCL at ~75ms — the ~370-430ms gap is entry-graph evaluation + lazy route chunk + storage/API read + render, the same critical path R1 shrinks. Dev mode is ~2.5× slower across the board (per-module serving + dev-mode React), so DX complaints and production symptoms share the same shape but not the same numbers. Percentiles per the measurement discipline; n=10 makes p95 indicative, p99 meaningless.

Throttled plan routes (CDP observed throttling: 1.6Mbps down / 150ms RTT / 4× CPU, cache disabled, n=3 after warm-up; spread <1% so all three shown as the range):

| Target | FCP | content |
|---|---|---|
| plan list | ~4.74s | 5.49-5.53s |
| local detail | ~6.03s | 6.81-6.87s |
| published detail | ~6.19s | 7.09-7.13s |

KR locale (same conditions): published-detail content 7.23s — indistinguishable from EN; KOTRA TTF confirmed loading under Hangul. Pretendard's KR capture window was truncated (748KB needs ~3.7s at this bandwidth; snapshot at 2.5s) — re-audit KR font slices after R2 lands.

Editor interaction latency (edit route, seeded probe plan, unthrottled, Event Timing API ≥16ms, 87 samples across title typing + tiptap note typing): p50 24ms, p95 32ms, max 32ms, zero long tasks — INP is healthy at small plan size. The `JSON.stringify` dirty-check gotcha (O(n) in plan size, meme `gotcha-json-stringify-based-dirty-state-detection`) remains open for LARGE plans; this measurement does not close it. Deck-card toggle latency unmeasured (pointer-interception in the test; drive the card wrapper by role in the R7 drill).

## §1c Plan-route profile + skeleton geometry audit (reference — basis of R7)

Boundary profile, local plan detail, prod build, unthrottled (medians of 6 runs; instrumented via injected init script — IDB/fetch patches + Resource Timing; the React-commit observer failed to register, marks bracket the answer regardless):

| Milestone | ms |
|---|---|
| DCL (entry evaluated) | 76 |
| IndexedDB opened (route loader's title read) | 82 |
| First API fetch done | 123 |
| All lazy chunks arrived (code + JSON-data chunks) | 180 |
| Plan content visible | 450 |

Published detail: loader fetch done ~108ms, chunks ~180ms, content ~453ms. **Conclusion: all inputs ready by ~180ms; the ~270ms tail (60%) is mount/render/effect work.** This profile REJECTED the loader-parallel-data lever (Decision 12) — data already loads during routing.

Skeleton geometry audit (desktop 1440px; loaded heights measured via Playwright, skeleton heights derived from their source classes). The plan viewer renders per-section `Suspense` fallbacks (`GuideModeViewer.tsx`); the route-level inline fallback (~980px) only bridges until the viewer mounts, so fidelity is judged per section (corrected 2026-07-18 after user review — an earlier draft wrongly compared the route-level fallback against the whole page):

| Plan-viewer section | Fallback reserves | Loaded | Verdict |
|---|---|---|---|
| Deck Builder | 12 tiles `w-16 h-20` ≈ ~116px | ~780px (cards 160×290) | inconsistent — tile scale |
| Start Buff | `h-32` stub (128px) | ~250px (selected card 272×320) | inconsistent |
| Start Gift | `h-32` stub (128px) | ~202px | inconsistent (moderate) |
| EGO Gift Observation | shaped, ~205px | ~250px | faithful — pattern to copy |
| Skill Replacement | shaped grid, ~350px | ~380px | faithful — pattern to copy |
| Comprehensive Gift Grid | text-line fallback ~130px | ~571px | inconsistent — no skeleton |
| Floor Theme Gallery | text-line fallback ~85px | ~1,500-2,400px | worst — no skeleton |

| Other surface | Skeleton reserves | Loaded content | Gap |
|---|---|---|---|
| Gesellschaft list grid | 8 cards × 280×160 ≈ 2 rows ~336px | 18-20 cards, 150px tall, grid 813px | ~480px under + 10px/card height drift (constant 160 vs rendered 150) |
| Home page-level fallback | `LoadingState` ≈ 256px text box | main 2,116px (announcements 684, community grid 1,006) | ~1,800px under |

## §2 Eager-graph inventory (binding targets for R1)

Entry HTML: 1 entry script + 27 `modulepreload` + 1 CSS = **520KB gzip eager**. Offenders that must leave the eager set:

| Chunk (gz) | Content | Pulled in by |
|---|---|---|
| entry, 111.5KB | `pako`, `src/pages/planner/hooks`, `dompurify` | `GlobalLayout.tsx:5-9` → `@/pages/planner`+`@/pages/settings` barrels; `router.tsx:18-21` static `fetchPublishedPlanner` import |
| 91.8KB | `shared/filter/components`, `shared/assets`, `shared/skill/components` | planner barrel graph |
| 49.7KB | `pages/egoGift/components` | planner `lib/{floorGiftBucketing,plannerRules,plannerValidation}.ts` → `@/pages/egoGift` |
| 11.9KB | `pages/ego` | same graph |

Lighthouse `unused-javascript`: 98KB of the 113KB entry transfer unused (~87%). Legitimate eager residents (react-dom 57.5, radix 36.2, tanstack 35.1, i18n 19.7, zod 18.2, tailwind-merge/date-fns 59.7, sonner 8.9 — KB gz): sum ≈ 250KB → the 320KB budget (§5) has ~70KB headroom.

`@tiptap/*` chunk (118KB gz, 105KB unused) loads on `gesellschaft-detail` + `planner-md-new` route chunks (R6 target).

## §3 Image economics (binding targets for R3/R4)

| Page | Images fetched | Bytes | Note |
|---|---|---|---|
| ab-event | 165 | 11.7MB | 130-260KB each; `AbEventCard` has NO `loading="lazy"` |
| theme-pack | 120 | 10.1MB | ~120KB each; `ThemePackCard` has NO lazy |
| ego | 90 | 6.7MB | 200-240KB each; lazy present but Chrome lookahead (1250-2500px) still fetches ~90 |
| planner-md-new / deck-builder | — | 2.2-2.4MB | roster picker renders full EGO art (140-180KB each) |

Thumb contract: webp, ≥ 2× largest rendered slot (INV7), target ~10-20KB/file; grid consumers switch, detail pages keep originals (Decision 7). Grid-consumed art dirs confirmed by measurement: `images/ego/*`, `images/identity/*`, `images/abEvent/*`, `images/themePack/*`, plus roster-picker consumption of `images/ego/*`. `images/icon/egoGift` files are already icon-sized (~10KB avg) — no thumb variant needed for the egoGift grid.

## §4 Fonts (binding constraints for R2)

| File (source) | Dist transfer | Loaded on | Constraint |
|---|---|---|---|
| `Pretendard-Regular.woff2` (full) | 748KB | every page | replace with vendored `pretendard-subset.css` slices (Decision 5) |
| S-Core Dream TTF | 306KB | planner pages | **binary untouchable** — license prohibits conversion (Decision 6) |
| KOTRA_BOLD TTF | 57KB | every page | **binary untouchable** — same |

Current declaration to remove: the hand-written Pretendard `@font-face` in `globals.css` (~line 21-26, three-format src). Replacement: import the vendored `frontend/src/assets/fonts/Pretendard-1.3.9/web/static/pretendard-subset.css` (already unicode-range sliced; OFL-licensed, vendor-shipped — no file generation on our side). `font-display: swap` semantics unchanged (B12).

## §5 Budget ledger (binding)

| Budget | Value | Verified by |
|---|---|---|
| Eager JS (entry + modulepreload) | ≤ 320KB gzip | eager-set gate (§6) |
| Mobile FCP | < 4s, every measured route | harness sweep |
| Mobile LCP | < 5s on browse routes (identity, ego, ego-gift, theme-pack, ab-event, keyword lists) | harness sweep |
| CLS | < 0.1 on home + gesellschaft | harness sweep |
| Non-planner mobile font transfer | ≤ 200KB | network audit per page |

## §6 Verification harness recipe (binding — reconstruct in scratchpad, do not commit)

1. Build with maps and a same-origin API base (else browser API fetches are CORS-blocked from the preview origin — see §1 caveat): `VITE_API_BASE_URL=http://localhost:4173 ROLLDOWN_MAX_BLOCKING_THREADS=512 yarn --cwd frontend vite build --sourcemap`. The resulting `dist/` is a measurement artifact, not deployable.
2. Serve: `yarn --cwd frontend preview --port 4173` (backend must be up — nginx :80 proxies `/api`; without it API-backed pages render error states and skew LCP/CLS)
3. Lighthouse: install via `yarn add lighthouse` in a scratchpad dir (npx is hook-blocked), `CHROME_PATH=/usr/bin/chromium`, flags `--only-categories=performance --chrome-flags="--headless=new --no-sandbox --disable-gpu"`, plus `--preset=desktop` for the desktop pass.
4. Routes: the 24 slugs of §1 — statics (`/`, `/identity{,/10101}`, `/ego{,/20101}`, `/ego-gift{,/9001}`, `/theme-pack{,/1001}`, `/ab-event{,/901001}`, `/keyword{,/AStrokeOfDeath}`), planner (`/planner`, `/planner/md`, `/planner/md/gesellschaft{,/<live-id>}`, `/planner/md/new`, `/planner/deck`, `/planner/extraction`), misc (`/settings`, `/moderation`, `/privacy`, `/terms`). Fetch `<live-id>` from `GET /api/planner/md/published?page=0&size=1`.
5. Eager-set gate: parse `dist/index.html` for entry + modulepreload hrefs; gzip each chunk; map content via its `.js.map` `sources`; FAIL if any source matches `node_modules/(pako|dompurify|@tiptap)` or `/src/pages/`; FAIL if summed gzip > 320KB.
6. Image attribution when needed: md5-join `dist/a/*.webp` against `static/images/**` (the hash manifest is virtual).

## §7 Move mechanics (binding, from prior decisions)

- `git mv` + import-only edits for every relocation (meme: `decision-file-moves-within-page-slice-migrations`); tests move with their modules.
- Before dropping a barrel export: reverse-import sweep, e.g. `rg -l "@/pages/planner'" frontend/src --glob '!frontend/src/pages/planner/**'` — every hit repoints in the same commit.
- Boundary lint (`no-restricted-imports` deep-path ban) stays enabled throughout; `router.tsx` remains its sole exemption, now with dynamic imports only for slice reaches.

## §8 R7 contracts (binding)

- Router: `defaultPreload: 'intent'` in `src/lib/router.tsx`; rely on query-cache staleTime (5min on published detail) to keep hover-triggered loader runs idempotent. No per-route preload overrides unless a loader proves non-idempotent.
- Deferred sections on plan detail: the deck grid, floor sections, and notes mount deferred behind their skeletons; the header (title, category, metadata) renders in the first commit from loader data. Section order and chrome heights per the §1c stack.
- Skeleton fidelity ledger (per the §1c per-section audit): in `GuideModeViewer.tsx` (and its `TrackerModeViewer` counterparts) — Deck Builder fallback tiles adopt the real deck-card dimensions from constants; Start Buff/Gift `h-32` stubs grow to their sections' measured chrome heights (Start Buff sized from `START_BUFF_CARD_SIZE`); Comprehensive Gift Grid and Floor Theme Gallery text-line fallbacks become shaped skeletons (model them on the faithful EGO Gift Observation / Skill Replacement fallbacks); the route-level inline fallback aligns with the viewer section-stack chrome. `PlannerGridSkeleton` `cardCount` derives from `PLANNER_LIST.PAGE_SIZE` and row math, not a hardcoded 8; home's `RecentlyReleasedSection`/community-plans fallbacks become shaped skeletons (no `LoadingState` as a section fallback); `CARD_PRESETS.planner.height` (160, hardcoded in `ListPageSkeleton.tsx`) is reconciled with the rendered 150px card — the constant and the card component must consume one source (`CARD_GRID.HEIGHT`), whichever value is ruled correct at implementation.
- Geometry drill (verification, extends §6): step 7 — with the preview build seeded per §6, capture skeleton-state and loaded-state container heights on gesellschaft list, both plan details, and home; per-surface delta within ±15% for deterministic surfaces (grids, section chrome); CLS re-measure per INV8.
