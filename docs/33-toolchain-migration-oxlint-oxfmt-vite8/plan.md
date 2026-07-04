# Execution Plan

## Phase Summary

Four sequential, independently-shippable commits/PRs, each landing green before the next starts.
The seams are gates, not headings: INV2 forbids any inter-commit gate weaker than today's
(test → boundary-lint → build); D16 says "no Vite 8, no Vite+"; Phase 4 is conditional on Phase 3's
2-core build gate. Phases 1–2 ship immediate lint/format value with no bundler risk. Phase 3 swaps
the bundler behind a hard entry gate (2-core Docker build proof + manual smoke). Phase 4 consolidates
under `vp` and is reachable only after Phase 3 lands.

Cross-cutting invariants (apply to every phase):
- **INV5**: `tsc -b` + full vitest suite green at every phase boundary; no test removed/weakened.
- **INV4**: zero-width trap-street values in i18n/static survive byte-identically — grep post-commit.
  Phases 1–4 never touch `static/` or i18n JSON; Phase 2 explicitly excludes them from formatting.
- **INV2**: CI gate strength is monotonic. The lint gate flips to `--deny-warnings` only at zero findings.
- **Commit hygiene**: the working tree carries extensive unrelated uncommitted work (`.claude/`,
  `docs/`, `CLAUDE.md`, `static`). NEVER `git add -A`. Stage only each phase's own files. `frontend/src`
  itself is clean at start, so the reformat commit has a valid baseline.
- **Execution rule**: pure-mechanical transforms (Phase 2 `oxfmt`, Phase 4 `vp migrate`) are run
  DIRECTLY, never via a Read→Write code-writer agent — that invites the content drift the
  page-slice-migration playbook forbids for mechanical moves. Only genuine code authoring (Phase 1
  behavior fixes) uses agents, and even then the behavior-changing subset is done tightly with tests.

## Ground truth first

Before triaging Phase 1, reproduce the probe — the 92/22 count is a 2026-07-04 snapshot:
`PATH=<probe>/node_modules/.bin:$PATH oxlint --type-aware --react-plugin --import-plugin --tsconfig frontend/tsconfig.app.json frontend/src`
Work from the live count, not the spec table.

## Phases

### Phase 1: Oxlint adoption (type-aware from day one)
- Files:
  - `frontend/package.json` — add exact-pinned `oxlint` + `oxlint-tsgolint` devDeps; rewrite `lint`
    script to the oxlint invocation; drop `lint:boundary` (unless D7 fallback fires)
  - `frontend/.oxlintrc.json` — new: default categories + react + react-hooks + import + typescript,
    type-aware; boundary `no-restricted-imports` overrides mirroring the four pattern constants;
    `unbound-method` off for test globs
  - `frontend/eslint.config.js` + `frontend/eslint.boundary.config.js` — delete, OR shrink to a
    micro-ESLint config running ONLY `react-refresh` + `react-you-might-not-need-an-effect` if the
    JS-plugin bridge (D7) fails to load them
  - `frontend/src/**` — ~22 production + mechanical test fixes (triage split below)
  - `.github/workflows/ci-frontend.yml` — replace `lint:boundary` step with
    `oxlint --type-aware --deny-warnings` (blocking only at zero findings)
- Tests:
  - Behavior fixes where a rejection path becomes user-visible → add/extend the owning slice's
    `__tests__/` coverage (logout-everywhere, account-delete, `usePlannerSave`, `usePlannerMigration`)
  - INV1 bite-test script (seed one violation per tier, assert oxlint fails, revert) — transcript in PR
- Considerations:
  - **Triage split (advisor-flagged, critical).** Do NOT let a "make lint exit 0" agent blanket-`void`
    everything. Two buckets:
    - **Behavior-changing (per-site judgment + tests, done tightly, NOT a bulk agent):**
      `no-floating-promises` ×10 (logout, **account-delete**, save-critical `usePlannerSave`,
      `usePlannerMigration` ×2, `i18n.ts`, `Header`, image-upload, `PlannerMDEditorContent`,
      `PlannerDetailHeader`) — await / `.catch` / `void`-with-intent PER call site; a delete/save flow
      that should await must await so rejections surface. `react-hooks/exhaustive-deps` ×4
      (`usePlannerSave.ts:404,661,731`, `useSseEngine.ts:126`) — each reviewed individually; a missing
      dep may be an intentional stale-closure choice; document or fix, never blanket-suppress.
      `usePlannerSave` is save-critical: full suite + manual save-flow verification after touching it.
    - **Mechanical (safe to delegate):** test-file `no-unused-vars` ×10, `require-array-sort-compare`
      ×5 (real — numeric `.sort()` without comparator), `no-unsafe-optional-chaining` ×2,
      `no-base-to-string` ×3 tests + ×1 prod (`router.tsx:487`), `no-redundant-type-constituents`
      (`KeywordTypes.ts:23`), `unbound-method` ×1 prod (`use-tiptap-editor.ts:41`), and the singletons
      (`restrict-template-expressions`, `no-meaningless-void-operator` ×2, `no-useless-default-assignment`,
      `unicorn/no-new-array`, `oxc/only-used-in-recursion`).
    - **Disable, not fix:** `unbound-method` in tests ×44 (D6) — off for `**/*.test.*` + `**/__tests__/**`.
  - **INV1 boundary parity (bite-test, mandatory).** Oxlint `no-restricted-imports` uses gitignore-style
    `group` patterns incl. negation. Verify the `!@/shared/noteEditor/**` negation and the `router.tsx`
    full exemption behave identically to the flat-config last-match-wins semantics. Flat-config's four
    tiers → Oxlint `overrides`: all files get `[DEEP_PAGES, DEEP_SHARED]`; `src/shared/**` +
    `src/components/**` add `SINK_PAGES`; `src/lib/**` gets `[SINK_PAGES, SINK_SHARED]`;
    `GlobalLayout.tsx` + `RouteErrorComponent.tsx` released back to `[DEEP_PAGES, DEEP_SHARED]`;
    `src/lib/router.tsx` fully exempt. Seed one violation per tier, assert fail, revert.
  - **D7 plugin gap — RESOLVED (2026-07-04).** The ESLint-compat JS-plugin bridge (`jsPlugins`) loads
    AND runs both `eslint-plugin-react-refresh` and `eslint-plugin-react-you-might-not-need-an-effect`
    end-to-end (verified). No micro-ESLint fallback needed; both legacy eslint configs are deleted.
    **Scope discovery:** with the plugins enabled these surface **113 latent findings** (69 react-refresh
    across 16 files + 44 effect across ~15 files) that Reference A's probe never measured (it ran without
    the bridge) and that were never enforced (the old config crashed on load; zero inline disables exist).
    **Decision (assistant default, then user-corrected):** adopt both plugins in a LOCAL-ONLY advisory
    config (`.oxlintrc.jsplugins.json` + `yarn lint:advisory`, no `--deny-warnings`) — react-refresh with
    `configs.vite` semantics (`allowConstantExport: true`), effect with `configs.recommended`. **The
    advisory step is NOT wired into CI (user directive 2026-07-04: "lint must reject the CI. Remove the
    advisory lint from CI").** CI runs only the blocking `yarn lint` gate. The advisory config remains
    available for IDE/local use, preserving D7 coverage without adding non-blocking noise to CI. The
    blocking `--deny-warnings` gate is scoped to the 92 type-aware/boundary findings (all fixed). INV2
    holds: the CI gate (blocking type-aware + boundary at zero) is strictly ≥ today's boundary-only gate.
  - **D5.** Strict-preset parity is a NON-goal — the old `strictTypeChecked` config never ran. Gate on
    Oxlint defaults; do not re-inflate to four digits of stylistic findings by chasing `pedantic`/`style`.
  - **D12/D13.** Pin exact versions (type-aware is alpha). Fallback if tsgolint hard-*crashes* (not
    findings): ship non-type-aware now, type-aware fast-follow. CI uses `--deny-warnings`, blocking only
    at zero findings.
- Depends on: none
- Verify: `yarn --cwd frontend lint` (oxlint) exits 0; `tsc -b` green; full vitest green; INV1
  bite-test transcript shows all seeded violations caught; CI diff shows the `--deny-warnings` gate.

### Phase 2: Oxfmt one-shot reformat
- Files:
  - `frontend/.oxfmtrc.json` — new: `{ "semi": false, "singleQuote": true }` and nothing else (D8);
    `sortImports`/`sortTailwindcss` OFF (D9); `sortPackageJson` on by default WILL reorder
    `frontend/package.json` (accepted)
  - `frontend/src/**` + frontend root ts/json config files — one-shot reformat (~670/678 files)
  - `.git-blame-ignore-revs` — new at repo root: the reformat commit hash;
    `git config blame.ignoreRevsFile .git-blame-ignore-revs` (document in PR)
  - `.github/workflows/ci-frontend.yml` — add `oxfmt --check` gate
- Tests:
  - INV3a parity harness: build → snapshot `dist/` (file list + content hashes) → reformat → rebuild →
    diff. Must be byte-identical. Keep script in spec dir/scratchpad, NOT committed to src.
  - INV4 trap-street grep clean post-commit.
- Considerations:
  - **RUN OXFMT DIRECTLY — not via a code-writer agent.** A Read→Write agent over 670 files is exactly
    the drift failure mode the mechanical-move rule forbids.
  - **D11.** Exclude `static/` and all i18n JSON (belt-and-braces: no JSON exists under `frontend/src`;
    Prettier-class formatters never rewrite string contents). Oxfmt reads `.gitignore`/`.prettierignore`
    from the invocation dir; scope the path deliberately — quoted globs can silently match nothing.
  - **INV3a guard.** The asset-hash plugin's `transform` hook regex-matches `resolveAsset(...)` call
    sites in `assetPaths.ts`. Residual risk = line-wrapping of long calls. If the dist parity check
    trips, add `assetPaths.ts` to `ignorePatterns` rather than fighting the formatter.
  - **COMMIT AUTHORIZATION.** Phase 2 cannot exist without a real commit (the hash goes in
    `.git-blame-ignore-revs`). Surface this to the user at the Phase 1→2 boundary before creating any
    commit. Stage only frontend format files — never `git add -A`.
- Depends on: Phase 1
- Verify: `oxfmt --check frontend/src` exits 0; dist byte-parity holds (INV3a); trap grep clean (INV4);
  `tsc -b` + vitest still green; CI has the check gate.

### Phase 3: Vite 7 → 8 (Rolldown) — ENTRY-GATED
- Files:
  - `frontend/package.json` — remove `resolutions.vite` pin; `vite` → 8.x + `@vitejs/plugin-react` → v6
    together (D14: v6 requires Vite 8; keep `@vitejs/plugin-react` NOT `plugin-react-oxc` — React
    Compiler needs Babel)
  - `frontend/vite.config.ts` — `build.rollupOptions` → `build.rolldownOptions`; function-form
    `manualChunks` vendor splitter → `output.codeSplitting.groups` (D15)
  - `frontend/vite-plugin-hash-static.ts` — only if Phase 3 revalidation demands it
  - `.github/workflows/ci-frontend.yml` — reference the deadlock issue; keep build gate
- Tests:
  - INV3b dist audit script: every hashed path referenced in chunks exists in `dist/a/`; dynamic-import
    map keys rewrite; `closeBundle` copies/deletes as before. Run post-build.
  - INV6: 2-core build proof (`docker run --cpus=2`) + real branch CI run.
- Considerations:
  - **ENTRY GATE FIRST (D16, blocking).** This repo already retreated from Vite 8 (commit `1c24f5c0`)
    because Rolldown deadlocked 2-core GitHub Actions runners (sveltejs/kit#15554, closed, fix version
    unconfirmed). Prove the build on a 2-core constraint BEFORE any merge. Mitigation ladder if it hangs:
    cap `TOKIO_WORKER_THREADS` → larger CI runner → stay pinned on Vite 7. **Phase 4 is conditional on
    this gate. This gate is NOT agent-self-verifiable — it needs Docker + a real CI run; hand back to
    the user here if it cannot be cleared in-session.**
  - **INV3b plugin revalidation.** Known Rolldown deltas: `renderChunk` receives comment-stripped
    content; chunk internals/codegen differ; parallel hooks run sequentially; confirm `[hash:12]`
    placeholder + `json.stringify: true` parity (loud failures). All five hooks
    (`resolveId`/`load`/`transform`/`renderChunk`/`closeBundle`) still exist.
  - Minifier swap is implicit (esbuild → Oxc JS, Lightning CSS). Escape hatches: `build.minify:'esbuild'`
    / `cssMinify:'esbuild'` (needs esbuild devDep).
  - CJS default-import interop changed — invisible to tsc; run full suite AND manual app smoke (dev +
    preview of prod build, asset-heavy pages).
  - `rollup-plugin-visualizer`: verify under Rolldown or drop (dev-only, not a blocker).
  - Browser targets rise (Chrome 111 / FF 114 / Safari 16.4) — accepted, note in PR.
- Depends on: Phase 2
- Verify: 2-core `vite build` completes (INV6); dist audit clean, no 404s (INV3b); suite green;
  manual smoke of asset-heavy pages passes.

### Phase 4: `vp migrate` — CONDITIONAL on Phase 3 gate
- Files:
  - `frontend/package.json` — vite-plus install; old `vite`/`vitest` deps removed ONLY after rewrites
    confirmed (vite-plus ships them as direct deps)
  - `frontend/vite.config.ts` — Oxlint/Oxfmt config blocks merged in per the migration tool
  - `.github/workflows/ci-frontend.yml` — scripts move to `vp` surface
    (`vp dev/build/check/lint/fmt/test`); `vp run <script>` for remaining package.json scripts
- Tests:
  - Re-run INV1 bite-test after the config merge (rule-set equivalence).
  - Post-migration sequence: `vp install` → `vp check` → `vp test` → `vp build`.
- Considerations:
  - `vp migrate` prereqs: Vite 8+, Vitest 4.1+ (repo on 4.1.2, pass) — so it is UNREACHABLE until
    Phase 3 lands Vite 8. Run non-interactively (`--no-interactive`), treat output as the BEGINNING of
    a review, not an end state.
  - Verify rewrites: `vite` imports → `vite-plus`, `vitest` → `vite-plus/test`
    (`@vitest/browser*` → `vite-plus/test/browser*`). `declare module 'vitest'` augmentations
    intentionally NOT rewritten. Remove old deps only after confirming.
  - `vp test` = built-in vitest; `vp run test` = package.json script — do not confuse in CI.
  - Vite+ is beta v0.2.2, MIT, wraps yarn v1.
- Depends on: Phase 3 (hard gate)
- Verify: `vp check`/`vp test`/`vp build` are the CI surface; INV1 bite-test re-passes; old deps
  removed only after rewrites confirmed; suite green.

## Phase Dependencies
Group A: Phase 1 (independent)
Group B (after A): Phase 2
Group C (after B, entry-gated): Phase 3 — blocking 2-core proof; not agent-self-verifiable
Group D (after C, conditional): Phase 4 — unreachable without Vite 8

## Execution note for this /build run
Plan + status are durable for all four phases. Execute forward to the first gate that cannot be
cleared in-session: Phase 1 fully (with the triage split), pause at the Phase 1→2 boundary for commit
authorization, then Phase 2. Phase 3's entry gate (Docker 2-core + manual smoke) and Phase 4 (beta
tooling + manual review) are handed back to the user at their gates rather than faked by an agent.
