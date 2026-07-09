# Status: Home Page Announcement Section

## Execution Progress

Last Updated: 2026-02-20
Current Step: 0 / 22
Current Phase: Not Started

---

## Milestones

| Milestone | Steps | Status |
|-----------|-------|--------|
| M1: Data layer ready | 1–11 | ⏳ Pending |
| M2: Logic layer ready | 12–14 | ⏳ Pending |
| M3: Components ready | 15–17 | ⏳ Pending |
| M4: Integration complete | 18 | ⏳ Pending |
| M5: Tests passing | 19–22 | ⏳ Pending |

---

## Step Log

| Step | File | Status |
|------|------|--------|
| 1 | `static/data/announcements.json` | ⏳ |
| 2 | `static/i18n/KR/announcements.json` | ⏳ |
| 3 | `static/i18n/EN/announcements.json` | ⏳ |
| 4 | `static/i18n/CN/announcements.json` | ⏳ |
| 5 | `static/i18n/JP/announcements.json` | ⏳ |
| 6 | `static/i18n/EN/common.json` | ⏳ |
| 7 | `static/i18n/KR/common.json` | ⏳ |
| 8 | `static/i18n/CN/common.json` | ⏳ |
| 9 | `static/i18n/JP/common.json` | ⏳ |
| 10 | `frontend/src/types/AnnouncementTypes.ts` | ⏳ |
| 11 | `frontend/src/schemas/AnnouncementSchemas.ts` | ⏳ |
| 12 | `frontend/src/lib/constants.ts` | ⏳ |
| 13 | `frontend/src/lib/formatDate.ts` | ⏳ |
| 14 | `frontend/src/hooks/useAnnouncementData.ts` | ⏳ |
| 15 | `frontend/src/components/home/AnnouncementSection.tsx` | ⏳ |
| 16 | `frontend/src/components/home/AnnouncementDialog.tsx` | ⏳ |
| 17 | `frontend/src/components/home/AnnouncementContent.tsx` | ⏳ |
| 18 | `frontend/src/routes/HomePage.tsx` | ⏳ |
| 19 | Hook tests | ⏳ |
| 20 | AnnouncementSection tests | ⏳ |
| 21 | AnnouncementDialog tests | ⏳ |
| 22 | AnnouncementContent tests | ⏳ |

---

## Feature Status

### Core Features

| Feature | Status |
|---------|--------|
| F1: Latest announcement preview on home page (title + date) | ⏳ |
| F2: "View All" button opens dialog | ⏳ |
| F3: Dialog list view — all announcements, newest first | ⏳ |
| F4: Dialog detail view — full body, Back button returns to list | ⏳ |
| F5: Language switch updates title/body/UI strings | ⏳ |
| F6: Suspense skeleton shows during initial load | ⏳ |

### Edge Cases

| Edge Case | Status |
|-----------|--------|
| E1: All announcements expired → section not rendered | ⏳ |
| E2: Empty `announcements.json` → section not rendered | ⏳ |
| E3: Single announcement → list + detail both work | ⏳ |
| E4: Long body text → dialog scrolls, no viewport overflow | ⏳ |
| E5: i18n entry missing for an id → skip + log, no crash | ⏳ |
| E6: Close + reopen dialog → resets to list view | ⏳ |

### Integration

| Check | Status |
|-------|--------|
| I1: Section positioned between banner and two-column grid | ⏳ |
| I2: Skeleton matches visual shape of rendered section | ⏳ |
| I3: Dialog resets to list on close | ⏳ |

### Dependency Verification

| Dependency | Verified |
|------------|----------|
| D1: `I18N_LOCALE_MAP` confirmed at `constants.ts:1021` | ✅ |
| D2: Local `LOCALE_MAP` in `formatDate.ts:103` uses `'ja'` — new function uses `I18N_LOCALE_MAP` | ✅ |
| D3: `useStartBuffData.ts` query key factory pattern confirmed | ✅ |
| D4: `NotificationDialog.tsx` Dialog wrapper pattern confirmed | ✅ |
| D5: `CommunityPlansSection.tsx` skeleton pattern confirmed | ✅ |

---

## Testing Checklist

### Automated Tests

- [ ] UT1: Hook — expired entries filtered out
- [ ] UT2: Hook — sort order newest-first
- [ ] UT3: Hook — empty input returns `[]`
- [ ] UT4: Hook — i18n miss skips entry, does not throw
- [ ] UT5: Section — renders latest title and date
- [ ] UT6: Section — "View All" triggers `onViewAll`
- [ ] UT7: Skeleton — renders without props
- [ ] UT8: Dialog — opens in list view
- [ ] UT9: Dialog — row click transitions to detail view
- [ ] UT10: Dialog — Back returns to list without closing
- [ ] UT11: Dialog — close+reopen resets to list view
- [ ] UT12: Dialog — empty list shows `noAnnouncements` string
- [ ] UT13: Content — returns null for empty array
- [ ] UT14: Content — renders section + dialog when data present

### Manual Verification

- [ ] MV1: Section visible between banner and two-column grid on `/`
- [ ] MV2: Latest title and date are correct
- [ ] MV3: "View All" opens dialog in list view
- [ ] MV4: Clicking a row shows detail view with full body
- [ ] MV5: Back button returns to list view
- [ ] MV6: Close + reopen resets to list view
- [ ] MV7: Language switch — title/body/UI strings all update
- [ ] MV8: Long body text — dialog scrolls, stays in viewport

---

## Summary

Steps: 0 / 22 complete
Features: 0 / 6 verified
Edge cases: 0 / 6 verified
Tests: 0 / 14 passing
Overall: 0%
