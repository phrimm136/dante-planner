# Findings: Home Page Announcement Section

## What Was Easy

- research.md pattern mapping was accurate; useStartBuffData/NotificationDialog patterns copied directly with minimal adaptation
- I18N_LOCALE_MAP was already centralized; formatAnnouncementDate followed existing formatEntityReleaseDate conventions
- TanStack Query factory pattern (queryOptions + useSuspenseQuery) replicated without surprises
- Suspense + Skeleton insertion into HomePage was plug-and-play using RecentlyReleasedContent as the model
- Two-state dialog logic was clean once inner component owned selection state separately from Dialog shell

## What Was Challenging

- Skeleton was hardcoded to 3 rows regardless of ANNOUNCEMENT_PREVIEW_COUNT — required constant-driven refactor
- Phase 2 agent stalled at Step 12 (constants only); Steps 13–14 required manual completion
- First-pass hook tests only covered schemas and query keys — missed UT1–UT4 business logic entirely
- Mid-session expiry and mid-open initialSelectedId change were valid edge cases with no initial test coverage
- Local LOCALE_MAP in formatDate.ts used 'ja'/'ko' instead of 'ja-JP'/'ko-KR' — silent inconsistency with I18N_LOCALE_MAP

## Key Learnings

- Schema validation tests and business logic tests are separate concerns — one does not substitute for the other
- Skeleton components must be driven by the same constant as the real component; mismatches cause layout shift
- Inner component extraction (AnnouncementDialogContent) isolates re-render scope and enforces SRP
- useEffect dependency on initialSelectedId enables deep-link navigation without close/reopen — but creates a new untested state transition
- All locale strings must route through I18N_LOCALE_MAP in constants.ts; local maps cause silent BCP 47 drift
- Fake timers (vi.useFakeTimers + vi.setSystemTime) are the right tool for testing time-dependent hook logic

## Spec-Driven Process Feedback

- research.md accurately mapped all pattern sources; one miss: didn't flag that hook tests needed business logic coverage beyond schema validation
- 22-step execution order was logical; component responsibility split (orchestrator / presenter / shell) was clear throughout
- Phase 2 agent scope was too broad — constants + formatDate + hook in one task caused incomplete execution; smaller phases improve completion rate
- staleTime rule conflict (5 min vs 7 days) required a user decision mid-task; pre-research should resolve stale-time category before plan is written

## Pattern Recommendations

- Document "dual-query merge loop" pattern (spec array + i18n record → merged output) as a reusable hook template
- Anti-pattern: hardcoded skeleton row counts — skeleton row count must be derived from the same constant as the rendered list
- Add to locale guidelines: I18N_LOCALE_MAP in constants.ts is the single source of truth; no file-local locale maps allowed
- Hook test checklist should explicitly include: query key tests, schema tests, AND business logic tests (filter / sort / miss handling)

## Next Time

- Write business logic tests alongside the hook, not as a review catch-up
- Add "skeleton row count matches constant" as an explicit checklist item in plan.md
- Split agent phases so each covers one concern (constants, date utils, hook) to avoid stalled execution
- Resolve stale-time category for the data type during research, before plan is finalized
