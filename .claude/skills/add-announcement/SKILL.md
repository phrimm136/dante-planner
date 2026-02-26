---
name: add-announcement
description: Add a new announcement to all 5 JSON files with auto-translation. Use when adding announcements, writing update notices, or posting new content.
---

# Add Announcement Workflow

## Step 1 — Gather Input

Ask the user for:
- **Korean title** (`kr_title`)
- **Korean body** (`kr_body`)
- **Date** — default today `YYYY-MM-DD` if not provided
- **expiresAt** (`YYYY-MM-DD`) — optional, skip if not provided

## Step 2 — Generate ID

**Format:** `{date}-{NN}` where NN is a zero-padded 2-digit incremental counter.

To determine NN:
1. Read `static/data/announcements.json`
2. The array is newest-first — inspect the `id` of the first entry
3. If the first entry's date matches today's date, increment its counter; otherwise start at `01`
4. Example: existing `"2026-02-20-02"` → new ID is `"2026-02-20-03"`

## Step 3 — Translate

Before translating, read the term dictionary at `.claude/skills/add-announcement/terms.csv`.
It contains canonical translations for:
- Sinner names (12 characters)
- Game terms: 인격 → Identity/人格/人格, E.G.O 기프트 → E.G.O Gifts/E.G.Oギフト/E.G.O饰品, E.G.O
- Mirror dungeon names (mirrors 1–7)
- Difficulty modes: 일반/Normal/ノーマル/普通, 어려움/Hard/ハード/困难, 평행중첩, etc.

For identity names, EGO names, and EGO gift names — read the name list files directly:
- `static/i18n/{lang}/identityNameList.json` (lang: KR, EN, JP, CN)
- `static/i18n/{lang}/egoNameList.json`
- `static/i18n/{lang}/egoGiftNameList.json`

These files use numeric IDs as keys. Identify which IDs appear in the Korean text, then read the corresponding entries in EN/JP/CN files.

Translate `kr_title` and `kr_body` into EN, CN, JP using the dictionaries above for all game-specific terms. Use natural-sounding language for surrounding prose.

## Step 4 — Write All 5 Files

Read each file before editing. JSON must remain valid (no trailing commas).

### `static/data/announcements.json`
Prepend a new object at index 0 (newest-first):
```json
{
  "id": "<generated-id>",
  "date": "<date>"
}
```
Include `"expiresAt": "<date>"` only if provided.

### `static/i18n/KR/announcements.json`
Add key `"<id>": { "title": "<kr_title>", "body": "<kr_body>" }` alongside existing entries.

### `static/i18n/EN/announcements.json`
Add key `"<id>": { "title": "<en_title>", "body": "<en_body>" }`.

### `static/i18n/CN/announcements.json`
Add key `"<id>": { "title": "<cn_title>", "body": "<cn_body>" }`.

### `static/i18n/JP/announcements.json`
Add key `"<id>": { "title": "<jp_title>", "body": "<jp_body>" }`.

## Step 5 — Confirm

List all 5 files changed and display the generated ID.
