# Task: Frontend Toolchain Migration — Oxlint (type-aware) → Oxfmt → Vite 8/Rolldown → Vite+

> **Audience note**: this spec is written context-maximal on purpose — it will be executed by a
> fresh session with no access to the brainstorm conversation. The Reference sections at the bottom
> contain measured probe data, reproduction commands, and upstream sources. Trust the measurements
> here over stale memory entries (see "Superseded knowledge").

## Decisions

- **D1. Staged adoption, not one-shot `vp migrate`** — Vite+'s migration prerequisite is Vite 8+ and
  Vitest 4.1+; the repo is on Vitest 4.1.2 (pass) but Vite is hard-pinned to 7.2.2 (fail). Oxlint and
  Oxfmt are standalone MIT tools consumable without Vite+, so the lint/format value ships immediately
  while the bundler swap is de-risked separately. (evidence: `frontend/package.json` `resolutions`;
  https://viteplus.dev/guide/migrate)
- **D2. Phase order lint → format → bundler → vp** — the one-shot reformat churns every file; doing it
  *before* the Vite 8 swap means Phase 3's dist-parity diffs are attributable to Rolldown alone, never
  to formatting noise. Lint-before-format follows the project lesson: apply strict formatting on
  already-cleaned code. (evidence: brainstorm consensus; global lesson "adopt linter/formatter after
  cleanup sweeps")
- **D3. SSR/R2 integration abandoned** — the `feat-r2-infra-deploy-workflows` worktree branch (which
  already carried Vite 8) is dropped; it diverged too much. The Vite 7→8 migration is therefore
  self-owned in Phase 3 on `dev`. (evidence: user decision, 2026-07-04)
- **D4 (taste). Type-aware linting from day one** — lint gates exist to surface real defects; adopting
  a gate that hides a known-real bug class (10 unawaited promises in logout/account-delete/planner-save
  flows) defeats the purpose. Prefer depth from day one whenever the measured cost is small. Measured
  cost here: 92 findings total, only 22 in production code — not the feared ~1,397. (evidence: user
  override of the assistant's shallower recommendation; probe data in Reference A)
- **D5 (default). Strict-preset parity is a non-goal** — the old `strictTypeChecked` +
  `stylisticTypeChecked` aspiration never actually ran (config crashed without `projectService`), so it
  is not a baseline being regressed from. Gate on Oxlint's default type-aware categories; promote
  individual `pedantic`/`style` rules later, deliberately, one at a time. Chasing wholesale parity
  re-inflates the debt to four digits of mostly-stylistic findings.
- **D6 (default). `unbound-method` disabled in test files, enabled in `src`** — 44 of 45 hits are the
  `expect(mock.method)` vitest idiom, a canonical false positive; typescript-eslint's own guidance is to
  disable it for tests. The 1 production hit (`use-tiptap-editor.ts:41`) is real and gets fixed.
- **D7. Plugin gap: JS-plugin bridge first, micro-ESLint fallback** — `eslint-plugin-react-refresh` and
  `eslint-plugin-react-you-might-not-need-an-effect` are not in Oxlint's native rule set. Try Oxlint's
  ESLint-compat JS-plugin support first; if either fails to load, keep a minimal ESLint config running
  only those two plugins (no type-aware extends) until Oxlint covers them. (evidence: user answer)
- **D8. Oxfmt with exactly two deviations from defaults: `semi: false`, `singleQuote: true`** — both
  match measured house style (semicolon-free codebase; 2,969 single-quote vs 92 double-quote imports).
  Every other option stays at Oxfmt defaults (printWidth 100, tabWidth 2, trailingComma "all",
  jsx double quotes, sortPackageJson on). Minimizing the reformat diff *is* the selection criterion.
  (evidence: measurements in Reference B)
- **D9 (default). `sortImports` and `sortTailwindcss` stay OFF in the initial adoption** — import
  reordering can move side-effect imports and interacts with boundary conventions; Tailwind class
  sorting is cosmetic (tailwind-merge resolves conflicts at runtime). One change class at a time;
  either can be enabled later as its own commit.
- **D10. One-shot reformat + `.git-blame-ignore-revs`** — format all of `frontend/src` (plus frontend
  root ts/json config files) in a single commit recorded in the blame-ignore file; CI enforces
  `oxfmt --check` from then on. (evidence: user answer)
- **D11. `static/` and all i18n JSON are excluded from formatting** — i18n strings carry zero-width
  Unicode trap-street values that must survive byte-identically. Note: Prettier-class formatters never
  rewrite string *contents*, and no JSON exists under `frontend/src` at all, so this is
  belt-and-braces. (evidence: project trap-street convention; `find frontend/src -name '*.json'` → empty)
- **D12. Pin `oxlint` + `oxlint-tsgolint` exact versions** — type-aware mode is alpha (announced
  2026-07-02). Pinning makes upgrades deliberate. Fallback if tsgolint hard-crashes on the codebase
  (crash, not findings): ship non-type-aware now, type-aware as fast-follow. Probe proved 1.72.0 works
  today. (evidence: https://voidzero.dev/posts/announcing-oxlint-type-aware-linting-alpha; probe)
- **D13. CI lint gate uses `--deny-warnings` and flips to blocking only at zero findings** — oxlint
  exits 0 on warnings by default; never gate CI on a red baseline.
- **D14. `@vitejs/plugin-react` v6, NOT `plugin-react-oxc`** — React Compiler
  (`babel-plugin-react-compiler`) requires Babel; the oxc variant has no Babel pipeline. v5 was pinned
  alongside Vite 7 (v6 requires Vite 8), so both upgrade in the same Phase 3 commit. (evidence: pin
  commit `1c24f5c0`)
- **D15. `manualChunks` (function form) → `rolldownOptions.output.codeSplitting.groups`** — object form
  is removed in Vite 8, function form deprecated. The pin commit deliberately kept function form "for
  forward compatibility"; convert during Phase 3. (evidence: https://vite.dev/guide/migration)
- **D16. Phase 3 entry gate: prove the build on a 2-core environment before merging** — this repo
  already tried Vite 8 in March 2026 and retreated because Rolldown deadlocked 2-core GitHub Actions
  runners (commit `1c24f5c0`, upstream sveltejs/kit#15554 — now closed, fix version unconfirmed).
  Mitigation ladder if it still hangs: cap Tokio threads (`TOKIO_WORKER_THREADS`) → larger CI runner →
  stay pinned on Vite 7. **Phase 4 is conditional on this gate**: no Vite 8, no Vite+.
- **D17. Vite+ adopted as Phase 4 via `vp migrate`** — acknowledged that the `vp` shell's headline
  features (monorepo task caching, package-manager wrapping) add little for a single-package yarn-v1
  repo today; the user wants the consolidation. Vite+ is MIT, beta v0.2.2, wraps yarn rather than
  replacing it. (evidence: user request; https://github.com/voidzero-dev/vite-plus)

## Description

Migrate the frontend toolchain in four sequential phases, each landing green before the next starts.
The end state: one linter (Oxlint, type-aware), one formatter (Oxfmt), Vite 8 on Rolldown, all fronted
by the `vite-plus` (`vp`) unified CLI, with CI gates at every step at least as strong as today's.

Today's baseline (main tree, branch `dev`):
- `frontend/` is a standalone package (no root package.json, no workspaces), yarn **Classic v1**, Node 24 in CI.
- vite 7.2.2 (pinned via `resolutions`), vitest 4.1.2, typescript ~6.0.2, eslint 10 + typescript-eslint 8.58.
- **`yarn lint` has never worked**: `eslint.config.js` extends `strictTypeChecked` without
  `parserOptions.projectService`, so type-aware rules crash at load. A second config
  (`eslint.boundary.config.js`, run as `yarn lint:boundary`) exists solely as the CI-safe boundary gate.
- **No formatter exists anywhere** (no prettier/biome/dprint/editorconfig).
- CI (`.github/workflows/ci-frontend.yml`): `yarn test` (vitest) → `yarn lint:boundary` → `yarn build`
  (`tsc -b && vite build`, NODE_OPTIONS max-old-space-size=6144).

### Phase 1 — Oxlint adoption (type-aware from day one)

1. Add exact-pinned `oxlint` + `oxlint-tsgolint` devDeps (probe validated 1.72.0).
2. Create the Oxlint config translating the current setup:
   - Plugins/presets: default categories + react + react-hooks + import + typescript, `--type-aware`.
   - **Boundary rules** (`no-restricted-imports`) with the same four pattern constants and per-glob
     overrides as `eslint.config.js` (flat-config last-match-wins → Oxlint `overrides`):
     `DEEP_PAGES` bans `@/pages/*/**`; `DEEP_SHARED` bans `@/shared/*/**` except `!@/shared/noteEditor/**`;
     `SINK_PAGES` bans `@/pages/*` + deep; `SINK_SHARED` same for shared. All files get
     `[DEEP_PAGES, DEEP_SHARED]`; `src/shared/**` + `src/components/**` add `SINK_PAGES`; `src/lib/**`
     gets `[SINK_PAGES, SINK_SHARED]`; `GlobalLayout.tsx`/`RouteErrorComponent.tsx` released to
     `[DEEP_PAGES, DEEP_SHARED]`; `src/lib/router.tsx` fully exempt. Oxlint's rule supports gitignore-style
     `group` patterns incl. negation — verify the noteEditor negation behaves identically (bite-test, INV1).
   - Override: `unbound-method` off for `**/*.test.*` + `**/__tests__/**` (D6).
3. Attempt the JS-plugin bridge for the two uncovered plugins (D7); on failure, shrink
   `eslint.config.js` to a micro-config with only those two plugins (non-type-aware) and keep it as a
   separate CI step; otherwise delete both legacy ESLint configs and the now-redundant
   `lint:boundary` script.
4. Fix the 92 probe findings per this triage (full locations in Reference A):
   - **Fix, production, high value**: `no-floating-promises` ×10 — every one is an unawaited promise in
     user-facing async flows (`LogoutEverywhereSection`, `AccountDeleteSection`, `usePlannerSave`,
     `usePlannerMigration` ×2, `i18n.ts`, `Header`, image-upload node, `PlannerMDEditorContent`,
     `PlannerDetailHeader`). Await, `.catch`, or explicit `void` with intent — per call site.
   - **Fix with care, behavior-changing**: `react-hooks/exhaustive-deps` ×4 (`usePlannerSave.ts:404,661,731`,
     `useSseEngine.ts:126`). Each reviewed individually — a missing dep may be an intentional
     stale-closure choice; document or fix, never blanket-suppress. usePlannerSave is save-critical:
     full suite + manual save-flow verification after touching it.
   - **Fix, production hygiene**: `unbound-method` ×1 (`use-tiptap-editor.ts:41`), `no-base-to-string` ×1
     (`router.tsx:487`), `no-redundant-type-constituents` (`KeywordTypes.ts:23`), plus singletons
     (`restrict-template-expressions`, `no-meaningless-void-operator` ×2, `no-useless-default-assignment`,
     `unicorn/no-new-array`, `oxc/only-used-in-recursion`).
   - **Fix, tests, mechanical**: `no-unused-vars` ×10, `require-array-sort-compare` ×5 (real — numeric
     `.sort()` without comparator can make assertions pass wrongly), `no-unsafe-optional-chaining` ×2,
     `no-base-to-string` ×3, `no-redundant-type-constituents` in tests.
   - **Disable, not fix**: `unbound-method` in tests ×44 (D6).
5. Rewire CI: replace the `yarn lint:boundary` step with `oxlint --type-aware --deny-warnings`
   (plus the micro-ESLint step if D7's fallback fired). `yarn lint` script becomes the oxlint invocation.

### Phase 2 — Oxfmt one-shot reformat

1. Add pinned `oxfmt` devDep (probe validated 0.57.0). Create `frontend/.oxfmtrc.json`:
   `{ "semi": false, "singleQuote": true }` (D8) — nothing else. `sortImports`/`sortTailwindcss` remain
   off (D9); note `sortPackageJson` is on by default and will reorder `frontend/package.json` fields in
   this commit (accepted).
2. Ignore rules: exclude `static/` and any i18n JSON (D11). No JSON exists under `frontend/src`.
3. One-shot format of `frontend/src` + frontend root ts/json config files. Expect ~670/678 files
   touched; per-file churn is wrapping/trailing-comma only.
4. Record the reformat commit hash in a new root `.git-blame-ignore-revs`; configure
   `git config blame.ignoreRevsFile .git-blame-ignore-revs` (document in the PR).
5. Add `oxfmt --check` to CI.
6. **Guard**: the asset-hash plugin's `transform` hook regex-matches `resolveAsset(...)` call sites in
   `assetPaths.ts`. With quotes/semis matching house style the residual risk is line-wrapping of long
   calls. Run the dist byte-parity check (INV3a); if it trips, add `assetPaths.ts` to
   `ignorePatterns` instead of fighting the formatter.

### Phase 3 — Vite 7 → 8 (Rolldown) on `dev`

**Entry gate first (D16)**: build the current branch with Vite 8 under a 2-core constraint
(`docker run --cpus=2` or equivalent) and then a real CI run on a branch — before any merge.

1. Remove the `resolutions` pin; upgrade `vite` to 8.x and `@vitejs/plugin-react` to v6 together (D14).
2. Rename `build.rollupOptions` → `build.rolldownOptions`; convert the function-form `manualChunks`
   vendor splitter (react-vendor/tanstack/radix/i18n/icons/zod/sonner/tiptap) to
   `codeSplitting.groups` (D15); verify chunk composition is equivalent.
3. Revalidate `vite-plugin-hash-static.ts` end-to-end (INV3b). Known Rolldown deltas: `renderChunk`
   receives comment-stripped content; chunk internals/codegen differ; parallel hooks run sequentially;
   `[hash:12]` placeholder support and `json.stringify: true` parity must be confirmed (loud failures).
   The plugin's five hooks (`resolveId`/`load`/`transform`/`renderChunk`/`closeBundle`) all still exist.
4. Minifier swap lands implicitly (esbuild → Oxc for JS, Lightning CSS for CSS). Escape hatches if
   misbehavior: `build.minify: 'esbuild'` / `cssMinify: 'esbuild'` (requires esbuild devDep).
5. CJS default-import interop changed in Vite 8 — invisible to tsc; run the full suite AND a manual
   app smoke (dev + preview of the production build, exercising asset-heavy pages).
6. `rollup-plugin-visualizer`: verify under Rolldown; replace with a Rolldown-native analyzer or drop
   (dev-only tool, not a blocker).
7. Browser targets rise to Chrome 111/FF 114/Safari 16.4 defaults — accepted, note in PR.

### Phase 4 — `vp migrate` (conditional on Phase 3 gate)

1. Install `vite-plus`; run `vp migrate` on a branch, non-interactively, then treat the result as the
   *beginning of a review*, not an end state (per the guide).
2. Verify: `vite` imports rewritten → `vite-plus`, `vitest` imports → `vite-plus/test`
   (`@vitest/browser*` → `vite-plus/test/browser*`); `declare module 'vitest'` augmentations are
   intentionally NOT rewritten. Only after confirming rewrites, remove the old `vite`/`vitest` deps
   (vite-plus ships them as direct deps).
3. Oxlint/Oxfmt configs from Phases 1–2 merge into `vite.config.ts` blocks per the migration tool;
   verify rule-set equivalence (re-run the INV1 bite-test).
4. Scripts move to the `vp` command surface (`vp dev/build/check/lint/fmt/test`); CI workflows rewired
   accordingly. `vp run <script>` covers remaining package.json scripts (e.g. sitemap generation).
5. Post-migration sequence per guide: `vp install` → `vp check` → `vp test` → `vp build`.

**Not applicable**: docs/spec.md's Data-Driven Features sections (Data Model Catalog, Normalization
Layer, Rendering Modes) — this task consumes no raw game data.

## Scope

Read for context before implementing:
- `frontend/package.json` — scripts, versions, the `resolutions` pin
- `frontend/eslint.config.js` + `frontend/eslint.boundary.config.js` — the boundary pattern constants
  and per-glob overrides to translate faithfully
- `frontend/vite.config.ts` + `frontend/vite-plugin-hash-static.ts` — plugin hook usage, manualChunks,
  `a/[hash:12]` filename scheme, virtual module
- `frontend/tsconfig.app.json` — test exclusion (`src/**/*.test.*`, `src/**/__tests__/**`)
- `frontend/vitest.setup.ts` + `test` block in vite.config.ts
- `.github/workflows/ci-frontend.yml` — current gates
- `.claude/rules/frontend/architecture/page-slice-migration.md` — boundary model rationale
- `git show 1c24f5c0` — the Vite pin origin (rolldown CI deadlock)
- `static/CLAUDE.md` — before touching anything under static (should not be needed; fmt excludes it)

## Target

Create:
- `frontend/.oxlintrc.json` (or oxlint's current config format)
- `frontend/.oxfmtrc.json`
- `.git-blame-ignore-revs` (repo root)

Modify:
- `frontend/package.json` + `frontend/yarn.lock` (deps per phase)
- `frontend/eslint.config.js` / `frontend/eslint.boundary.config.js` — delete, or shrink to micro-ESLint (D7)
- `.github/workflows/ci-frontend.yml` — gates per phase
- `frontend/src/**` — Phase 1 lint fixes (~40 sites), Phase 2 reformat (~670 files)
- `frontend/vite.config.ts` — Phase 3 (rolldownOptions, codeSplitting) and Phase 4 (vp config blocks)
- `frontend/vite-plugin-hash-static.ts` — only if Phase 3 revalidation demands it

## Invariants

- **INV1 (boundary parity)**: every import banned by the ESLint boundary config is banned by the Oxlint
  config, including the `!@/shared/noteEditor/**` negation and the router.tsx exemption — test:
  bite-test; seed one violation per tier (deep-pages, deep-shared, sink-pages from components, sink from
  lib), assert oxlint fails on each, revert. Re-run after Phase 4's config merge.
- **INV2 (gate strength)**: at no commit between phases is any CI gate weaker than today's
  (test → boundary-lint → build) — test: CI config review per phase PR; lint gate flips to
  `--deny-warnings` only at zero findings.
- **INV3a (reformat is behavior-free)**: production build output (emitted `dist` file set and hashed
  asset resolution) is byte-identical across the Phase 2 reformat commit — test: build before/after,
  diff `dist/` trees.
- **INV3b (bundler swap preserves the asset pipeline)**: after Phase 3, every `resolveAsset` call site
  resolves to an existing `/a/<hash>` file, dynamic-import map keys rewrite correctly, and `closeBundle`
  copies/deletes as before — test: dist audit script + manual smoke of asset-heavy pages; hashes may
  differ from Vite 7 (different bundler), but internal consistency must be total (no 404s).
- **INV4 (trap streets survive)**: zero-width Unicode trap-street values in i18n/static are
  byte-identical after every phase — test: grep the known trap values post-commit.
- **INV5 (suite green per phase)**: `tsc -b` and the full vitest suite pass at every phase boundary —
  test: existing suite, no test removed or weakened.
- **INV6 (2-core CI viability)**: Phase 3's `vite build` completes on a 2-core runner — test: the
  entry-gate reproduction (D16) plus the real branch CI run.

## Done When

- [ ] Phase 1: `yarn lint` (oxlint, type-aware) runs to completion and exits 0 — first working lint in
      this repo's history; all 92 triaged findings fixed or deliberately overridden per D6
- [ ] Phase 1: bite-test transcript in the PR shows all seeded boundary violations caught (INV1)
- [ ] Phase 1: CI has a blocking `--deny-warnings` lint gate replacing `lint:boundary`
- [ ] Phase 2: one-shot reformat commit landed; its hash is in `.git-blame-ignore-revs`; `oxfmt --check`
      gates CI; dist byte-parity verified (INV3a)
- [ ] Phase 3: Vite 8 build passes on 2-core CI (INV6); asset pipeline audit clean (INV3b);
      `resolutions` pin removed with the deadlock issue referenced in the commit message
- [ ] Phase 4: `vp check`/`vp test`/`vp build` are the CI surface; old `vite`/`vitest` deps removed only
      after import rewrites confirmed; INV1 bite-test re-passed after config merge
- [ ] All existing tests pass at every phase boundary (INV5); no test weakened
- [ ] Trap-street grep clean after Phases 2–4 (INV4)

## Test Plan

### Test Runner
- Framework: vitest (frontend), per CLAUDE.md
- Run commands (project conventions: `--cwd`, never `cd`; redirect to /tmp logs):
  - `yarn --cwd frontend test > /tmp/fe-test-<session-id>-<phase>.log`
  - `yarn --cwd frontend typecheck > /tmp/fe-typecheck-<session-id>-<phase>.log`
  - `yarn --cwd frontend build > /tmp/fe-build-<session-id>-<phase>.log`

### Tests to Write
- [ ] INV1 bite-test: scripted seed-violation/assert-fail/revert procedure (throwaway script or
      documented transcript in the PR; per-tier seeds listed in INV1) — run in Phases 1 and 4
- [ ] INV3a parity harness: script that builds, snapshots `dist/` (file list + content hashes), rebuilds
      after the reformat commit, diffs — keep in the spec dir or scratchpad, not committed to src
- [ ] INV3b dist audit: script verifying every emitted hashed path referenced in chunks exists in
      `dist/a/`, run post-Phase-3
- [ ] INV4 trap-street grep: known zero-width values found intact post-format and post-migrate
- [ ] Behavior fixes from Phase 1 (floating promises in `usePlannerSave`/`usePlannerMigration`,
      logout/delete flows): where a promise rejection path becomes user-visible, add/extend the
      slice's existing `__tests__/` coverage (tests live in `__tests__/` subdirectories per project
      convention)
- [ ] Every invariant above is realized by one of these checks — no invariant ships unverified

---

## Reference A — Lint probe (measured 2026-07-04, oxlint 1.72.0 + oxlint-tsgolint, repo untouched)

Command (note both gotchas: tsgolint must be discoverable via PATH from the invocation directory, and
type-aware needs the tsconfig):

```
PATH=<probe>/node_modules/.bin:$PATH oxlint --type-aware --react-plugin --import-plugin \
  --tsconfig frontend/tsconfig.app.json frontend/src
```

Totals: **92 findings (70 in test files, 22 in production)**. Non-type-aware baseline alone: 18.
tsgolint DOES lint files excluded from tsconfig (infers a default project), so tests get type-aware
coverage despite the exclusion — no tsconfig change needed.

| Rule | Count | Notes |
|---|---|---|
| typescript/unbound-method | 45 | 44 tests (disable there), 1 prod: `use-tiptap-editor.ts:41` |
| typescript/no-floating-promises | 10 | ALL prod — see file list in Phase 1 step 4 |
| eslint/no-unused-vars | 10 | all tests, mechanical |
| typescript/no-redundant-type-constituents | 6 | `KeywordTypes.ts:23` (×3), deckFilter/floorGiftBucketing tests |
| typescript/require-array-sort-compare | 5 | egoGiftEncoding.test.ts:223,234,242; floorGiftBucketing.test.ts:163,173 |
| typescript/no-base-to-string | 4 | prod: `router.tsx:487`; tests: TrackerModeViewer:28, ColoredText:57, IdentityDetailPage:216 |
| react-hooks/exhaustive-deps | 4 | `usePlannerSave.ts:404,661,731`, `useSseEngine.ts:126` — case-by-case |
| eslint/no-unsafe-optional-chaining | 2 | plannerValidation.test.ts:335,489 |
| typescript/no-meaningless-void-operator | 2 | — |
| others ×1 each | 4 | restrict-template-expressions, no-useless-default-assignment, unicorn/no-new-array, oxc/only-used-in-recursion |

Why not ~1,397: that number came from typescript-eslint `strictTypeChecked`+`stylisticTypeChecked`
(never-running config, measured in a prior session via `projectService`). Oxlint ships those rules but
files most under `pedantic`/`style`, off by default. See D5.

## Reference B — Formatter probe (oxfmt 0.57.0)

- `oxfmt --check frontend/src` under pure defaults: **670/678 files** differ.
- Sample `usePlannerSave.ts`: 822 changed diff lines under defaults → **503** with
  `{"semi": false, "singleQuote": true}`; residual churn is >100-char wrapping + trailing commas only.
- Style census: 2,969 single-quote vs 92 double-quote imports; JSX attributes 123 double vs 0 single;
  850/88,704 lines exceed 100 chars (327 exceed 120) — printWidth 100 (default) kept.
- Oxfmt passes 100% of Prettier's JS/TS conformance tests; formats JSON/YAML/CSS/MD too — scope the
  invocation deliberately. `--check` and `--list-different` are the read-only modes; bare `oxfmt`
  writes. It reads `.gitignore`/`.prettierignore` from the invocation directory; passing a directory
  path works, quoted globs can silently match nothing.
- `oxfmt --migrate=prettier|biome` exists but is irrelevant here (no formatter config to migrate).

## Reference C — Vite 8/Rolldown deltas that touch this repo

Pin origin — commit `1c24f5c0` (2026-03-31): "Vite 8 uses rolldown (Rust bundler) which deadlocks on
2-core GitHub Actions runners (sveltejs/kit#15554). Pin Vite to ^7.2.2 and @vitejs/plugin-react to
^5.1.0 (v6 requires vite/internal export only in Vite 8). manualChunks function form kept for forward
compatibility." Upstream issue closed; fixing version unconfirmed — hence gate D16.

From the official migration guide (https://vite.dev/guide/migration):
- `rollupOptions` → `rolldownOptions` (deprecated alias); `manualChunks` object form REMOVED, function
  form deprecated → `output.codeSplitting` (groups)
- Minify: JS esbuild → Oxc (no property mangling); CSS → Lightning CSS; escape hatches
  `build.minify:'esbuild'`, `cssMinify:'esbuild'`
- `renderChunk` receives comment-stripped content; parallel hooks sequential; removed hooks
  (`shouldTransformCachedModule`, `resolveImportMeta`, `renderDynamicImport`, `resolveFileUrl`) — none
  used by our plugin
- Transforms: esbuild → Oxc (`esbuild` option → `oxc`); `transformWithEsbuild` users need esbuild as
  explicit dep (we have none)
- CJS default-import interop rule change (runtime-visible only)
- `build.target` defaults rise to Chrome 111 / Edge 111 / FF 114 / Safari 16.4
- Dev server risk low (build-side deadlock was the failure); full-bundle dev mode not required

## Reference D — Vite+ facts (as of 2026-07)

- MIT, open source, **beta v0.2.2**; wraps the existing package manager (yarn v1 fine) via
  `packageManager`/lockfile detection; bundles Vite 8, Vitest 4.1, Oxlint, Oxfmt, Rolldown, tsdown.
- `vp migrate` prereqs: Vite 8+, Vitest 4.1+. Post-migrate: `vp install` → `vp check` → `vp test` →
  `vp build`. Flags: `--no-interactive`, `--no-hooks`, `--no-agent`, `--no-editor`.
- Import rewrites per Phase 4 step 2; `declare module` augmentations intentionally not rewritten.
- Replaces lint-staged with a `staged` block in vite.config.ts (we use neither — N/A). Git-hook
  auto-migration is husky-v9-only (we have none — N/A).
- `vp test` = built-in vitest; `vp run test` = package.json script — don't confuse in CI.
- Sources: https://viteplus.dev/guide/migrate · https://viteplus.dev/guide/ ·
  https://github.com/voidzero-dev/vite-plus ·
  https://voidzero.dev/posts/announcing-oxlint-type-aware-linting-alpha ·
  https://oxc.rs/docs/guide/usage/linter/rules/eslint/no-restricted-imports ·
  https://oxc.rs/docs/guide/usage/formatter

## Superseded knowledge

Memory/doc entries stating "FE lint debt = ~1,397 errors requiring a large cleanup"
(`todo-frontend-yarn-lint-never-runs-to`, `arch-fe-yarn-lint-is-broken-repo`) are superseded by
Reference A: under the adopted Oxlint default type-aware gate the debt is 92 findings (22 prod). The
"boundary via throwaway eslint config" workaround those entries describe is retired by Phase 1. The
`feat-r2-infra-deploy-workflows` worktree (SSR/Vite 8) is abandoned per D3 — do not mine it for the
Phase 3 config.
