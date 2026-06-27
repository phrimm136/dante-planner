---
name: plan-keyword
description: Add a selectable planner keyword end-to-end across FE, BE, and i18n with the proper in-game translation. Use when adding a plan keyword, synergy keyword, faction keyword, or a value to the planner keyword filter.
---

# Plan Keyword Workflow

> Scope: keyword-only. Hybrid execution — Claude performs every file edit by hand;
> the read-only companion `resolve-labels.py` only resolves and displays the exact
> unit-keyword labels so the hand-edits are byte-accurate. The script never writes
> project files.

## Concept primer — the three identities of a keyword

| Thing | Example | Lives in | Used here? |
|---|---|---|---|
| Internal id (join key) | `AccelBullet` | every layer | yes — the value you add |
| Mechanic name | "Acceleration Round" | `battleKeywords.json` | **no — wrong source** |
| Faction label (displayed) | "The Thumb" | `unitKeywords.json` → `plannerKeywords.json` | yes — the label |

## Step 1 — Gather input
- The user supplies one or more **`keyword-id` ↔ `unit-keyword-id` pairs**
  (e.g. `AccelBullet ↔ THUMB_FINGER`, `SojiRyoshuEntangle ↔ SPIDER_HOUSE`).
  - `keyword-id` = the internal battle-keyword id (the join key added to every layer).
  - `unit-keyword-id` = the faction key in `unitKeywords.json` whose label is displayed.
- **Target array is always `SYNERGY_KEYWORDS`** in `frontend/src/lib/constants.ts`.
  `KEYWORD_ORDER` and `AFFINITIES` are **never** modified by this skill.

## Step 2 — Resolve localized labels  *(read-only helper)*
- Run the resolver (pair form recommended):
  ```
  python3 .claude/skills/plan-keyword/resolve-labels.py AccelBullet=THUMB_FINGER SojiRyoshuEntangle=SPIDER_HOUSE
  ```
  It prints each id's EN/KR/JP/CN label **byte-for-byte** with a `utf-8 bytes=NN`
  count, and — for the pair form — a paste-ready `plannerKeywords.json` property line
  per locale. It only displays; it writes nothing.
- Use the printed bytes as the source of truth for the hand-edits in Step 4 — never
  retype or normalize a label; copy the exact bytes (incl. any non-ASCII). The
  `utf-8 bytes=NN` count is your copy-fidelity check.
- **Confirm spelling/plurality** of the EN label with the user (e.g. "Spider" → in-game
  "The House of Spiders") before editing. A `!! MISSING` line means that locale has no
  translation for the faction — raise it with the user, do not invent one.

## Step 3 — Edit the three enforcement layers by hand (keep in sync)
Append each `keyword-id` to all three:

| Layer | Full path | Edit |
|---|---|---|
| FE list | `frontend/src/lib/constants.ts` | append id to the `SYNERGY_KEYWORDS` array, before its closing `] as const` |
| BE allow-list | `backend/src/main/java/org/danteplanner/backend/converter/KeywordSetConverter.java` | append id to `VALID_KEYWORDS` (the `Set.of(...)`) |
| DB constraint | new `backend/src/main/resources/db/migration/V{next}__add_<slug>_keywords.sql` | `ALTER TABLE planners MODIFY COLUMN selected_keywords SET(...)` re-declaring **every existing member** + the new id(s) |

- `V{next}`: list `backend/src/main/resources/db/migration/`, take the highest `V###`, add 1.
- Copy the SET body from the latest keyword migration and append — never remove a member (Flyway is append-only; see Gotchas).

## Step 4 — Edit the 4 i18n label files by hand  *(static submodule)*
Add a `"<keyword-id>": { "label": "<exact-bytes-from-Step-2>" }` entry to each:
- `static/i18n/EN/plannerKeywords.json`
- `static/i18n/KR/plannerKeywords.json`
- `static/i18n/JP/plannerKeywords.json`
- `static/i18n/CN/plannerKeywords.json`

## Step 5 — Verify  *(no new tests needed)*
- FE: in `frontend`, `tsc -b`.
- BE: `KeywordParityTest` (auto-asserts FE↔converter↔SET parity) + the containerized
  `MySQLIntegrationTest` round-trip.
- **Do not author per-keyword tests** — both suites read the keyword set dynamically and
  cover additions automatically.
- Fidelity check: each label you wrote into `plannerKeywords.json` must match the
  `utf-8 bytes=NN` the resolver printed in Step 2.

## Step 6 — Commit
- Hand off to `commit-process`: **two-repo, submodule-first** (label commit on `static`/`main`,
  then supermodule commit with the pointer bump). The submodule push must go up too, or
  the supermodule pointer references a commit nobody else can fetch.

## Gotchas  *(the reason this skill exists)*
- **Converter is the real gate.** Skip `VALID_KEYWORDS` → save throws `IllegalArgumentException`;
  reads silently drop the keyword via `.filter(VALID_KEYWORDS::contains)`. The MySQL `SET`
  migration alone is **not** enough.
- **`StructuralValidator` is type-only** — it checks `selectedKeywords` is an array, not
  membership. **No change needed** (don't waste an edit here).
- **H2 vs MySQL column divergence.** The converter column has no `columnDefinition`: H2 auto-DDL
  makes VARCHAR(255), prod/IT is the real `SET`. The all-keywords e2e (~375 chars) only fits the
  MySQL/Testcontainers tier — never assert it on H2.
- **Flyway is append-only.** Never edit a prior migration; always a new `V{next}` re-declaring
  the whole SET.

## Companion: `resolve-labels.py`
- **Location:** `.claude/skills/plan-keyword/resolve-labels.py` (finds the repo root itself; runs from any cwd).
- **Role:** read-only accuracy aid. Resolves `unit-keyword-id`s → display labels so the
  Step 4 hand-edits are byte-exact. **Writes nothing.**
- **Input:** one or more `[KEYWORD_ID=]UNIT_KEYWORD_ID` tokens (bare token = unit-keyword id).
- **Reads:** `static/i18n/{EN,KR,JP,CN}/unitKeywords.json`.
- **Output:** per id, the EN/KR/JP/CN label byte-for-byte + `utf-8 bytes=NN`; the pair form
  also prints a paste-ready `plannerKeywords.json` property line; `!! MISSING` when a locale
  lacks the faction.

## Reference — canonical file map & invariant
| Purpose | Path |
|---|---|
| FE selectable list | `frontend/src/lib/constants.ts` (`SYNERGY_KEYWORDS` → `PLANNER_KEYWORDS` → `type PlannerKeyword`) |
| BE allow-list (the gate) | `backend/src/main/java/org/danteplanner/backend/converter/KeywordSetConverter.java` (`VALID_KEYWORDS`) |
| DB constraint | `backend/src/main/resources/db/migration/V{n}__…` (`selected_keywords` SET column) |
| Label source (read-only) | `static/i18n/{EN,KR,JP,CN}/unitKeywords.json` |
| Label store (rendered) | `static/i18n/{EN,KR,JP,CN}/plannerKeywords.json` |
| Parity guard | `backend/src/test/java/org/danteplanner/backend/converter/KeywordParityTest.java` |
| e2e round-trip (Docker) | `backend/src/test/java/org/danteplanner/backend/integration/MySQLIntegrationTest.java` (`SelectedKeywordsTests`) |

**Invariant:** the FE `plannerKeywords.json` key set, the converter `VALID_KEYWORDS`, and the
latest migration's `SET(...)` members must be **equal**. `KeywordParityTest` enforces it — adding
a keyword to fewer than all three fails the build.
