# Research: Home Page Announcement Section

## Spec Ambiguities

None. Spec is clear and complete.

---

## Spec-to-Code Mapping

- Spec file `{ id, date, expiresAt? }` → static JSON, dynamic import in hook
- i18n `Record<id, { title, body }>` → separate per-language dynamic import
- UI strings → `static/i18n/*/common.json` under `announcements.*`
- Hook → copy `useStartBuffData.ts` (no version param, add expiry filter + sort)
- Schema → copy `ExtractionSchemas.ts` Zod style
- Dialog → copy `NotificationDialog.tsx`, add two-state list/detail logic
- Section → copy `BannerSection.tsx` / `RecentlyReleasedSection.tsx` section styling
- `AnnouncementContent.tsx` (new) → orchestrator component; imported by `HomePage.tsx`
- `AnnouncementSkeleton` (exported from `AnnouncementSection.tsx`) → used as Suspense fallback in `HomePage.tsx`
- `HomePage.tsx` → import `AnnouncementContent` + `AnnouncementSkeleton`; `<Suspense fallback={<AnnouncementSkeleton />}>`

---

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `useAnnouncementData.ts` | `useStartBuffData.ts` | query key factory, dual `queryOptions`, `useSuspenseQuery`, spec+i18n merge |
| `AnnouncementSchemas.ts` | `ExtractionSchemas.ts` | `z.object()`, `.strict()`, export style |
| `AnnouncementTypes.ts` | `types/StartBuffTypes.ts` | interface naming, export style |
| `AnnouncementDialog.tsx` | `NotificationDialog.tsx` | Dialog wrapper, inner list function, Suspense + skeleton |
| `AnnouncementSection.tsx` | `components/home/CommunityPlansSection.tsx` | section layout, skeleton pattern (`CommunityPlansSkeleton`) — export `AnnouncementSkeleton` alongside section |
| `AnnouncementContent.tsx` | current `HomePage.tsx` | import hook + render Section/Dialog; no logic inlined in page |
| `HomePage.tsx` (modify) | current `HomePage.tsx` | import `AnnouncementContent` + `AnnouncementSkeleton`; wrap in `<Suspense fallback={<AnnouncementSkeleton />}>` |

---

## Pattern Copy Deep Analysis

### useStartBuffData.ts → useAnnouncementData.ts

- Reference structure: query key factory → two `queryOptions` functions → hook calls both with `useSuspenseQuery` → merges result → returns data
- Key differences from reference:
  - No version parameter (announcements are global, not MD-version-specific)
  - Spec is a flat array `[]`, not a `Record<id, ...>` — iterate with `array.map()` not `Object.entries()`
  - Hook owns filtering (expired) and sorting (newest-first) — not delegated to component
  - Date field is `"YYYY-MM-DD"` string, not YYYYMMDD integer

### NotificationDialog.tsx → AnnouncementDialog.tsx

- Reference structure: props `{ open, onOpenChange }` → Dialog wrapper → DialogHeader → Suspense with skeleton → inner list component
- Key differences from reference:
  - Add `selectedId: string | null` state (null = list view, non-null = detail view)
  - List view: rows of title + date, each clickable → sets `selectedId`
  - Detail view: title, date, body text (`whitespace-pre-wrap`), Back button → sets `selectedId` to null
  - No mutations (read-only) — remove all delete/clear logic
  - Reset `selectedId` to null on dialog close via `useEffect([open])`

---

## Existing Utilities

| Category | Location | Relevant Items |
|----------|----------|----------------|
| Date formatters | `lib/formatDate.ts` | `formatEntityReleaseDate` (YYYYMMDD int only — not directly usable), `formatPlannerDate`, `formatRelativeTime` |
| Locale mapping | `lib/constants.ts:1021` | `I18N_LOCALE_MAP` (`KR→ko-KR`, `JP→ja-JP`, `CN→zh-CN`, `EN→en-US`) |
| Validation | inline `.safeParse()` | No central `lib/validation.ts` — validate inline in hook |
| Constants | `lib/constants.ts` | Add `ANNOUNCEMENT_PREVIEW_COUNT = 1` |

**Date formatting gap**: No existing helper takes `"YYYY-MM-DD"` string. Either add `formatAnnouncementDate(dateStr, language)` to `lib/formatDate.ts` or parse inline in hook. Prefer adding to `lib/formatDate.ts` to keep formatting logic centralized.

---

## Gap Analysis

| Need | Status | Action |
|------|--------|--------|
| `AnnouncementTypes.ts` | Missing | Create |
| `AnnouncementSchemas.ts` | Missing | Create |
| `useAnnouncementData.ts` | Missing | Create |
| `AnnouncementDialog.tsx` | Missing | Create |
| `AnnouncementSection.tsx` | Missing | Create |
| `static/data/announcements.json` | Missing | Create |
| `static/i18n/*/announcements.json` (×4) | Missing | Create |
| `lib/formatDate.ts` | Partial | Add `formatAnnouncementDate(dateStr, language)` |
| `lib/constants.ts` | Partial | Add `ANNOUNCEMENT_PREVIEW_COUNT = 1` |
| `static/i18n/*/common.json` (×4) | Partial | Add `announcements.*` UI strings (4 keys × 4 langs) |
| `routes/HomePage.tsx` | Partial | Import `AnnouncementContent` + `AnnouncementSkeleton`; add `<Suspense fallback={<AnnouncementSkeleton />}><AnnouncementContent /></Suspense>` |
| `AnnouncementSkeleton` (in `AnnouncementSection.tsx`) | Missing | Export skeleton matching section shape: title line, date line, button placeholder |

---

## Technical Constraints

- **staleTime**: 7 days — matches `useStartBuffData`, static content only changes on deploy
- **i18n fallback**: If language file missing an id entry, skip that announcement — log error, do not crash
- **Date parsing**: `"YYYY-MM-DD"` → split by `-`, construct `new Date(y, m-1, d)` (local midnight, no timezone conversion)
- **Expiry**: `expiresAt && new Date(expiresAt) < new Date()` — accept timezone edge case at day boundary, document in comments
- **Empty state**: `if (announcements.length === 0) return null` — no container rendered
- **Dialog reset**: `useEffect(() => { setSelectedId(null) }, [open])` — resets to list view on close
- **Body display**: `whitespace-pre-wrap` Tailwind class on body `<p>` element
- **Sort**: descending by `date` string (ISO format sorts lexicographically = correct)
