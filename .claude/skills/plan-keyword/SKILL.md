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

### The join-key id doubles as the icon source

`getKeywordIconPath` in `frontend/src/lib/assetPaths.ts` branches on the **shape** of the
`keyword-id` — there is no separate icon field:

| keyword-id shape | Example | Icon resolved from |
|---|---|---|
| 4-digit numeric (ego-gift id) | `9828`, `9154` | `/images/icon/egoGift/{id}.webp` |
| any other string (battle keyword) | `AccelBullet` | `/images/icon/battleKeywords/{id}.webp` |

So to give a keyword an **ego-gift icon**, the join-key id you register *is* the 4-digit
ego-gift id. Before using one, confirm both exist: the icon asset
`static/images/icon/egoGift/{id}.webp` **and** a matching `{id}` entry in
`static/data/egoGiftSpecList.json`. The faction label still comes from a `unit-keyword-id`
(Step 2) — only the icon source changes.

## Step 1 — Gather input
- The user supplies one or more **`keyword-id` ↔ `unit-keyword-id` pairs**
  (e.g. `AccelBullet ↔ THUMB_FINGER`, `SojiRyoshuEntangle ↔ SPIDER_HOUSE`).
  - `keyword-id` = the join key added to every layer. It is **either** a battle-keyword id
    (`AccelBullet`) **or** a 4-digit ego-gift id (`9828`) — the shape decides the icon
    (see "The join-key id doubles as the icon source" above).
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
| BE allow-list | `backend/src/main/java/org/danteplanner/backend/planner/converter/KeywordSetConverter.java` | append id to `VALID_KEYWORDS` (the `Set.of(...)`) |
| DB constraint | new `backend/src/main/resources/db/migration/V{next}__add_<slug>_keywords.sql` | `ALTER TABLE planners MODIFY COLUMN selected_keywords SET(...)` re-declaring **every existing member** + the new id(s) |

- `V{next}`: list `backend/src/main/resources/db/migration/`, take the highest `V###`, add 1.
- Copy the SET body from the latest keyword migration and append — never remove a member (Flyway is append-only; see Gotchas).

### Variant — renaming or replacing a keyword id (e.g. swap the icon)

Replacing an existing id (changing its icon = changing the join-key id, e.g.
`AccelBullet` → `9828`) is **not** a pure append: the old id must be removed from all three
layers so `KeywordParityTest` stays satisfied. In the converter and `SYNERGY_KEYWORDS` just
swap the value in place. The DB migration is the trap — a MySQL `SET` `MODIFY` re-maps rows
**by value**, so dropping a member silently deletes it from existing rows. Use the proven
three-step add→migrate→remove pattern (model it on `V038__rename_charge_load_keyword.sql`):

1. `ALTER … MODIFY` the SET to add the new id while **keeping** the old one.
2. `UPDATE` existing rows with the boundary-safe replace (copy V038's `UPDATE` verbatim, just
   substitute the two ids):
   ```sql
   UPDATE planners
   SET selected_keywords = TRIM(BOTH ',' FROM
       REPLACE(CONCAT(',', selected_keywords, ','), ',OLD_ID,', ',NEW_ID,'))
   WHERE FIND_IN_SET('OLD_ID', selected_keywords) > 0;
   ```
3. `ALTER … MODIFY` the SET again to the final definition with the old id removed.

**Then register the rename in the converter — this step is mandatory, not optional.** Add an
`OLD_ID → NEW_ID` entry to `RENAME_MAP` in `KeywordSetConverter.java` (same pair as the SQL
`UPDATE`; keep the two identical). The migration `UPDATE` only rewrites rows **already in the DB**
at deploy time. An offline or stale-bundle client still holding the old id syncs it **after** the
migration, via `PUT /api/planner/md/{id}`. `convertToDatabaseColumn` remaps `RENAME_MAP` entries to
the current id before storing, so that late save is preserved as `NEW_ID` instead of being silently
dropped by the `VALID_KEYWORDS` filter. Omit this and every not-yet-synced client loses the keyword.

`RENAME_MAP` keys are the **removed** SET members — they must stay **out** of `VALID_KEYWORDS`
(which `KeywordParityTest` locks to the current SET/FE set). Old ids accumulate in `RENAME_MAP`;
never delete an entry, or its stragglers regress.

**Mirror the rename in the FE map too — also mandatory.** Add the same `OLD_ID → NEW_ID` entry to
`KEYWORD_RENAME_MAP` in `frontend/src/shared/gameData/constants.ts`. The FE `migrateKeywords`
converter uses it to remap legacy ids loaded from IndexedDB / server / import before they render,
so guest planners and pre-sync state show the correct icon instead of a broken one — the BE fix
alone can't reach those. `migrateKeywords` remaps renames but deliberately **preserves** unknown
(non-renamed) ids so the strict publish tier (`validateSelectedKeywords` → `KEYWORD_INVALID`) can
reject genuine corruption loudly, the way gift/EGO ids are rejected — it never silently drops.
Like the BE, FE `KEYWORD_RENAME_MAP` keys must stay **out** of `PLANNER_KEYWORDS`, and entries
accumulate (never delete one).

**FE ↔ BE `RENAME_MAP` must stay in lockstep.** They are the same alias→current pairs in two
languages; a rename that updates one and not the other leaves that tier broken. No test currently
guards this coupling (`KeywordParityTest` checks the valid-keyword sets, not the rename maps) — it is
a manual invariant, so update both in the same change.

If the old id is still a valid id in another domain (e.g. `AccelBullet` remains a battle
keyword used by identities), you are only removing its **planner-keyword registration** —
leave its battle-keyword data in `static/` untouched.

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
- **Converter is the real gate — and it now fails *silently*.** `convertToDatabaseColumn` no
  longer throws on an unregistered id (it once did — a stale keyword 500'd the whole planner sync).
  It remaps `RENAME_MAP` entries, then **drops** anything still outside `VALID_KEYWORDS`, mirroring
  the read filter. So forgetting `VALID_KEYWORDS` when adding a keyword produces no runtime error —
  the keyword just vanishes on save. `KeywordParityTest` is the **only** guard that catches the
  miss; the MySQL `SET` migration alone is **not** enough.
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
| FE selectable list | `frontend/src/shared/gameData/constants.ts` (`SYNERGY_KEYWORDS` → `PLANNER_KEYWORDS` → `type PlannerKeyword`; `KEYWORD_RENAME_MAP` for retired ids) |
| FE keyword converter | `frontend/src/shared/gameData/keywordNormalize.ts` (`migrateKeywords` remaps renames, keeps unknown) |
| FE strict reject | `frontend/src/pages/planner/lib/plannerValidation.ts` (`validateSelectedKeywords` vs `VALID_PLANNER_KEYWORDS` → `KEYWORD_INVALID`) |
| BE allow-list (the gate) | `backend/src/main/java/org/danteplanner/backend/planner/converter/KeywordSetConverter.java` (`VALID_KEYWORDS`; `RENAME_MAP` for retired ids) |
| DB constraint | `backend/src/main/resources/db/migration/V{n}__…` (`selected_keywords` SET column) |
| Label source (read-only) | `static/i18n/{EN,KR,JP,CN}/unitKeywords.json` |
| Label store (rendered) | `static/i18n/{EN,KR,JP,CN}/plannerKeywords.json` |
| Parity guard | `backend/src/test/java/org/danteplanner/backend/converter/KeywordParityTest.java` |
| e2e round-trip (Docker) | `backend/src/test/java/org/danteplanner/backend/integration/MySQLIntegrationTest.java` (`SelectedKeywordsTests`) |

**Invariant:** the FE `plannerKeywords.json` key set, the converter `VALID_KEYWORDS`, and the
latest migration's `SET(...)` members must be **equal**. `KeywordParityTest` enforces it — adding
a keyword to fewer than all three fails the build.
