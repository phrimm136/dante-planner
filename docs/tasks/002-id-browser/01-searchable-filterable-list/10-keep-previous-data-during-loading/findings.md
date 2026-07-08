# Findings: Keep Previous Search Data During Language Change

## What Was Easy

- TanStack Query's keepPreviousData is a one-liner addition with clear semantics
- All 4 hook files followed identical patterns - easy to apply same change
- No consumer code changes needed - fully backward compatible
- Research phase accurately identified all 5 query options factories

## What Was Challenging

- Build verification showed pre-existing TypeScript errors in unrelated files
- Playwright browser lock required manual testing workaround
- Distinguishing between "deferred" hooks (useQuery) and "suspending" hooks (useSuspenseQuery)

## Key Learnings

- keepPreviousData is TanStack Query v5's replacement for deprecated keepPreviousData: true option
- placeholderData only affects queries using useQuery, not useSuspenseQuery
- Language change creates new query key, triggering fresh fetch with data=undefined
- Industry pattern: keepPreviousData for pagination, filtering, and i18n transitions
- Memory overhead is temporary (~50KB) and released when new data loads

## Spec-Driven Process Feedback

- Research.md mapping was accurate - all 5 queries correctly identified
- Plan.md execution order worked well - Phase 1 (shared file) before Phase 2 (domain files)
- Instructions.md testing guidelines were comprehensive
- No spec clarification needed - TanStack Query docs were sufficient

## Pattern Recommendations

- Add keepPreviousData pattern to fe-data skill for i18n query options
- Document distinction between deferred (useQuery) and suspending (useSuspenseQuery) hooks
- Consider default: add keepPreviousData to all i18n queries in project convention

## Next Time

- Run yarn build before starting to identify pre-existing errors
- For surgical changes across multiple files, direct execution is faster than spawning agents
- Manual testing fallback when Playwright has issues
