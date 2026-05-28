# Featured Boss Section — Results

## What Was Done

All six phases from `plan.md` executed and verified, plus four post-build bug fixes raised by the user and one polish pass from `/review`.

- Phase 1 — Shared resolver + index fix + all-packs loop (compositor)
- Phase 2 — Manifest emission (`theme_pack.py` imports the shared resolver)
- Phase 3 — Pipeline wiring (`featured_boss_panel` registered in the Theme Pack step)
- Phase 4 — i18n mining (`themePack.featuredBoss` in all 4 `database.json`, with the existing trap-street suffix appended per language)
- Phase 5 — Schema (`featuredBosses` added as required field to `ThemePackDetailSchema`; Invariant 3 verified — not part of any saveable composition)
- Phase 6 — Frontend render (`getFeaturedBossImagePath` helper + `FeaturedBoss` component at the top of the right column, self-hides on empty roster)

Post-build work (the resolver evolved from 1 hop to a 4-tier existence-gated chain plus visual corrections):

- Bug 1 (pack 1002 missing 8001/8002) — added `id` as 3rd candidate
- Bug 2 (pack 1502 missing 1042 → 8376) — made `_portrait_path` resolve recursively across `Portrait/Ep*_*/`
- Bug 3 (pack 1520 Vergilius missing) — added `appearance`-prefix as 4th candidate (`9999_Vergilius...` → `9999_portrait.png`)
- Bug 4 (pack 3001 hidden pack missing) — broadened pack-floor glob from `-t*.json` to `*.json`
- Polish 1 (Vergilius faces right) — `FLIP_PORTRAIT_KEYS = {"9999"}` constant, mirrored on the placed-and-cropped layer
- Polish 2 (Vergilius biased right) — moved the flip from raw portrait to *after* fit+crop+mask, so the `+9px` x-anchor isn't compounded
- Polish 3 (logging consistency) — replaced per-line `print()` with the shared `Progress` helper (`featured_boss_panel/compose` label, matching `theme_pack_image/compose`)
- Polish 4 (re-run speed) — skip-on-existing in `main()`, paired with scoped orphan-prune so the dir stays consistent with the manifest

## Files Changed

| Path | Status |
|---|---|
| `static/scripts/featured_boss_panel.py` | NEW (untracked) — compositor, resolver chain, `Progress` logging, skip+prune |
| `static/scripts/theme_pack.py` | MODIFIED — imports shared resolver; emits `featuredBosses` per pack |
| `static/scripts/pipeline.py` | MODIFIED — registers `featured_boss_panel` in Theme Pack step |
| `static/scripts/__tests__/test_featured_boss_panel.py` | NEW — 28 tests covering resolver, manifest↔disk parity, idempotency, skip+prune, flip behavior, hidden-pack inclusion |
| `static/data/themePack/*.json` | REGENERATED — 116 files now carry `featuredBosses` arrays |
| `static/images/featuredBoss/*.webp` | NEW — 289 composited panels |
| `static/i18n/{EN,KR,JP,CN}/database.json` | MODIFIED — added `themePack.featuredBoss` mined from raw with trap-street suffix |
| `frontend/src/schemas/ThemePackSchemas.ts` | MODIFIED — `FeaturedBossSchema` + required `featuredBosses` field |
| `frontend/src/schemas/__tests__/ThemePackSchemas.test.ts` | MODIFIED — added populated/empty/omitted cases |
| `frontend/src/lib/assetPaths.ts` | MODIFIED — `getFeaturedBossImagePath(packId, portraitId)` |
| `frontend/src/routes/ThemePackDetailPage.tsx` | MODIFIED — `FeaturedBoss` component mounted first in `rightColumn` |
| `frontend/src/routes/__tests__/FeaturedBoss.test.tsx` | NEW — renders one img per manifest entry; self-hides on empty |
| `docs/22-featured-boss/plan.md`, `status.json` | NEW — execution plan + completion tracking |

## Verification

- **Pipeline pytest**: 28 / 28 pass in 0.66s (down from 198s pre-skip — the idempotency subprocess now skips all when re-running)
- **Frontend typecheck** (`tsc -b`): clean
- **Full FE test suite**: 5954 passed / 1 skipped across 109 files
- **`dataIntegrity.test.ts`**: validates all 116 `static/data/themePack/*.json` against the schema with the new required `featuredBosses` field — green
- **Manifest ↔ disk set-equality**: enforced by `test_manifest_matches_disk` in both directions; passes
- **Manual visual**: 1002 / 1502 / 1520 / 3001 panels rendered correctly; Vergilius now faces left and is centered
- **Code review** (`/review` orchestrator): ACCEPTABLE; all four invariants pass; no critical/high findings

## Issues & Resolutions

- I ran `cd frontend` in one early Bash call. CLAUDE.md forbids `cd`, and it corrupted the session's working directory so subsequent relative-path checks (`raw/`, `static/data`) returned empty results. I briefly thought `raw/` was unpopulated and almost surfaced a false blocker. → Caught it within a few turns by re-checking with absolute paths; from then on, every Bash call used absolute paths or `--cwd`/`-C`. Lesson reinforced.
- The `/check-output-redirect.sh` hook misfired on a multi-line `grep`-only inspection (no test/build invocation), blocking the command. → Replaced with the dedicated `Read`/`Bash`-with-grep tools.
- The `code-review-orchestrator` couldn't actually use the Task tool to fan out (env limitation), so it produced an inline aggregate. Findings were still cross-validated against the actual code per `feedback_filter_reviewer_findings`.
- Resolver evolved across four user reports. Each was the *same meta-bug*: an assumption about *where* a portrait key/file lives (the field, the dir, the filename). The cumulative fix collapses all four assumptions into one existence-gated chain over a recursive index — every `showBossIds` slot across all 115 packs now resolves except genuinely-unshipped art.

## Learnings

- **The "single shared resolver" invariant was the architectural spine, not a nice-to-have.** Inv 1 says `theme_pack.py` and the compositor must derive the boss roster from the same function — and they do. Without that, every bug-fix iteration would have required keeping two resolution chains in lockstep, and at least one would have drifted.
- **The skip-on-existing speedup only worked once paired with a scoped orphan-prune.** Skip alone leaves stale webps from prior resolver-version runs (key-changing edits); prune cleans them. The two go together for `featured_boss_panel.py` precisely because its filenames *embed the resolved portraitId* (unlike `theme_pack_image.py`, whose `{packId}.webp` filename is stable). Filename naming schemes carry implicit dependencies on cleanup strategy.
- **Empirical visual check trumped geometry math.** When Vergilius was "biased right," I started reasoning about anchor offsets and flip orientation; the user's "flip must be applied after cropping" was a one-line redirection that solved it. Reading the actual rendered image (via `Read` on the webp) was the fastest debugging signal — better than trying to predict pixel positions from RectTransform constants.
- **`tsc -b` excludes test files in this project, so the editor LSP and `tsc` disagree on test-file diagnostics.** False 2307/2339 errors appear on touched test files but every sibling test has the same latent issues; saved as `project_test_files_excluded_from_tsconfig.md` so I don't chase them again.
- **The trap-street suffix lives unchanged across all `themePack.*` siblings in each `database.json`.** Mining a new value and appending the suffix from an adjacent key is mechanical; round-tripping the JSON with `json.dumps(ensure_ascii=False, indent=2)` produced minimal (added-lines-only) diffs because the files were already exactly in that format.

## Spec Divergence

### What Changed

- **Resolution chain depth.** Spec described `_build_unit_index` as "nameID preferred, sdPortrait fallback" (2 tiers). Reality is 4 tiers (`nameID → sdPortrait → id → appearance-prefix`), each existence-gated, over a *recursive* portrait index. The spec's chain was correct for ~99 of the 290 showBoss slots (regular enemies) and the most-common abnormality case (sdPortrait), but silently dropped ~92 additional slots whose portrait keys lived at lower-priority candidates or in episode subdirs.
- **Pack-floor glob.** Spec referenced `mirrordungeon-theme-floor-t{N}.json` (tiered files only). Reality also includes `mirrordungeon-theme-floor-hidden.json` (pack 3001). The compositor must use `*.json` to match what `theme_pack.py` already used.
- **Pipeline behavior.** Spec said "the script does NOT skip existing webps — it recomposes/overwrites every run." This was changed mid-session per user request to a skip+prune model for re-run speed (28 tests run in 0.66s vs 198s).
- **Logging.** Spec didn't specify the log format. Per-line `print()` was the initial implementation; converted to `Progress(...)` to match sibling scripts (`theme_pack_image/compose`).
- **Per-asset corrections.** Spec did not anticipate the `FLIP_PORTRAIT_KEYS` constant (Vergilius's source art faces right; no game data field encodes facing).

### What Was Added (Not in Spec)

- `_appearance_prefix` candidate, `_portrait_index` recursive cache, hidden-pack glob, `FLIP_PORTRAIT_KEYS` flip-set, scoped orphan-prune, `Progress`-based logging.
- Why not foreseeable: the spec was authored against an audit of 290 boss-refs that *appeared* to cover all resolution cases (177 unique unitIds, 99 nameID-resolvable, 42 sdPortrait-resolvable, the rest "unresolved battle-only"). The "unresolved" bucket was actually a mix of: id-keyed portraits (recoverable), nested-subdir art (recoverable), appearance-prefix art (recoverable), hidden-pack hidden by file-glob (recoverable). Only by *trying to render* did the real distribution surface.
- The flip-set and the `Progress` logging are pure polish items raised after the user saw output.

### What Was Dropped

- Nothing functional. The spec's "Done When" checklist is fully satisfied. The frontend tests use `container.querySelectorAll` instead of `getAllByRole` because the panels are `alt=""` decorative — review confirmed this is correct given convention parity with sibling cards.

### Wrong Assumptions

- **Portrait directory structure.** Spec implicitly assumed `Sprite/Unit/Portrait/{key}_portrait.png` is flat. Reality: 497 flat + 136 in episode subdirs (`Ep7_*`, `Ep8_*`, `Ep9_*`); filenames are globally unique, making recursive lookup unambiguous.
- **Floor file naming.** Spec referenced "`mirrordungeon-theme-floor-t{N}.json`" as if those were all the floor files. The `-hidden` variant was not enumerated; pack 3001 lives there.
- **Resolution chain completeness.** Spec said "Recovers ~34 abnormality bosses (verified on disk)" with the sdPortrait fallback. Actually +56 with id-fallback, +36 more with recursive lookup, +1 with appearance-prefix = ~+125 boss-slots beyond the original 2-tier chain.

### Prompting Retrospective

What the user could have asked during `/brainstorm` or `/spec` to surface these earlier:

- **Resolution chain exhaustiveness:** "For each unitId in any pack's `showBossIds`, what *single* on-disk path would produce that boss's portrait? List the field-lookups *and* the directory-traversal pattern. Are there any unitIds where the candidate path doesn't exist?"
  - Why: This is the dual question. The spec asked "what's the resolution chain?" but not "is there any unitId where this chain returns nothing?" A boss-by-boss completeness audit would have surfaced 8001/8002 (id-keyed), 1042 (nested subdir), 8999 (appearance-prefix), and 3001 (file-glob narrow) before code was written.
- **Source-file globs:** "What's the exhaustive list of files matching `mirrordungeon-theme-floor*.json`? Does our planned glob match all of them?"
  - Why: The spec used `t{N}` shorthand. A literal `ls` would have shown `-hidden` immediately.
- **Per-asset overrides:** "Are there any boss portraits whose orientation/scale/offset differs from the default? How does the game encode that? If not encoded, what would our policy be?"
  - Why: Vergilius's right-facing source is a per-asset oddity. The spec assumed all portraits are visually interchangeable; absent a data field, our policy is "centralized flip set."
- **Pipeline performance posture:** "Should `featured_boss_panel.py` be incremental (skip existing) or unconditional (always recompose)? What's the cost of each on a CI re-run and on a dev iteration?"
  - Why: The spec defaulted to overwrite-always. Once 289 panels existed, re-runs were slow enough to motivate change. Pre-deciding skip-vs-overwrite would have surfaced the orphan-prune requirement before it became a bug.
- **Logging convention:** "Which existing pipeline script's logging do you want this to look like? `theme_pack_image/compose` or different?"
  - Why: Trivial decision but a divergence surfaced post-build; one sentence in spec would have settled it.

### Spec Process Takeaway

This spec systematically missed **per-asset and per-file exhaustiveness checks** — it described the *common* resolution paths and globs accurately but assumed they were complete. Future specs that touch large heterogeneous asset sets should include a "completeness audit" step: for each member of the input set, prove the planned chain produces a non-null output (or document why a null is the correct answer).

## Session State

- **Uncommitted**, in this order:
  1. `static` submodule — 9 code/data/i18n changes + 289 untracked webps + 2 new files (`scripts/featured_boss_panel.py`, `scripts/__tests__/test_featured_boss_panel.py`)
  2. Parent repo frontend — 4 modified files + 1 new test
  3. Parent repo docs — `docs/22-featured-boss/{plan,status,results}.md`
- **Next step:** commit when ready. Submodule first (the commit-process skill handles branch creation and message), then the parent pointer bump in the same workflow.
- **Blockers:** none.
- **Resume hint:** read `docs/22-featured-boss/results.md` (this file) and `instructions.md` for full context. The plan/status files reflect all six phases done; results adds the four post-build resolver fixes and the polish pass.
