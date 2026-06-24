# Harness Changelog

## 2026-06-24 — Planner-core Refactor (4-phase + review)

**Graduated:**
- New tactic → root `CLAUDE.md` Code Review section: **behavior-preserving refactors — diff findings against the original** via `git show HEAD:<path>` to classify regression-vs-preserved. Earned this session: 3 confident reviewer "High"s (difficulty-validation absence, two import-order disorders) all resolved to 0 production edits once checked against the deleted/original source. Extends the existing `feedback_filter_reviewer_findings` discipline with the specific oracle for moves/renames/splits.

**Evolved:**
- None beyond the graduation. No new feedback corrections from the user this session (interaction was `/build` → `/review` → `/wrap` command-driven; spec was fully decided).

**Validated:**
- `reference_code_review_orchestrator_overflow` + CLAUDE.md large-diff rule: 15-file/5151-LOC diff → skipped the orchestrator, ran 3 focused reviewers (architecture/reliability/consistency) in parallel; no overflow, multi-dimensional coverage. Rule confirmed working.
- `feedback_filter_reviewer_findings`: every finding got a Fix/Skip/Confirmation verdict; pre-existing import disorder skipped under surgical-precision rather than auto-fixed.
- `feedback_no_todo_human_for_specified_work`: spec fully decided across all 4 phases; no Learn-by-Doing delegation despite active learning output style.
- `project_test_files_excluded_from_tsconfig`: confirmed repeatedly — harness LSP flagged 2307/2345/2558 on test files and a phantom deleted `plannerHelpers.ts` throughout; ignored correctly after `tsc -b` + vitest passed.
- `feedback_tests_convention`: all new tests in `__tests__/` (lib, hooks, stores, test-utils).
- `check-output-redirect.sh`: all build/test output redirected to `/tmp/fe-*` logs.
- CLAUDE.md rule #11 (NEVER cd): a session-cwd reset (working dir fell back to repo root) made a `yarn typecheck` fail spuriously; recovered by always passing `--cwd /…/frontend`. Re-confirms the in-command-directory-flag convention; no enforcement change needed.
- code-writer agents followed SEARCH LOG / PATTERN LOG; main conversation skipped it during verification/manual fixes (consistent with prior sessions).

## 2026-06-20 — Request-Latency CORS Preflight

**Graduated:**
- None. Session corrections were instances of already-captured feedback (`feedback_describe_before_code`, `feedback_question_vs_action`, `feedback_one_task_at_a_time`) already present in CLAUDE.md/rules; no full `feedback_*.md` re-audit performed this session.

**Evolved:**
- New reference memory `request_latency_cors_investigation`: pointer to the `docs/learning/` report + spec, recording the non-obvious "FE and API are cross-origin but same-site; `SameSite=Lax` (not the preflight) is the CSRF control" fact so future CORS/auth work recalls it.

**Validated:**
- brainstorm → spec → research-ambiguities → implement → commit gating held; user gated each transition (`feedback_describe_before_code`).
- `feedback_question_vs_action`: conceptual questions (why the preflight; the CORS security model) answered as explanation only, no edits.
- `feedback_lsp_over_grep`, `feedback_tests_convention`: followed (targeted reads/LSP; new test in `__tests__/`).
- `check-output-redirect.sh`: all test/build output redirected to `/tmp`; no violations.
- `project_test_files_excluded_from_tsconfig`: confirmed again — LSP `global` 2304 false positive on `api.test.ts` correctly ignored after `tsc -b` + vitest passed.

## 2026-05-28 — Featured Boss Section

**Graduated:**
- None new. All session-relevant feedbacks (`feedback_minimal_edits`, `feedback_filter_reviewer_findings`, `feedback_simple_fixes`, `feedback_no_inline_annotations`, `feedback_lsp_over_grep`, `feedback_no_todo_human_for_specified_work`) were already graduated into root `CLAUDE.md` or domain rules in prior sessions.

**Evolved:**
- New project memory `project_featured_boss_portrait_resolution`: 4-tier existence-gated resolver chain (`nameID → sdPortrait → id → appearance-prefix`) over a recursive `Portrait/` index, hidden-pack floor glob, and `FLIP_PORTRAIT_KEYS` per-asset flip set (mirrored on the placed-and-cropped layer, not raw source). Protects future maintenance from re-discovering 5 distinct resolution gaps that surfaced through user reports this session.
- New project memory `project_test_files_excluded_from_tsconfig`: `tsconfig.app.json` excludes `__tests__/**`, so `tsc -b` never sees test files and the editor LSP surfaces false 2307/2339 diagnostics under an inferred project. Captured so I don't add inconsistent triple-slash references to silence them next time.

**Validated:**
- `feedback_filter_reviewer_findings`: applied to `/review` orchestrator output — every finding cross-validated against the actual code before Fix/Skip/Defer verdict. The "Inv 1 corrupt-PNG edge" and `ThemePackDetailSchema` missing `.strict()` findings were both validated as real-but-out-of-scope and properly skipped.
- `feedback_minimal_edits`: declined to add `.strict()` to a pre-existing non-strict schema even though the reviewer flagged it; only touched code authored this PR.
- `feedback_simple_fixes`: held back from mtime/content-hash invalidation on skip-on-existing; implemented plain existence-check + scoped orphan-prune (the minimum to make skip safe).
- `feedback_no_inline_annotations`: no change-history comments across all phases or post-build fixes.
- `feedback_no_todo_human_for_specified_work`: spec was fully decided across the build; no Learn-by-Doing delegation despite active learning output style.
- `feedback_tests_convention`: all new tests placed in `__tests__/` (FE `routes/__tests__/FeaturedBoss.test.tsx`, pipeline `scripts/__tests__/test_featured_boss_panel.py`).
- `check-output-redirect.sh` hook: caught a multi-line grep-only inspection (over-triggered) — response was to use dedicated `Read`/`Grep` tools, which was the correct path; hook nudged toward better tooling rather than blocking real work.
- CLAUDE.md rule #11 (NEVER cd): self-caught violation early (one `cd frontend` corrupted relative paths and briefly suggested `raw/` was empty); recovered by switching to absolute paths for the rest of the session. Rule's costliness when violated is now experientially confirmed; no enforcement change needed (single-violation self-correction).
- code-review-orchestrator: env-limited (Task tool unavailable), produced inline aggregate review with cross-validated findings; verdict ACCEPTABLE.
- `Progress` shared logging helper: featured_boss_panel adopted the `featured_boss_panel/compose [N/M] done=X skip=Y` format to match `theme_pack_image/compose` — proves the convention is reusable and worth keeping uniform across pipeline scripts.

## 2026-05-27 — Lineage-Based Refresh Token Rotation

**Graduated:**
- `feedback_filter_reviewer_findings` → root `CLAUDE.md` Code Review section: validate each reviewer finding against code/runtime before applying; state per-finding verdict. Proved high-value this session — the adversarial reviewer caught a real lock-eviction race a prior code-writer had argued away in a Javadoc.

**Evolved:**
- root `CLAUDE.md` Code Review section: added fallback to a single focused `code-architecture-reviewer` when `code-review-orchestrator` overflows ("Prompt is too long") on large diffs. Backed by new memory `reference_code_review_orchestrator_overflow`.

**Validated:**
- `feedback_no_todo_human_for_specified_work`: spec was fully decided, so no Learn-by-Doing delegation despite active learning output style.
- `feedback_no_claude_sign` / `feedback_no_inline_annotations`: no Co-Authored-By, no change-history comments across all phases.
- `feedback_tests_convention`: new frontend test placed in `__tests__/`.
- `check-output-redirect.sh` hook: all build/test output redirected to `/tmp/lineage-rotation-*` logs.
- SEARCH LOG / PATTERN LOG convention: code-writer agents followed it; main conversation skipped it during verification/manual fixes (acceptable).
- code-writer subagents correctly adapted when a spec-mandated mechanism (`ConcurrentHashMap.compute`) was physically impossible, surfacing the deviation rather than forcing it.

## 2026-04-16 — Asset Worker (Phase 3)

**Graduated:**
- `project_wrangler_env_model` (new memory) → `.claude/rules/deployment/wrangler.md`: wrangler 4 strict env inheritance + project's `--env=""` for prod pattern + dry-run verification step
- `feedback_global_submodule_recurse` (new memory): captured `submodule.recurse=true` global config; future sessions won't manually init submodules in worktrees
- `project_canvas_tainting_setup` (new memory): three browser-side conditions + post-Spectre `Cross-Origin-Resource-Policy: cross-origin` requirement
- `feedback_filter_reviewer_findings` (new memory): validate each review finding against code/runtime; dispute with evidence

**Evolved:**
- `.claude/rules/deployment/wrangler.md` (new): graduates the wrangler env model from session memory into a formal deployment rule
- Workflow `deploy-frontend.yml` (out of session scope but related): converted to env-var indirection for shell-injection hardening + conditional `--env=""` for prod (will be committed with this epic)

**Validated:**
- `check-output-redirect.sh` hook: enforced `/tmp/` redirect on all test/typecheck/dryrun commands
- `feedback_no_claude_sign.md`: followed (no Co-Authored-By in any code or commits)
- `feedback_describe_before_code.md`: followed — described 3+ rounds of changes before applying
- `feedback_simple_fixes.md`: followed — collapsed user's 30-line `isAllowedOrigin` to 4-line idiomatic version
- `feedback_no_inline_annotations.md`: followed — no history annotations in code
- `feedback_lsp_over_grep.md`: PARTIAL — used Grep for STAGING_ORIGIN check and routes/images orphan check where LSP `findReferences` would have been better. Worth re-reading next session.
- `feedback_question_vs_action.md`: followed — long discussions about Referer/Origin, canvas tainting, CF Access were explanation-only with no premature edits
- Code review orchestrator: 3 rounds, caught real bugs (shell injection, fake-passing tests) and produced wrong findings (A-1, A-2 in round 1) — validates running `/review` AND validates filtering reviewer findings
- SEARCH LOG / PATTERN LOG: code-writer agents followed; main conversation skipped during manual fixes (consistent with prior 3 sessions — formal rule and practice are drifting; consider relaxing rule to "agents MUST, main conversation MAY skip for targeted single-file edits")
- `commit-process` skill: not engaged this session (user wrapped before commit)

## 2026-04-07 — R2 Infrastructure (Phase 1)

**Graduated:**
- None — no new feedback patterns requiring graduation

**Evolved:**
- None

**Validated:**
- `check-output-redirect.sh` hook: enforced `/tmp/` redirect on test/typecheck commands
- `block-npm-npx.sh` hook: caught `npm view wrangler` attempt, forced yarn alternative
- `feedback_no_claude_sign.md`: followed (no Co-Authored-By in any code)
- `feedback_question_vs_action.md`: followed — manifest architecture discussion was explanation only, no code edits until user confirmed direction
- `feedback_no_inline_annotations.md`: followed across all file changes
- SEARCH LOG / PATTERN LOG: code-writer agents followed; main conversation skipped during manual fixes (acceptable for targeted edits)
- Code review orchestrator: caught real bugs (path filter `'static'` vs `'static/**'`, silent skip in closeBundle) — validates running `/review` on infra code

## 2026-03-30 — Keyword Browser Initialization

**Graduated:**
- `feedback_python_pipeline_ownership` → memory: each Python script (id/ego/gift) must own its domain only
- `feedback_no_node_scripts_for_python_pipeline` → memory: never use Node.js for Python pipeline data generation

**Evolved:**
- `router.tsx`: removed all `pendingComponent` from database routes (identity, ego, egoGift, keyword) — was causing double-skeleton loading UX issue
- `DetailPageSkeleton.tsx`: added `keyword` preset with backlinks layout

**Validated:**
- `check-output-redirect.sh` hook: caught duplicate typecheck output multiple times, forced reading existing results
- `feedback_no_inline_annotations.md`: followed across all phases
- `feedback_tests_convention.md`: all new tests placed in `__tests__/` subdirectories
- SEARCH LOG / PATTERN LOG convention: code-writer agents followed it; main conversation skipped during manual fixes
- `selectable` CSS class: project design system primitive for interactive hover effects — must use instead of custom hover styles

## 2026-03-26 — Compact Deck Editor

**Graduated:**
- None — no new feedback patterns this session

**Evolved:**
- None

**Validated:**
- `check-output-redirect.sh` hook: caught duplicate typecheck output correctly, forced reading existing results
- `feedback_minimal_edits.md`: followed — only changed deck visualization section, left all other DeckBuilderContent code untouched
- `feedback_no_inline_annotations.md`: followed — no "changed from X" comments in code
- SEARCH LOG / PATTERN LOG convention: code-writer agents followed it; main conversation skipped it during manual fixes (acceptable for small targeted edits)
