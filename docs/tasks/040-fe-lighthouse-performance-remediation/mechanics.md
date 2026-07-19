# Mechanics — FE Performance Remediation + Measurement Harness

Companion to `requirements.md`. Transcribed from the 2026-07-18 diagnosis session (48-run Lighthouse sweep against the production build of `dev`) plus the 2026-07-19 R8 tooling decision. Sections marked **binding** are contract; §1's tables are the **reference baseline** all budget verifications compare against. Enforced contract rows are in `tests.manifest.json`.

## §1 Measured baseline (reference — regression anchor)

Conditions: `vite build` output served by `vite preview` on :4173, backend proxied via nginx :80, Chromium headless, Lighthouse defaults (mobile = simulated slow-4G; desktop = `--preset=desktop`). `moderation` logged-out; `/planner/md/$id{,/edit}` excluded from the throttled sweep (IndexedDB-dependent).

Mobile (score / FCP / LCP / CLS / KB): worst offenders — gesellschaft 34/9.1/9.6/0.63/1440¹, home 38/9.1/11.2/0.38/1875, ego 56/9.0/**22.1**/0.00/8584, identity 55/9.1/18.4/0.00/4408, theme-pack 56/9.0/13.7/0.00/12069, ab-event 56/9.1/12.5/0.00/**13399**. Every route's FCP is pinned ~9.1s. Desktop: 69–87, FCP uniform 1.6–1.7s. **TBT ≤ 150ms everywhere on both form factors — compute is a non-factor (D1).**

**CORS caveat (discovered 2026-07-18, after the sweep):** the sweep's build carried `.env`'s `VITE_API_BASE_URL=http://localhost` (absolute origin), so browser-context API fetches from the `:4173` preview origin were CORS-blocked and API-backed routes rendered error/empty states. Shell-, font-, and asset-bound findings are unaffected.

**Re-sweep with live data (2026-07-18, backend CORS allowlist extended to `:4173`, stock build) — supersedes §1 for the API-backed routes:**

| Route | Mobile score/FCP/LCP/CLS/KB | Desktop score/LCP | vs tainted row |
|---|---|---|---|
| home | 38 / 9.1s / 11.3s / 0.38 / 1877 | 71 / 2.0s | unchanged — validated |
| gesellschaft | 56 / 9.1s / 9.6s / **0.00** / 1493 | 85 / 1.7s | ¹ **CLS 0.63 was a CORS artifact**; live data shows no footer shift here |
| gesellschaft-detail | 56 / 9.6s / **20.0s** / 0.00 / **4861** | 72 / 3.4s | far heavier than the tainted row (10.7s/1600KB) — live plan data pulls full deck art |
| planner-md | 53 / 9.2s / 9.9s / 0.12 / 1452 | 85 / 1.8s | unchanged |

Consequences: home's 0.38 is the real footer-shift evidence for R5 (D9); gesellschaft-detail joins the browse pages as an LCP-worst route and a primary R3/R7 beneficiary.

## §1b Plan-loading baseline (Playwright, 2026-07-18 — reference)

Functional loading timings, complementary to §1 and NOT comparable to it (unthrottled localhost, warm cache, Playwright Chromium, corrected same-origin build per §6). n=10 reloads after 1 discarded warm-up; TTFB 2–7ms; `content` = first moment the route's content probe passes. **Probe note:** the list probe must match a uuid-shaped card link, NOT `a[href^="/planner/md/"]` — the latter matches the "Create New" nav link and fires on the shell (an earlier run read ~120ms this way; corrected to ~442ms). A first run was under machine-wide OOM; this table is the healthy re-run (detail deltas within ±10%).

Production build (`vite preview`, the reference):

| Target | content p50 / p95 | FCP p50 / p95 | LCP p50 / p95 | DCL p50 |
|---|---|---|---|---|
| `/planner/md` (list) | 442 / 453 ms | 140 / 155 ms | 140 / 155 ms | 74 ms |
| `/planner/md/$id` (local detail) | 449 / 483 ms | 158 / 224 ms | 386 / 560 ms² | 74 ms |
| gesellschaft detail | 482 / 512 ms | 170 / 218 ms | 592 / 623 ms | 83 ms |

`yarn dev` (:5173, dev-server overhead reference, not a production figure): list content 996/1094 ms, local detail 1234/1271 ms, published 1238/1345 ms — uniformly ~2.5× slower (per-module serving + dev-mode React).

² Bimodal on detail routes: LCP flips between the text block and a late card image — the LCP element is unstable there.

## §1c Plan-route profile + skeleton geometry audit + interaction (reference — basis of R7)

Boundary profile, local plan detail, prod build, unthrottled (medians of 6; injected IDB/fetch patches + Resource Timing):

| Milestone | ms |
|---|---|
| DCL (entry evaluated) | 76 |
| IndexedDB opened (route loader's title read) | 82 |
| First API fetch done | 123 |
| All lazy chunks arrived | 180 |
| Plan content visible | 450 |

**Conclusion: all inputs ready by ~180ms; the ~270ms tail (60%) is mount/render/effect work.** This REJECTED the loader-parallel-data lever (D12) — data already loads during routing.

Skeleton geometry audit (desktop 1440px; loaded heights measured via Playwright, skeleton heights from source classes). The plan viewer renders per-section `Suspense` fallbacks (`GuideModeViewer.tsx`, `TrackerModeViewer.tsx`); the route-level inline fallback (~980px) only bridges until the viewer mounts, so fidelity is judged per section (corrected 2026-07-18 after user review — an earlier draft wrongly compared the route-level fallback against the whole page):

| Plan-viewer section | Fallback reserves | Loaded | Verdict |
|---|---|---|---|
| Deck Builder | 12 tiles `w-16 h-20` ≈ ~116px | ~780px (cards 160×290) | inconsistent — tile scale |
| Start Buff | `h-32` stub (128px) | ~250px (card 272×320) | inconsistent |
| Start Gift | `h-32` stub (128px) | ~202px | inconsistent (moderate) |
| EGO Gift Observation | shaped, ~205px | ~250px | faithful — model to copy |
| Skill Replacement | shaped grid, ~350px | ~380px | faithful — model to copy |
| Comprehensive Gift Grid | text-line fallback ~130px | ~571px | inconsistent — no skeleton |
| Floor Theme Gallery | text-line fallback ~85px | ~1,500–2,400px | worst — no skeleton |

| Other surface | Reserves | Loaded | Gap |
|---|---|---|---|
| Gesellschaft list grid | 8 cards ≈ ~336px | 18–20 cards, grid 813px | ~480px under + 10px/card drift (constant 160 vs rendered 150) |
| Home page-level fallback | `LoadingState` ≈ 256px | main 2,116px | ~1,800px under |

Throttled plan routes (CDP: 1.6Mbps / 150ms RTT / 4× CPU, cold, n=3, spread <1%): list FCP ~4.74s content 5.49–5.53s; local detail FCP ~6.03s content 6.81–6.87s; published FCP ~6.19s content 7.09–7.13s. KR locale (same conditions): published content 7.23s — indistinguishable from EN; KOTRA TTF confirmed under Hangul; Pretendard KR-slice capture was truncated (re-audit after R2).

Editor INP (edit route, seeded plan, unthrottled, Event Timing ≥16ms, 87 samples over title + note typing): **p50 24ms, p95 32ms, max 32ms, zero long tasks — healthy at small plan size.** The `JSON.stringify` dirty-check gotcha (O(n), meme `gotcha-json-stringify-based-dirty-state-detection`) stays open for LARGE plans; this does not close it. Deck-card toggle latency unmeasured (pointer-interception in the test; drive the card wrapper by role in the R7/R8 geometry+INP drill).

## §2 Eager-graph inventory (binding targets for R1)

Entry HTML: 1 entry script + 27 `modulepreload` + 1 CSS = **520KB gzip eager**. Offenders that must leave the eager set:

| Chunk (gz) | Content | Pulled in by |
|---|---|---|
| entry, 111.5KB | `pako`, `pages/planner/hooks`, `dompurify` | `GlobalLayout` → `@/pages/planner`+`@/pages/settings` barrels; `router.tsx` static `fetchPublishedPlanner` |
| 91.8KB | `shared/filter`, `shared/assets`, `shared/skill` | planner barrel graph |
| 49.7KB | `pages/egoGift/components` | planner `lib/{floorGiftBucketing,plannerRules,plannerValidation}.ts` → `@/pages/egoGift` |

Lighthouse `unused-javascript`: 98KB of the 113KB entry transfer unused (~87%). Legitimate eager residents (react-dom 57.5, radix 36.2, tanstack 35.1, i18n 19.7, zod 18.2, tailwind-merge/date-fns 59.7, sonner 8.9 KB gz) ≈ 250KB → the 320KB budget has ~70KB headroom. `@tiptap/*` (118KB gz, 105KB unused) rides the `gesellschaft-detail`+`planner-md-new` route chunks (R6 target).

## §3 Image economics (binding targets for R3/R4)

| Page | Images | Bytes | Note |
|---|---|---|---|
| ab-event | 165 | 11.7MB | 130–260KB each; `AbEventCard` has NO `loading="lazy"` |
| theme-pack | 120 | 10.1MB | ~120KB each; `ThemePackCard` has NO lazy |
| ego | 90 | 6.7MB | 200–240KB each; lazy present but Chrome lookahead (1250–2500px) still fetches ~90 |
| planner-md-new / deck-builder | — | 2.2–2.4MB | roster picker renders full EGO art (140–180KB each) |

Thumb contract: webp, ≥ 2× largest rendered slot (INV7), ~10–20KB/file; grid consumers switch, detail pages keep originals (D7). Grid-consumed art dirs (measured): `images/ego/*`, `images/identity/*`, `images/abEvent/*`, `images/themePack/*`, plus roster-picker `images/ego/*`. `images/icon/egoGift` files are already icon-sized (~10KB) — no thumb needed.

## §4 Fonts (binding constraints for R2)

| File (source) | Dist transfer | Loaded on | Constraint |
|---|---|---|---|
| `Pretendard-Regular.woff2` (full) | 748KB | every page | replace with vendored `pretendard-subset.css` slices (D5) |
| S-Core Dream TTF | 306KB | planner pages | **binary untouchable** — license prohibits conversion (D6) |
| KOTRA_BOLD TTF | 57KB | every page | **binary untouchable** — same |

Remove: the hand-written Pretendard `@font-face` in `globals.css` (three-format src). Replace with an import of the vendored `frontend/src/assets/fonts/Pretendard-1.3.9/web/static/pretendard-subset.css` (already unicode-range sliced; vendor-shipped — no generation on our side). `font-display: swap` unchanged (B12).

## §5 Budget ledger (binding)

| Budget | Value | Verified by |
|---|---|---|
| Eager JS (entry + modulepreload) | ≤ 320KB gzip | eager-set gate (§6) |
| Mobile FCP | < 4s, every measured route | harness sweep |
| Mobile LCP | < 5s on browse routes (identity, ego, ego-gift, theme-pack, ab-event, gesellschaft-detail) | harness sweep |
| CLS | < 0.1 on home + gesellschaft | harness sweep |
| Non-planner mobile font transfer | ≤ 200KB | network audit per page |
| Plan-route content p50 | improves vs §1b | Playwright profile |

## §6 Verification harness recipe (binding — this is what R8 productionizes)

1. Build with maps and a same-origin API base (else browser API fetches are CORS-blocked from the preview origin — §1 caveat): `VITE_API_BASE_URL=http://localhost:4173 ROLLDOWN_MAX_BLOCKING_THREADS=512 yarn --cwd frontend vite build --sourcemap`. The `dist/` is a measurement artifact, not deployable.
2. Backend CORS must allow the preview origin. The block is **Spring's allowlist**, not nginx: `CORS_ALLOWED_ORIGINS` (compose env, default `http://localhost,http://localhost:5173`) must include `http://localhost:4173`. Extend it in the root `.env` (durable) — a shell-level override on `docker compose up` reverts on the next plain up.
3. Serve: `yarn --cwd frontend preview --port 4173` (backend up; nginx :80 proxies `/api`).
4. Lighthouse: install via `yarn add lighthouse` in a scratchpad/`scripts/perf` dir (npx is hook-blocked), `CHROME_PATH=/usr/bin/chromium`, flags `--only-categories=performance --chrome-flags="--headless=new --no-sandbox --disable-gpu"`, `--preset=desktop` for desktop.
5. Routes: the 24 slugs of §1. Fetch a live gesellschaft id from `GET /api/planner/md/published?page=0&size=1`. Seed a local plan via the UI (`/planner/md/new` → fill title → Save) for `/planner/md/$id{,/edit}`.
6. Eager-set gate: parse `dist/index.html` for entry + modulepreload hrefs; gzip each; map content via its `.js.map` `sources`; FAIL if any source matches `node_modules/(pako|dompurify|@tiptap)` or `/src/pages/`; FAIL if summed gzip > 320KB.
7. Geometry drill: at 1440px, capture skeleton-state and loaded-state container heights on gesellschaft list, both plan details, and home; per-surface delta within ±15% for deterministic surfaces; CLS re-measure per INV8.
8. Image attribution: md5-join `dist/a/*.webp` against `static/images/**` (the hash manifest is virtual).
9. Cleanup: kill preview via `pkill -f "frontend/node_modules/.bin/vite"`; verify port free. The `| head`-style pipe closes stdin and kills a backgrounded preview — run bare.

## §7 Move mechanics (binding, from prior decisions)

- `git mv` + import-only edits for every relocation (meme `decision-file-moves-within-page-slice-migrations`); tests move with their modules.
- Before dropping a barrel export: reverse-import sweep (`rg -l "@/pages/planner'" frontend/src --glob '!frontend/src/pages/planner/**'`); every hit repoints same commit.
- Boundary lint (`no-restricted-imports` deep-path ban) stays enabled; `router.tsx` remains its sole exemption, now with dynamic imports only for slice reaches.

## §8 R7 contracts (binding)

- Router: `defaultPreload: 'intent'` in `src/lib/router.tsx`; query-cache staleTime (5min on published detail) keeps hover-triggered loader runs idempotent. No per-route override unless a loader proves non-idempotent.
- Deferred sections: deck grid, floor sections, and notes mount deferred behind their skeletons; the header renders in the first commit from loader data. Section order/chrome per §1c.
- Skeleton fidelity ledger (per the §1c per-section audit): in `GuideModeViewer.tsx`/`TrackerModeViewer.tsx` — Deck Builder fallback tiles adopt real deck-card dimensions from constants; Start Buff/Gift `h-32` stubs grow to their sections' chrome (Start Buff from `START_BUFF_CARD_SIZE`); Comprehensive Gift Grid + Floor Theme Gallery text-line fallbacks become shaped skeletons (model on the faithful Observation/Skill-Replacement fallbacks); the route-level inline fallback aligns with the viewer section-stack chrome. `PlannerGridSkeleton` `cardCount` derives from `PLANNER_LIST.PAGE_SIZE` and row math, not a hardcoded 8; home's `RecentlyReleasedSection`/community fallbacks become shaped skeletons (no `LoadingState` as a section fallback); `CARD_PRESETS.planner.height` (160, in `ListPageSkeleton.tsx`) reconciles with the rendered 150px card — constant and component consume one source (`CARD_GRID.HEIGHT`), whichever value is ruled correct at implementation.

## §9 R8 harness contracts (binding)

Home: `scripts/perf/` (mirrors `scripts/ops/`), self-contained `scripts/perf/package.json` (its own `node_modules`; pins `lighthouse` + `@playwright/test`; NOT added to `frontend/package.json` — INV12). Chromium via `CHROME_PATH=/usr/bin/chromium`.

Suite (each script codifies a §6 step; the scratchpad originals are the reference implementations):

| Script | Codifies | Emits |
|---|---|---|
| `sweep.mjs` | §6 steps 1–5, full-route Lighthouse | per-route JSON + score/FCP/LCP/CLS/KB table |
| `eager-gate.mjs` | §6 step 6 | pass/fail + eager KB + offender list; **exit non-zero on breach** (the INV1/INV3 gate) |
| `profile.mjs` | §1c boundary profile | milestone medians |
| `geometry.mjs` | §6 step 7 | skeleton-vs-loaded deltas + CLS |
| `inp.mjs` | §1c interaction | INP p50/p95 + long tasks |
| `lib/` | shared: preview lifecycle, seed-a-plan, CDP throttle presets, percentile math | — |
| `baselines.json` | the §1/§1b/§1c archived numbers | the regression anchor |
| `selftest.mjs` | run vs current build, diff against `baselines.json` | pass within tolerance (INV11): Lighthouse ±1 mobile score / ±0.5s FCP; plan content p50 ±15% |

Contracts: (1) the suite drives a caller-provided base URL (default `:4173`), never hardcodes a plan/gesellschaft id — it seeds a local plan and fetches a live published id at runtime (§6 step 5). (2) `eager-gate.mjs` is the one script wired into `/build` verification as a hard gate; the rest are measurement/report. (3) No CI workflow (D16). (4) Percentiles per the measurement discipline — report p50/p95, never averages; n=10 makes p95 indicative, p99 meaningless. (5) The suite is a dev/measurement tool: it must not be imported by app code and adds nothing to the shipped bundle.
