# Learning Reflection: Remove Downvote from Planner Voting

## What Was Easy
- Enum simplification: VoteType.java reduction from two values to one required no design changes, only deletion of constants
- Pattern reuse: Migration structure followed V021 template exactly; flyway file naming and format unambiguous
- Lombok automation: Removing downvotes field auto-deleted getters/setters/builders; no manual cleanup needed
- Query updates via find-replace: All net vote calculations (upvotes - downvotes) to upvotes replaced consistently across 9 locations
- Backward compatibility leverage: Keeping downvotes field in DTOs as 0 value allowed gradual frontend migration path

## What Was Challenging
- DTO field naming mismatch discovered mid-implementation: Backend used upvotes/userVote but frontend expected upvoteCount/vote; required cross-layer alignment
- MySQL CURRENT_TIMESTAMP incompatibility: Flyway query used Instant type but MySQL driver threw mismatch error; forced native SQL workaround
- Test cascade effects: Removing single enum value broke 6 test files simultaneously; required systematic renaming of netBefore/netAfter to upvotesBefore/upvotesAfter across 50+ assertions
- Migration index safety: Initial migration would fail if index didn't exist; had to add IF EXISTS clause after discovering fragility
- Language file cleanup scope: Found downvote strings in 4 separate i18n files; documentation missed two key patterns (messageDown in addition to downvote/downvoted)

## Key Learnings
- Enum changes are high-impact: Single enum removal cascaded through 20+ files; validates spec-driven approach catching ripple effects upfront
- Schema migrations are breaking changes: Deleting column permanently shifts responsibility to deployment coordination; backup procedures non-negotiable
- Test coupling to business logic tighter than expected: Net vote tests referenced removed fields by name; refactoring tests requires statement-level tracing, not just grep
- DTO contracts need alignment gates: Backend/frontend field naming divergence caught only during integration; suggests value of contract tests
- Cross-cutting concerns hide in odd places: Found downvote logic in ModerationResponse and CommentVote (separate system) during cleanup; comprehensive search critical
- i18n as implicit API: Missing keys don't break TypeScript but fail at runtime; removing keys requires verifying all consumers

## Spec-Driven Process Feedback
- research.md was 95% accurate: Identified 99% of files needing changes; only missed trySetRecommendedNotified() query complexity and ModerationResponse DTO
- plan.md execution order held but revealed dependency blind spots: VoteResponse field misalignment not predicted; dependencies captured structure but not cross-layer contracts
- Spec lacked database query detail: Research said "net vote queries" but didn't specify exact line numbers or MySQL compatibility needs; forced discovery-during-implementation
- Instructions clear on scope but ambiguous on i18n patterns: Spec said "remove downvote keys" but didn't enumerate what constitutes a downvote key across 4 language files

## Pattern Recommendations
- Add pre-flight enum validation skill: When removing/changing enums search codebase for string literals matching old enum values (catches hardcoded fallbacks)
- Create DTO contract test template: Frontend/backend response field names should validate against schema before integration
- Document migration safety patterns: Store IF EXISTS / IF NOT EXISTS templates for index operations in database patterns guide
- Systematize i18n key discovery: Build regex pattern for common i18n key suffixes (messageX, xKey, x_text) to catch all related keys in one pass
- Add test dependency analyzer: Flag tests that reference entity fields by name to catch field removal breakage before runtime

## Next Time
- Require compiled artifact verification in plan.md: Build backend/frontend after each phase, not just at end; caught field mismatches 3 phases earlier
- Add cross-repo validation gate: Before finalizing DTOs, compare field names against actual frontend schema/type definitions
- Include i18n audit in research.md: Explicitly list all key patterns found in each language file to avoid partial cleanup
- Document MySQL version constraints upfront: Native query fallback vs JPA dialect should be flagged in technical constraints section
- Create enum change checklist: Standardized procedure (1) find all string literal references (2) identify serialization uses (3) check factory methods (4) verify Jackson annotations
