# Task: Home Page Announcement Section

## Description

Add an announcement section to the home page, positioned between the banner carousel and the two-column layout (Recently Released + Community Plans).

### Component Responsibilities

| Component | Responsibility |
|-----------|----------------|
| `AnnouncementContent.tsx` | Calls `useAnnouncementData`, owns `dialogOpen` state, renders `AnnouncementSection` + `AnnouncementDialog`. Imported into `HomePage.tsx`. |
| `AnnouncementSection.tsx` | Purely presentational — receives `latest: Announcement` and `onViewAll: () => void` as props. Also exports `AnnouncementSkeleton`. |
| `AnnouncementSkeleton` | Exported from `AnnouncementSection.tsx`. Matches the visual shape of the section: a title line, a date line, and a button placeholder. Used by `HomePage.tsx` as the Suspense fallback. |
| `AnnouncementDialog.tsx` | Receives `announcements[]`, `open`, `onOpenChange` as props; owns `selectedId` state internally |

`HomePage.tsx` adds `<Suspense fallback={<AnnouncementSkeleton />}><AnnouncementContent /></Suspense>` — imports both `AnnouncementContent` and `AnnouncementSkeleton`, no logic inline.

### Home Section Behavior
- Displays the **single most recent** (non-expired) announcement as a preview: title + date
- Shows a **"View All"** button that opens the announcement dialog
- If the announcements list is empty (or all are expired), the section **renders nothing** — do not show an empty placeholder

### Dialog Behavior (Two-State)
The dialog has two views controlled by a selected announcement state:

**List View** (default on open):
- Shows all non-expired announcements, sorted newest first
- Each row shows: title + date
- Clicking a row transitions to Detail View for that announcement
- Closing the dialog resets to list view

**Detail View** (after clicking an announcement):
- Shows the selected announcement: title, date, full body text
- Body is plain text (support newlines with `white-space: pre-wrap`)
- A **"Back"** button returns to List View without closing the dialog

### Data Structure
- Spec file (`static/data/announcements.json`): array of `{ id, date, expiresAt? }`
  - `id`: slug string (e.g., `"2026-02-19-md7-patch"`)
  - `date`: `"YYYY-MM-DD"` string
  - `expiresAt`: optional `"YYYY-MM-DD"` string; if set and in the past, hide the announcement
- i18n files (`static/i18n/{lang}/announcements.json`): `Record<id, { title, body }>`
  - Written in Korean, LLM-translated to EN, CN, JP

### i18n UI Strings (common.json)
Add under key `"announcements"`:
- `title`: section/dialog header label (e.g., "Updates")
- `viewAll`: button text to open dialog
- `backToList`: button text to return to list view
- `noAnnouncements`: empty state text for the dialog list

### Date Formatting (Locale-Aware)

The codebase uses two patterns for locale-aware date display — pick the right one:

**Pattern 1: `I18N_LOCALE_MAP` + `Intl.DateTimeFormat` / `toLocaleDateString`**
- `I18N_LOCALE_MAP` in `lib/constants.ts` maps app language codes to BCP 47 locale strings:
  - `KR → 'ko-KR'`, `JP → 'ja-JP'`, `CN → 'zh-CN'`, `EN → 'en-US'`
- Used by `formatEntityReleaseDate(dateInt: number, locale: string)` in `lib/formatDate.ts`:
  - Takes a `YYYYMMDD` integer (e.g., `20250109`), converts to localized `"Jan 9, 2025"` / `"2025. 1. 9."` etc.
  - Passes `locale` string to `toLocaleDateString(locale, { year, month, day })`
- Usage in hooks: `formatEntityReleaseDate(date, I18N_LOCALE_MAP[language] ?? 'en-US')`

**Pattern 2: `Intl.DateTimeFormat(undefined, ...)` (browser locale, not app language)**
- Used by `formatPlannerDate` and `formatFullDate` in `lib/formatDate.ts`
- Passes `undefined` as locale → uses the browser's locale, not the app's selected language
- **Do not use this pattern for announcements** — it ignores the user's in-app language selection

**For announcements**, the `date` field is `"YYYY-MM-DD"` — neither existing helper takes this format directly. The implementer should:
1. Parse `"YYYY-MM-DD"` into year/month/day parts
2. Format using `new Date(year, month - 1, day).toLocaleDateString(I18N_LOCALE_MAP[i18n.language] ?? 'en-US', { year: 'numeric', month: 'short', day: 'numeric' })`
3. Consider whether to add a `formatAnnouncementDate(dateStr: string, language: string)` helper to `lib/formatDate.ts`, or inline the conversion in the hook

---

## Research

- `useStartBuffData.ts` — primary pattern for spec + i18n loading via `useSuspenseQuery` and dynamic import
- `NotificationDialog.tsx` — existing dialog with scrollable list inside
- `HomePage.tsx` — how `RecentlyReleasedContent` inner component + Suspense boundary pattern works
- `BannerSection.tsx` — styling conventions for home page sections
- `lib/formatDate.ts` — all date formatting utilities; `formatEntityReleaseDate` is the closest pattern
- `lib/constants.ts` — `I18N_LOCALE_MAP` for language → BCP 47 locale mapping
- `static/i18n/EN/common.json` `pages.home.*` — where to add i18n UI strings
- `frontend/src/lib/constants.ts` — where to add `ANNOUNCEMENT_PREVIEW_COUNT`
- `frontend/src/schemas/ExtractionSchemas.ts` — reference for Zod schema style
- shadcn/ui `Dialog` component pattern (`.claude/rules/frontend/components/shadcn-ui.md`)

---

## Scope

Files to READ for context:

- `frontend/src/routes/HomePage.tsx`
- `frontend/src/hooks/useHomePageData.ts`
- `frontend/src/hooks/useStartBuffData.ts`
- `frontend/src/components/notifications/NotificationDialog.tsx`
- `frontend/src/components/home/BannerSection.tsx`
- `frontend/src/schemas/ExtractionSchemas.ts`
- `frontend/src/lib/constants.ts`
- `frontend/src/lib/formatDate.ts`
- `static/i18n/EN/common.json`

---

## Target Code Area

**New files:**

| File | Purpose |
|------|---------|
| `static/data/announcements.json` | Spec list: `[{ id, date, expiresAt? }]` |
| `static/i18n/KR/announcements.json` | KR title + body per announcement id |
| `static/i18n/EN/announcements.json` | EN title + body per announcement id |
| `static/i18n/CN/announcements.json` | CN title + body per announcement id |
| `static/i18n/JP/announcements.json` | JP title + body per announcement id |
| `frontend/src/types/AnnouncementTypes.ts` | TypeScript interfaces |
| `frontend/src/schemas/AnnouncementSchemas.ts` | Zod schemas for spec list and i18n |
| `frontend/src/hooks/useAnnouncementData.ts` | Hook: load spec + i18n, filter expired, sort desc |
| `frontend/src/components/home/AnnouncementSection.tsx` | Latest preview + "View All" button |
| `frontend/src/components/home/AnnouncementDialog.tsx` | Two-state list/detail dialog |
| `frontend/src/components/home/AnnouncementContent.tsx` | Orchestrator: loads data, owns dialog open state, renders Section + Dialog |

**Modified files:**

| File | Change |
|------|--------|
| `frontend/src/lib/constants.ts` | Add `ANNOUNCEMENT_PREVIEW_COUNT = 1` |
| `static/i18n/EN/common.json` | Add `announcements.*` UI strings |
| `static/i18n/KR/common.json` | Same in Korean |
| `static/i18n/CN/common.json` | Same in Chinese |
| `static/i18n/JP/common.json` | Same in Japanese |
| `frontend/src/routes/HomePage.tsx` | Import and render `<AnnouncementContent />` wrapped in Suspense — no logic added inline |

---

## System Context (Senior Thinking)

- **Feature domain**: Home Page
- **Core files in domain**: `routes/HomePage.tsx`, `hooks/useHomePageData.ts`, `components/home/BannerSection.tsx`, `components/home/RecentlyReleasedSection.tsx`, `components/home/CommunityPlansSection.tsx`
- **Cross-cutting concerns touched**:
  - i18n: new per-language `announcements.json` files + `common.json` UI strings
  - Validation: Zod schema for spec and i18n structure
  - TanStack Query: `useSuspenseQuery` with 7-day staleTime (static content)

---

## Impact Analysis

- Files being modified:
  - `routes/HomePage.tsx` — Low impact (page-isolated; adding a new inner component)
  - `lib/constants.ts` — Low impact (additive only)
  - `static/i18n/*/common.json` — Low impact (additive only)
- What depends on these files: Only `HomePage` depends on `useAnnouncementData`
- Potential ripple effects: None — all new files are isolated to the announcement feature
- High-impact files to watch: None; `constants.ts` is high-impact but the change is additive

---

## Risk Assessment

- **`expiresAt` edge case**: If a deployment is made on exactly the expiry date, timezone differences may cause inconsistent visibility. Client-side `new Date()` comparison is UTC in JS; date strings are compared as local midnight. Document this and accept the simplification.
- **Empty announcements list**: If `announcements.json` is empty or all entries expired, the section must disappear entirely — not show an empty container.
- **Body text length**: Body text is plain string, potentially long. Dialog needs `overflow-y-auto` with max-height to prevent the modal from exceeding viewport.
- **i18n fallback**: If the i18n file for the current language lacks an entry for an id, the hook should skip that announcement (or fall back to KR). Decide on fallback strategy before implementation.
- **staleTime**: Set to 7 days (matches `useStartBuffData`). Announcements are static content that only changes on deployment.
- **No backend dependency**: Entire feature is static — no API calls, no auth required.

---

## Testing Guidelines

### Manual UI Testing

1. Open the home page (`/`)
2. Verify an announcement preview section appears below the banner and above the two-column grid
3. Verify it shows the most recent announcement's title and date
4. Click the "View All" button
5. Verify the dialog opens in **List View** showing all announcements (title + date per row)
6. Click any announcement row in the list
7. Verify the view transitions to **Detail View** showing: title, date, full body text
8. Click the "Back" button
9. Verify it returns to **List View** (not closing the dialog)
10. Close the dialog (X button or click outside)
11. Reopen via "View All"
12. Verify it opens in **List View** again (state resets on close)

### Language Testing

13. Switch the app language to KR/EN/CN/JP
14. Verify announcement title and body text change accordingly
15. Verify UI strings ("View All", "Back", etc.) also localize correctly

### Automated Functional Verification

- [ ] Latest announcement: Home section shows only 1 announcement (the most recent)
- [ ] Sort order: Dialog list shows announcements sorted newest-first
- [ ] Expiry filtering: Announcements with `expiresAt` in the past are hidden from both section and dialog
- [ ] Empty state: When all announcements are expired (or list is empty), home section renders nothing
- [ ] Dialog reset: Closing and reopening the dialog always starts at List View
- [ ] Zod validation: Invalid spec or i18n structure logs an error (via `validateData`)

### Edge Cases

- [ ] Empty `announcements.json` array: Section is not rendered at all
- [ ] All announcements expired: Same as above — section invisible
- [ ] Single announcement: List view shows 1 item; "Back" button works correctly
- [ ] Very long body text: Detail view scrolls vertically; dialog does not exceed viewport
- [ ] Missing i18n entry for an id: Decide behavior (skip or fallback) before implementation — document in hook

### Integration Points

- [ ] Home page load: Announcement data loads within the same Suspense boundary; does not block other sections
- [ ] Language switch: Announcement content updates when user changes language without page reload
