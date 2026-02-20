# Plan: Home Page Announcement Section

## Planning Gaps

None. Proceed.

---

## Execution Overview

- 22 steps across 5 phases
- 11 new files, 7 modified files
- Frontend-only — no backend changes
- All modifications to shared files are strictly additive
- No existing feature is broken

---

## Dependency Analysis

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `lib/constants.ts` | High (shared) | Nothing | Every component in codebase |
| `lib/formatDate.ts` | Low (additive) | `lib/constants.ts` | Components using date formatting |
| `static/i18n/*/common.json` ×4 | Low (additive) | Nothing | All components via i18next |
| `routes/HomePage.tsx` | Low (page-isolated) | `AnnouncementContent`, `AnnouncementSkeleton` | `lib/router.tsx` (route mount only) |

### Ripple Effect Map

- `lib/constants.ts` — new `ANNOUNCEMENT_PREVIEW_COUNT` is additive; no existing consumers change
- `lib/formatDate.ts` — new `formatAnnouncementDate()` is additive; zero impact on existing exports
- `static/i18n/*/common.json` — new `announcements.*` key block; all existing keys untouched
- `HomePage.tsx` — new Suspense block inserted between banner and two-column grid; existing blocks not moved

### High-Risk Modifications

| File | Risk | Mitigation |
|------|------|------------|
| `lib/constants.ts` | Syntax error breaks entire frontend build | Read surrounding lines first; additive only |
| `static/i18n/*/common.json` | Malformed JSON silently breaks all i18n | Read full file before editing; no trailing commas |
| `lib/formatDate.ts` | Local `LOCALE_MAP` (line 103) uses `'ja'`, not `'ja-JP'` — must use `I18N_LOCALE_MAP` from constants instead | Import `I18N_LOCALE_MAP` from `@/lib/constants`; do not use local map |

---

## Execution Order

### Phase 1: Data Layer

**Step 1** — Create `static/data/announcements.json`
- Content: array with one real entry; `expiresAt` omitted on initial entry
- Enables: hook to have a real data source

**Step 2** — Create `static/i18n/KR/announcements.json`
- Content: `Record<id, { title, body }>` matching Step 1 id
- Enables: F1, F5 (Korean language content)

**Step 3** — Create `static/i18n/EN/announcements.json`

**Step 4** — Create `static/i18n/CN/announcements.json`

**Step 5** — Create `static/i18n/JP/announcements.json`

**Step 6** — Modify `static/i18n/EN/common.json` — add `announcements.*` keys: `title`, `viewAll`, `backToList`, `noAnnouncements`
- Risk: read full file first; validate JSON after edit

**Step 7** — Modify `static/i18n/KR/common.json` — same 4 keys in Korean

**Step 8** — Modify `static/i18n/CN/common.json` — same 4 keys in Chinese

**Step 9** — Modify `static/i18n/JP/common.json` — same 4 keys in Japanese

**Step 10** — Create `frontend/src/types/AnnouncementTypes.ts`
- Pattern: `types/StartBuffTypes.ts` — interface naming, JSDoc, export style
- Interfaces: `AnnouncementSpec`, `AnnouncementI18nEntry`, `Announcement`

**Step 11** — Create `frontend/src/schemas/AnnouncementSchemas.ts`
- Pattern: `schemas/ExtractionSchemas.ts` — `z.object()`, section structure, `z.infer<>` exports
- Schemas: `AnnouncementSpecSchema`, `AnnouncementSpecListSchema`, `AnnouncementI18nEntrySchema`, `AnnouncementI18nSchema`

---

### Phase 2: Logic Layer

**Step 12** — Modify `frontend/src/lib/constants.ts` — add `ANNOUNCEMENT_PREVIEW_COUNT = 1`
- Additive only; read surrounding lines before editing

**Step 13** — Modify `frontend/src/lib/formatDate.ts` — add `formatAnnouncementDate(dateStr: string, language: string): string`
- Parse `"YYYY-MM-DD"` → split by `-` → `new Date(y, m-1, d)` → `toLocaleDateString(I18N_LOCALE_MAP[language] ?? 'en-US', { year, month: 'short', day: 'numeric' })`
- Import `I18N_LOCALE_MAP` from `@/lib/constants` — do NOT use the file-local `LOCALE_MAP`
- Depends on: Step 12

**Step 14** — Create `frontend/src/hooks/useAnnouncementData.ts`
- Pattern: `hooks/useStartBuffData.ts` — query key factory, `queryOptions`, dual `useSuspenseQuery`, merge loop
- Differences: no version param; spec is flat array (use `array.map` not `Object.entries`); hook owns expiry filter and sort; on i18n miss → skip entry + `console.error`
- `staleTime`: 7 days for both spec and i18n queries
- Depends on: Steps 10, 11, 12, 13

---

### Phase 3: Interface Layer

**Step 15** — Create `frontend/src/components/home/AnnouncementSection.tsx`
- Pattern: `components/home/CommunityPlansSection.tsx` — section wrapper, `CommunityPlansSkeleton` structure
- Exports: `AnnouncementSection` (props: `latest: Announcement`, `onViewAll: () => void`) and `AnnouncementSkeleton` (named export, no props)
- Skeleton shape: title line `h-5 w-48`, date line `h-4 w-24`, button placeholder `h-9 w-24`
- Depends on: Step 10

**Step 16** — Create `frontend/src/components/home/AnnouncementDialog.tsx`
- Pattern: `components/notifications/NotificationDialog.tsx` — Dialog wrapper, DialogContent/Header/Title, inner list function
- Props: `announcements: Announcement[]`, `open: boolean`, `onOpenChange: (open: boolean) => void`
- Internal state: `selectedId: string | null` (null = list view)
- Reset: `useEffect(() => setSelectedId(null), [open])`
- List view: rows with title + formatted date, `cursor-pointer hover:bg-accent`
- Detail view: title, date, `<p className="whitespace-pre-wrap">` for body, Back button
- Dialog scrollable body: `overflow-y-auto max-h-[60vh]`
- Depends on: Steps 10, 13

**Step 17** — Create `frontend/src/components/home/AnnouncementContent.tsx`
- Calls `useAnnouncementData()`; owns `dialogOpen: boolean` state
- Returns `null` if `announcements.length === 0`
- Otherwise renders `AnnouncementSection` + `AnnouncementDialog`
- Depends on: Steps 14, 15, 16

---

### Phase 4: Integration

**Step 18** — Modify `frontend/src/routes/HomePage.tsx`
- Add imports: `AnnouncementContent` from `AnnouncementContent.tsx`, `AnnouncementSkeleton` from `AnnouncementSection.tsx`
- Insert between banner `div` and two-column `grid` div: `<div className="mb-8"><Suspense fallback={<AnnouncementSkeleton />}><AnnouncementContent /></Suspense></div>`
- Do not move or modify any existing blocks
- Depends on: Steps 15, 17

---

### Phase 5: Tests

**Step 19** — Write tests for `useAnnouncementData.ts`
- Expired entries filtered out; newest-first sort; empty result returns `[]`; i18n miss skips entry

**Step 20** — Write tests for `AnnouncementSection.tsx`
- Renders title and date; "View All" triggers callback; `AnnouncementSkeleton` renders without props

**Step 21** — Write tests for `AnnouncementDialog.tsx`
- Opens in list view; row click → detail view; Back → list view; close+reopen resets state; empty list shows `noAnnouncements`

**Step 22** — Write tests for `AnnouncementContent.tsx`
- Returns null for empty array; renders section + dialog when data present

---

## Verification Checkpoints

| After Step | Verify |
|------------|--------|
| Steps 1-9 | All JSON files valid; i18n keys present in all 4 languages |
| Step 11 | TypeScript compiles; Zod schema shapes match Step 1 JSON |
| Step 13 | `formatAnnouncementDate("2026-02-20", "KR")` returns Korean locale format |
| Step 14 | Hook returns sorted, filtered `Announcement[]` |
| Step 15 | `AnnouncementSkeleton` visual shape matches `AnnouncementSection` layout |
| Step 17 | Returns null for empty input; renders both children otherwise |
| Step 18 | Section appears between banner and two-column grid; Suspense skeleton visible during load |
| Step 22 | All tests pass; `yarn typecheck` clean |

---

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| JSON syntax error in `common.json` | 6-9 | Read full file before edit; no trailing commas |
| Syntax error in `constants.ts` | 12 | Additive only; read surrounding lines first |
| Wrong `LOCALE_MAP` used in `formatDate.ts` | 13 | Import `I18N_LOCALE_MAP` from constants; local map uses `'ja'` not `'ja-JP'` |
| `expiresAt` timezone edge at day boundary | 14 | Accepted; document in comment |
| Dialog overflow on long body | 16 | `overflow-y-auto max-h-[60vh]` on scrollable container |
| Wrong insertion point in `HomePage.tsx` | 18 | Read full file first; insert between banner div and grid div |

---

## Pre-Implementation Validation Gate

- [ ] Read `static/i18n/EN/common.json` in full — identify insertion point for `"announcements"` key
- [ ] Read `lib/constants.ts` near line 1021 — confirm `I18N_LOCALE_MAP` and safe insertion for new constant
- [ ] Read `lib/formatDate.ts` in full — confirm no existing `formatAnnouncementDate`; note local `LOCALE_MAP` at line 103
- [ ] Confirm `static/data/announcements.json` does not already exist
- [ ] Verify Zod schema field names match Step 1 JSON before writing `AnnouncementSchemas.ts`

---

## Rollback Strategy

- New files: `git rm` — zero risk to existing functionality
- `lib/constants.ts`: remove single added line
- `lib/formatDate.ts`: remove new function
- `routes/HomePage.tsx`: remove two imports and the Suspense block
- `static/i18n/*/common.json`: remove `"announcements"` key block from each file
- No backend changes, no DB migrations — full rollback is safe at any step
