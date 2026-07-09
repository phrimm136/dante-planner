# Mechanics — keyword migration/validation relocation

Companion to `requirements.md`. Transcribed from the 2026-07-06 → 2026-07-08 session. Contracts below (flow ordering, the strict/lenient split, package layout, set-points) are **binding**; worked failure-mode traces and the full design derivation are **reference** — see the session, not re-transcribed here.

## 1. Save pipeline (binding order)

Every keyword write set-point runs, for `MIRROR_DUNGEON` content:

```
String content (from req or existing entity)
  1. structuralValidator.validateContentSize(content)      // fail-fast on the RAW string, before parse
  2. JsonNode root = objectMapper.readTree(content)         // parse ONCE
  3. keywordMigrator.migrate(root)                          // mutate root.selectedKeywords in place
  4. contentValidator.validate(root, category, published)   // tree validators incl. KeywordValidator; combined 400
  5. String canonical = objectMapper.writeValueAsString(root)
  6. planner.setContent(canonical)                          // canonical storage
  7. planner.setSelectedKeywords(deriveSet(root))           // SET column derived from the same tree
```

- `contentValidator.validate` takes a **`JsonNode`** now, not a `String` (parse moved to the caller so it happens once). The content-**size** check (step 1) is split out to the caller because it needs the string.
- `deriveSet(root)` = read `root.selectedKeywords` → migrate (idempotent here) → **filter to `VALID_KEYWORDS`**. The filter is what keeps the MySQL `SET` write storable on the lenient path (D6).

## 2. Strict vs lenient split (binding)

`strictMode = planner.getPublished()` (the free in-transaction read, D8). Behaviour per keyword class × mode:

| Keyword in content | Draft sync (lenient) | Publish (strict) |
|---|---|---|
| current id (`9828`) | stored | stored |
| renamed id (`AccelBullet`) | migrated → `9828`, stored | migrated → `9828`, stored |
| genuinely unknown (`GhostKeyword`) | **dropped from SET**, kept in content blob | **400 `KEYWORD_INVALID`** |

- Migration (step 3) is mode-independent — renames always resolve.
- `KeywordValidator` (step 4) only raises `KEYWORD_INVALID` when `strictMode` is true; in lenient mode it is silent.
- The SET-derivation filter (step 7) removes any survivor so the column stays storable. On draft, that means an unknown is silently dropped *from the projection* but preserved in the opaque content blob (which the FE migrates on read).
- Consequence to accept: a keyword silently dropped from a draft's SET can still 400 on a later publish (content blob still holds it) — same as stale gift ids. Genuinely-unknown ids can't be authored via the UI, so this is a theoretical edge.

## 3. Components & packages (binding)

| Component | Package | Holds | Responsibility |
|---|---|---|---|
| `KeywordMigrator` | `planner.converter` (NOT `.validation`) | `RENAME_MAP` + `ObjectMapper` | `migrate(JsonNode root)` — remap `root.selectedKeywords` via `RENAME_MAP`, **keep** unknowns, dedupe alias↔current collision, mutate in place. No-op if field absent/non-array. |
| `KeywordValidator` | `planner.validation` (beside `IdReferenceValidator`) | `VALID_KEYWORDS` | `validate(root, ctx)` — for each `root.selectedKeywords`, if `!VALID_KEYWORDS.contains(k)` and `ctx.isStrict()`, `ctx.addError(KEYWORD_INVALID)`. Message names only the offending id — no whitelist enumeration (leak guard). |
| `KeywordSetConverter` | `planner.converter` | — | Pure `Set ↔ CSV`. Write: `null`/empty → null, else `sorted().join(",")`. Read: split/trim/filter-empty. **No remap, no whitelist filter, never throws.** |

- `VALID_KEYWORDS` **moves** `KeywordSetConverter` → `KeywordValidator`. `RENAME_MAP` **moves** `KeywordSetConverter` → `KeywordMigrator`. `RENAME_MAP` keys stay **out** of `VALID_KEYWORDS` (rename targets like `9828` must be in it).
- `KeywordParityTest` repoints `KeywordSetConverter.VALID_KEYWORDS` → `KeywordValidator.VALID_KEYWORDS`, and gains a check that FE `KEYWORD_RENAME_MAP` (`frontend/src/shared/gameData/constants.ts`) equals BE `RENAME_MAP`.

## 4. Write set-points (binding — all must run the pipeline)

Keywords land on the entity at three points today; each must run §1 and derive the SET from content, gated to MD:
- `PlannerCommandService.applyRequestFields` (update path)
- `PlannerCommandService.createPlanner` builder (create path)
- `PlannerCommandService.importPlanners` builder (bulk import)
- Plus the publish flow (`PlannerPublishingService`) if it re-saves content.

Extract a single private helper (e.g. `resolveContentAndKeywords(content, category, published)`) returning `(canonicalContent, derivedSet)` and call it at all set-points — do not inline the pipeline three times.

## 5. Byte-parity (binding constraint + guard)

- Canonical storage (step 5) re-serializes on **every** save, so V8(`JSON.stringify`)≡Jackson(`writeValueAsString`) byte-parity becomes load-bearing for the content-size and note-size gates **only**.
- Content is currently stored **verbatim** (`PlannerContentSanitizer` is dead code — never wired), so this refactor *introduces* the re-serialization; the parity guard is required, not optional.
- Guard: a fixture test round-tripping realistic + max-size content through `readTree → writeValueAsString` and asserting byte-equality against a captured V8 reference. Planner content is ints + strings + Tiptap note JSON (note V8↔Jackson parity already proven, "no safety margin"); no floats.
- Reference: FE dirty/conflict detection is **immune** — it keys on `syncVersion` + the FE's own `stateToComparableString` (local V8), never on server content bytes; `usePlannerSyncAdapter` `JSON.parse`s server content on arrival and discards the raw string. So parity matters at the size gates and nowhere else.

## 6. Backward-compat & deploy (binding)

- DTO field deletion is safe: `JacksonConfig.java:30` disables `FAIL_ON_UNKNOWN_PROPERTIES`. A stale client still sending top-level `selectedKeywords` → ignored.
- **Deploy BE-first.** BE derives from content and ignores the old field; stale clients keep working; then the FE ships to stop sending. **Do not roll the BE back after the FE ships** — old BE reads `req.selectedKeywords()`, which the new FE no longer sends → the SET column stops updating.
- Existing SET rows self-heal (re-derived on next save); no backfill.

## 7. SET-column consumers (reference — must keep working)

The SET column exists because it's queried; the derivation must keep feeding these:
- `PlannerSpecifications` `FIND_IN_SET(keyword, selectedKeywords)` — published-list keyword filter.
- `PlannerRepository` `LOWER(selectedKeywords) LIKE '%search%'` — keyword search (×8 queries).
- `PublicPlannerResponse.selectedKeywords` — list-card chips (read path, unchanged; FE `PublicPlannerSchema` still `migrateKeywords`-preprocesses it).
