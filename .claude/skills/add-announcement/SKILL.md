---
name: announcement
description: Add a new announcement to all 5 JSON files with auto-translation, commit, and tag. Use when adding announcements, writing update notices, or posting new content.
---

# Announcement Workflow

## Step 1 — Gather Input

Ask the user for:
- **Korean title** (`kr_title`)
- **Korean body** (`kr_body`)
- **Date** — default today `YYYY-MM-DD` if not provided
- **expiresAt** (`YYYY-MM-DD`) — optional, skip if not provided
- **permanent** (`boolean`) — optional, skip if not provided


## Step 2 — Generate ID

**Format:** `{date}-{NN}` where NN is a zero-padded 2-digit incremental counter.

The date used in the ID depends on announcement type:
- **Regular announcements:** Use the provided date (or today)
- **Permanent announcements:** Use `1970-01-01`

To determine NN:
1. Read `static/data/announcements.json`
2. Search the entire array for entries whose `id` starts with the target date prefix
3. If matches exist, increment the highest counter; otherwise start at `01`
4. Example: existing `"1970-01-01-01"` → new permanent ID is `"1970-01-01-02"`

## Step 3 — Refine Korean Text

Before translating, refine the raw Korean input to match the tone and style of existing announcements:
1. Read recent entries in `static/i18n/KR/announcements.json` to learn the established style
2. Read the term dictionary at `.claude/skills/add-announcement/terms.csv` and correct any game terms to their canonical Korean forms (e.g., `EGO 기프트` → `E.G.O 기프트`)
3. Rewrite `kr_title` and `kr_body` to match that style (sentence structure, formality, formatting)
4. Present the refined Korean text to the user for approval before proceeding to translation

Do NOT proceed to translation until the user approves the refined Korean text.

## Step 4 — Translate

Read the term dictionary at `.claude/skills/add-announcement/terms.csv` (if not already read in Step 3).
It contains canonical translations for:
- Sinner names (12 characters)
- Game terms: 인격 → Identity/人格/人格, E.G.O 기프트 → E.G.O Gifts/E.G.Oギフト/E.G.O饰品, E.G.O
- Mirror dungeon names (mirrors 1–7)
- Difficulty modes: 노말/Normal/ノーマル/普通, 어려움/Hard/ハード/困难, 평행중첩, etc.

For in-game terms like identity names, EGO names, and EGO gift names — read the subsection.

Translate `kr_title` and `kr_body` into EN, CN, JP using the dictionaries above for all game-specific terms. Use natural-sounding language for surrounding prose.

### In-game term syntax

In the input, `[[{category}:{name}]]` marks an in-game term. Find its id or internal representation in the KR i18n files, then use that key to look up translations in EN/JP/CN.

| Category | Lookup file (`static/i18n/KR/`) | Key format |
|----------|--------------------------------|------------|
| `id` | `identityNameList.json` | numeric id |
| `ego` | `egoNameList.json` | numeric id |
| `gift` | `egoGiftNameList.json` | numeric id |
| `keyword` | `battleKeywords.json` | PascalCase id |
| `themepack` | `themePack.json` | numeric id |

**Example:** `[[keyword:부하]]` → search `battleKeywords.json` for entry with `"name"` containing `부하` → find key `ChargeLoad` → look up `ChargeLoad` in EN/JP/CN `battleKeywords.json` → `부하/Load/負荷/载荷`

## Step 5 — Write All 5 Files

Read each file before editing. JSON must remain valid (no trailing commas).

### `static/data/announcements.json`

**Regular announcements:** Prepend a new object at index 0 (newest-first):
```json
{
  "id": "<generated-id>",
  "date": "<date>"
}
```

**Permanent announcements:** Append at the end of the array. Use date `1970-01-01`.
```json
{
  "id": "<generated-id>",
  "date": "1970-01-01",
  "permanent": true
}
```

Include `"expiresAt": "<date>"` only if provided.
Include `"permanent": true` only if provided.

### `static/i18n/KR/announcements.json`
Add key `"<id>": { "title": "<kr_title>", "body": "<kr_body>" }` alongside existing entries.

### `static/i18n/EN/announcements.json`
Add key `"<id>": { "title": "<en_title>", "body": "<en_body>" }`.

### `static/i18n/CN/announcements.json`
Add key `"<id>": { "title": "<cn_title>", "body": "<cn_body>" }`.

### `static/i18n/JP/announcements.json`
Add key `"<id>": { "title": "<jp_title>", "body": "<jp_body>" }`.

## Step 6 — Commit and Tag

1. **Commit in static submodule** (on `main`):
```bash
cd static
git add data/announcements.json i18n/KR/announcements.json i18n/EN/announcements.json i18n/JP/announcements.json i18n/CN/announcements.json
git commit -m "data: add <date> announcement"
```

2. **Commit submodule ref in supermodule** (on `dev`):
```bash
cd ..
git add static
git commit -m "data: add <date> announcement"
```

3. **Tag the supermodule commit** with format `v{YY}{MM}{DD}`:
```bash
git tag v<YYMMDD>
git push origin v<YYMMDD>
```
Example: date `2026-04-01` → tag `v260401`.

If the tag already exists (multiple announcements same day), append a sequential suffix: `v260401-2`.

## Step 7 — Confirm

List all 5 files changed, display the generated ID and tag name.
