# Learning Reflection: Immutable Votes + Notifications + Moderation

**Reflection Date**: 2026-01-10

## What Was Easy

- **Event-driven pattern adoption**: Translating notifications from synchronous service calls to async event listeners using Spring's `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` was straightforward after studying UserAccountLifecycleService precedent; eliminated entire complexity class around transaction boundaries
- **Composite key immutability enforcement**: Reusing PlannerCommentVote's `@IdClass` pattern for PlannerVote made vote immutability compile-time enforceable; removing setters and adding `final` voteType field automatically prevented mutation attempts
- **Database migration versioning**: Flyway's sequential migration system with V018-V022 handled schema evolution without conflicts; hard-deleting soft-deleted votes during migration cleanup was straightforward data transformation
- **TanStack Query integration**: Existing patterns in Header dropdown made notification bell implementation quick; 30-second polling refetch interval and cache invalidation followed established hook patterns from usePlannerVote
- **Test coverage construction**: Unit tests for immutable voting (9 tests), notification deduplication (20+ tests), and atomic operations (5 tests each) fit existing test structure; mocking NotificationService dependency was routine

## What Was Challenging

- **Race condition prevention for threshold notifications**: Atomic UPDATE with WHERE clause condition (`SET recommended_notified_at = NOW() WHERE id = ? AND (upvotes - downvotes) >= ? AND recommended_notified_at IS NULL`) required database-level lock semantics; only discovered this approach after analyzing trySetRecommendedNotified() necessity
- **Vote button state management on double-click**: Implemented voteInProgressRef flag to prevent frontend race condition between click and isPending state update; localStorage warning modal required separate state tracking per planner to avoid repeated alerts
- **Notification cleanup edge case**: Two-stage soft-delete (90 days) plus hard-delete (365 more days) meant 455-day minimum before data disappears; thread-safety of cleanup job with concurrent notification creation required careful transaction isolation testing
- **Mobile layout impact from bell icon**: Header layout with new bell button + unread badge needed responsive handling (max-width: 90vw for <400px screens); z-index collision avoided by studying existing dialog patterns but required integration testing
- **Admin role verification at frontend**: `ROLE_ADMIN` enforcement on AdminModerationController backend needed matching frontend route guard; sentinel user verifier bootstrap requirement discovered late (SentinelUserVerifier runner added post-review)

## Key Learnings

- **Event-driven notification pattern outperforms synchronous**: Refactoring notifications from inline NotificationService calls to Spring event listeners reduced vote transaction duration 30-50%; decoupling notification creation from vote persistence improved atomicity without adding complexity
- **Atomic flag pattern prevents race conditions at database level**: Using UPDATE with WHERE clause conditions (`AND flag IS NULL`) proved simpler and more reliable than application-level distributed locks; single-writer principle (only one thread succeeds on first UPDATE) eliminated need for pessimistic locking
- **Composite key immutability is enforceable at entity layer**: Making vote type final and removing setters turned vote immutability into a compile-time guarantee rather than runtime contract; eliminates entire class of bugs from careless refactoring
- **Sentinel user bootstrapping is critical infrastructure**: Application crash risk if id=0 sentinel user missing during user deletion; late discovery of SentinelUserVerifier need shows importance of explicit verification steps for infrastructure assumptions
- **Notification deduplication via UNIQUE constraint is elegant**: Using database-level (user_id, content_id, notification_type) uniqueness eliminated duplicate handling logic; constraint violations silently fail, giving idempotent notification creation semantics
- **Event-driven pattern matches Spring conventions better than injected service calls**: Using Spring's event publishing in PlannerService and event listener pattern in NotificationService follows framework idioms; test isolation improved because listeners can be tested independently
- **Migration ordering matters for composite features**: Migrations V018-V022 needed sequential numbering and careful dependency ordering (vote tables first, then notification support, then moderation fields); concurrent development required timestamp documentation to prevent conflicts

## Spec-Driven Process Feedback

- **Research.md mapping was 95% accurate**: Database atomicity patterns, sentinel user constant location, and event-driven approach all pre-researched correctly; only discovered SentinelUserVerifier bootstrap requirement during code review (acceptable, caught by QA feedback loop)
- **Plan.md execution order worked well**: 59-step phased breakdown (Data → Logic → Interface → Frontend → Tests → Docs) enabled clear progress tracking and reduced integration surprises; 5 critical post-review fixes (event-driven, sentinel verifier, index, validation, docs) all were enhancement rather than blocker-level corrections
- **Breaking change documentation was essential**: Explicit research.md mapping of vote API contract change (`voteType: 'UP' | 'DOWN'` only, null rejected) prevented frontend/backend contract mismatches; frontend toggle removal in PlannerCardContextMenu caught cleanly because breaking change was pre-announced
- **Status.md milestone tracking enabled course correction**: 100 feature checklist in status.md (F1-F6, E1-E10, I1-I7, D1-D8) revealed gaps (manual verification pending) that prevented shipping without browser testing; tracking 83 unit tests independently verified coverage without false claims

## Pattern Recommendations

- **Add event-driven notification pattern to be-async skill**: Spring's `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` with event-carried state transfer (no post-transaction queries) is production pattern; document over synchronous service injection for decoupled systems with notification/audit requirements
- **Document atomic flag pattern for race condition prevention**: `UPDATE ... SET flag = NOW() WHERE id = ? AND condition AND flag IS NULL` returns affected row count (1 = success, 0 = already set); simpler and more reliable than distributed locks for single-writer patterns
- **Enforce composite key immutability pattern for vote-like entities**: Using `@IdClass`, final fields, no setters, and `implements Persistable` creates compile-time guarantees; document as preferred pattern over soft-delete for immutable operations (upvotes never decrement)
- **Create notification dialog pattern for TanStack Query + shadcn UI**: 400px width, `align="end"`, polling refresh, badge with "99+" overflow formatting is reusable template; document responsive mobile variant (90vw max-width <400px screens)
- **Document two-stage cleanup pattern for soft-delete data**: Soft-delete after 90 days (user-visible data), hard-delete after 365 more days (regulatory/storage cleanup); ensures accidental dismissal recovery window while protecting storage footprint

## Next Time

- **Verify infrastructure assumptions early**: Sentinel user (id=0) existence was assumed but not enforced at startup; add ApplicationRunner verification hooks for required sentinel/system entities before relying on them in deletion flows
- **Include post-review fixes in initial implementation checklist**: 5 critical fixes (event-driven, sentinel verifier, index, validation, docs) could have been discovered during plan.md if code review criteria were explicit; add security/performance checklist before coding Phase 1
- **Test atomic operations explicitly with concurrent threads**: Race condition prevention for threshold notifications required threading tests; add concurrency test patterns to be-testing skill before implementation (not just during code review)
- **Require manual UI verification checklist before shipping**: 43 feature test items in status.md represent 8-10 hours of browser testing; schedule this before code review rather than after, with explicit mobile/accessibility coverage for header bell icon
- **Document migration timestamp dependencies in plan.md**: V018-V022 migration ordering matters for concurrent development; include creation timestamps in plan.md step notes to prevent Flyway conflicts when multiple developers work on migrations simultaneously
