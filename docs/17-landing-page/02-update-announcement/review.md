# Review: Home Page Announcement Section

## Overall Verdict: ACCEPTABLE

## Domain Summary

| Domain | Verdict | Critical | High | Resolved |
|--------|---------|----------|------|----------|
| Security | ACCEPTABLE | 0 | 0 | — |
| Architecture | ACCEPTABLE | 0 | 1 | ✅ |
| Performance | ACCEPTABLE | 0 | 0 | — |
| Reliability | ACCEPTABLE | 0 | 2 | ✅ |
| Consistency | ACCEPTABLE | 0 | 1 | ✅ |

## Spec-Driven Compliance

- Spec-to-Code Mapping: all 11 new files and 7 modified files completed per plan
- Spec-to-Pattern Mapping: hook, dialog, section, and content all follow named pattern sources
- Technical Constraints: whitespace-pre-wrap, dialog reset, null on empty, sort, staleTime 7d — all respected
- Execution Order: all 22 steps verifiably completed

## Issues & Resolutions

**Architecture — HIGH → RESOLVED**
AnnouncementDialog inlined list/detail state and rendering in a single function, violating SRP and diverging from the NotificationDialog inner-component pattern.
Resolution: extracted AnnouncementDialogContent as a separate inner component; outer shell now owns only Dialog wrapper and seed propagation.

**Reliability — HIGH → RESOLVED**
Merge loop in useAnnouncementData re-executes with a fresh new Date() on every re-render; expiry-during-session behavior had no test coverage.
Resolution: added fake-timer test that sets system time past an entry's expiresAt and verifies the entry is filtered on re-render.

**Reliability — HIGH → RESOLVED**
useEffect dependency on initialSelectedId meant a mid-open id change would navigate the open dialog to a new detail view — untested scenario.
Resolution: added rerender test confirming that changing initialSelectedId while open transitions the content component to the new detail view without closing the dialog.

**Consistency — HIGH → RESOLVED**
ANNOUNCEMENT_PREVIEW_COUNT JSDoc said "always 1: most recent" while the value was 5; skeleton hardcoded 3 rows regardless of the constant.
Resolution: corrected JSDoc; skeleton now uses ANNOUNCEMENT_PREVIEW_COUNT for row count.

## Backlog Items

- Add expiresAt example entry to static/data/announcements.json as a format reference for future editors
- Add CI lint or JSON schema for i18n files to catch accidental language mixing in new entries
- Align AnnouncementSkeleton header width to the real h2 heading to reduce layout shift on slow networks
