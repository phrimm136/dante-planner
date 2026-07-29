# Learning Reflection: Planner List Initialization

**Date**: 2025-12-30
**Task**: 07-planner-list/01-list-initialization

## What Was Easy

- Bottom-up execution plan (schema → entities → repos → service → controller) reduced rework
- JPA @IdClass composite key pattern was straightforward - existed in codebase
- Test-first validation caught issues early - 204 tests gave confidence
- Existing auth patterns (@AuthenticationPrincipal + service-layer checks) reused cleanly
- Flyway migration naming/structure was obvious from existing V001

## What Was Challenging

- API path mismatch between backend (`/api/planner/md/*`) and frontend (`/api/planners/*`) broke assumptions
- Manual curl testing blocked without DB access - unit tests became only validation path
- Email-to-author extraction security leak discovered late in review phase
- Race condition in vote counting wasn't obvious until adversarial review
- Spec had no clarity on SET column validation (DB vs app layer rejection)

## Key Learnings

- Denormalized vote counts need optimistic locking (@Version) to prevent lost updates
- Authorization check at service-layer is cleaner but requires careful null-handling
- MySQL SET column needs custom JPA AttributeConverter<Set<String>, String>
- Composite key requires BOTH @IdClass AND explicit unique constraint at DB level
- Public DTOs must never leak user data - wait for proper displayName instead of email extraction
- Pagination defaults need explicit verification in endpoint documentation

## Spec-Driven Process Feedback

- Research mapping was 95% accurate - API path confusion was the gap
- Plan execution order worked but missed integration point (frontend API path dependency)
- Edge cases comprehensive but race condition deferred as "high-scale" when it's immediate risk

## Pattern Recommendations

- Document @IdClass pattern with unique constraint requirement in be-service skill
- Create "denormalized field update" pattern for vote systems with @Version guidance
- Add SecurityConfig path migration checklist to be-security skill
- Establish "public DTO security checklist" in be-controller skill

## Next Time

- Decouple API path migration from feature work - separate preliminary task
- Flag security issues at plan stage, not review - explicit defer vs fix decision
- Run integration test after service layer, not after full implementation
- Add concurrency test for denormalized counts in verification section
- Create API path follow-up task immediately, don't defer to undefined future
