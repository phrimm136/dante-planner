# Code: Home Page Announcement Section

## What Was Done
- Created full data layer: spec JSON, 4 language i18n files, Zod schemas, TypeScript types
- Added `announcements.*` UI string keys to all 4 common.json files (EN/KR/CN/JP)
- Added `formatAnnouncementDate()` to `formatDate.ts`; merged local `LOCALE_MAP` into `I18N_LOCALE_MAP`
- Built hook `useAnnouncementData` — loads spec + i18n, filters expired, sorts newest-first, merges
- Built 3 components: `AnnouncementSection` (preview list + skeleton), `AnnouncementDialog` (list/detail), `AnnouncementContent` (orchestrator)
- Wired into `HomePage.tsx` between banner and two-column grid via Suspense boundary
- Added title buttons in section for direct deep-link into dialog detail view

## Files Changed

**New:**
- `static/data/announcements.json`
- `static/i18n/{EN,KR,CN,JP}/announcements.json`
- `frontend/src/types/AnnouncementTypes.ts`
- `frontend/src/schemas/AnnouncementSchemas.ts`
- `frontend/src/hooks/useAnnouncementData.ts`
- `frontend/src/components/home/AnnouncementSection.tsx`
- `frontend/src/components/home/AnnouncementDialog.tsx`
- `frontend/src/components/home/AnnouncementContent.tsx`
- `frontend/src/hooks/useAnnouncementData.test.ts`
- `frontend/src/components/home/AnnouncementSection.test.tsx`
- `frontend/src/components/home/AnnouncementDialog.test.tsx`
- `frontend/src/components/home/AnnouncementContent.test.tsx`

**Modified:**
- `frontend/src/lib/constants.ts` — added `ANNOUNCEMENT_PREVIEW_COUNT = 5`
- `frontend/src/lib/formatDate.ts` — added `formatAnnouncementDate`, removed local `LOCALE_MAP`
- `frontend/src/routes/HomePage.tsx` — inserted Suspense-wrapped announcement block
- `static/i18n/{EN,KR,CN,JP}/common.json` — added `announcements.*` keys
- `.claude/rules/frontend/data/stale-time.md` — updated stale time rules (static game data → 7 days)

## Verification Results
- Typecheck: PASS
- Tests: PASS (853 total, 0 failures)
- JSON validation: all files valid
- Manual checklist: all 22 plan steps completed

## Issues & Resolutions
- Phase 2 agent only completed Step 12 (constants) — Steps 13 and 14 added manually
- `ANNOUNCEMENT_PREVIEW_COUNT` JSDoc said "always 1" but value was 5 — comment corrected; user confirmed 5 is intentional
- Skeleton hardcoded 3 rows regardless of `ANNOUNCEMENT_PREVIEW_COUNT` — fixed to use constant
- Hook tests only covered schemas/query keys; business logic (expiry, sort, i18n-miss) had zero coverage — added UT1–UT4 via `useSuspenseQuery` mock
- Local `LOCALE_MAP` in `formatDate.ts` used `'ja'`/`'ko'` instead of `'ja-JP'`/`'ko-KR'` — removed and unified under `I18N_LOCALE_MAP`
- Test bug: `onOpenAnnouncement={vi.fn()}` passed inline (not the spy variable) — user fixed to pass named variable
