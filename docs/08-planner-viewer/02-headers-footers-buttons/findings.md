# Learning Reflection: Planner Headers, Footers, and Action Buttons

## What Was Easy

- Hook pattern reuse from usePlannerVote/usePlannerBookmark reduced cognitive load
- Component composition with variant-based design (published vs personal) kept logic modular
- shadcn/ui button variants (outline/ghost/destructive) covered all action types
- Separate routes eliminated conditional rendering complexity

## What Was Challenging

- Cache invalidation scope: broad gesellschaft.all caused side effects, needed narrowing
- Zod validation assumptions: server trust model caused false failures
- Locale mapping: KR→ko-KR, JP→ja-JP, CN→zh-CN needed explicit mapping
- Delete dialog race: 150ms delay needed for animation before navigation
- Security config: published detail endpoint required explicit permitAll()

## Key Learnings

- Spec-driven testing reveals assumptions that code-first approach misses
- Pattern consistency prevents bugs: following existing hook structures exactly
- Locale constants belong in constants.ts for discoverability
- Cache key precision matters: narrow keys (detail) safer than broad (all)
- SSR checks should use shared isClient constant
- Component deletion (PlannerDetailDropdown) simplified ownership

## Spec-Driven Process Feedback

- Research mapping was highly accurate: research→code gap near zero
- Plan execution order (DB→backend→frontend) prevented integration surprises
- Spec ambiguities (route design, delete flow, auto-subscription) clarified early
- Manual test checklist caught 5 bugs before code review

## Pattern Recommendations

- fe-data skill: mutation error recovery with toast and UI state reversion
- fe-component skill: button auth-gating pattern (isAuthenticated && isOwner)
- be-config skill: Security config for public API endpoints
- Anti-pattern: don't validate at client what server already validated
- Cache strategy: use granular keys, avoid broad invalidation

## Next Time

- Validate locale mappings early, create constants before components
- Test animation+navigation together before merging
- Clarify server trust model upfront in specs
- Use permission matrices (isOwner × isAuthed × isSubscribed) in specs
- Security review before backend completion
