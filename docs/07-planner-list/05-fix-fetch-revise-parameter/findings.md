# Learning Reflection: Planner List Route Restructuring

## What Was Easy

- Route-based architecture using TanStack Router + Zod validation applied cleanly
- Data layer refactoring followed established hook patterns (useSuspenseQuery + query keys)
- Component replacement (tabs → nav buttons) matched existing Link-based navigation
- Existing utilities reuse — usePlannerStorageAdapter already handled data merge
- Test migration adapted with minimal assertion changes

## What Was Challenging

- URL parameter hiding semantics — distinguishing "not set" from "explicitly false"
- Guest vs. auth data branching — merging IndexedDB + server API flows
- Query key scoping across routes — preventing cache collision between mutations
- TypeScript inference for filtered data with Suspense boundaries

## Key Learnings

- Zod `.default()` + `.optional()` enables implicit URL param hiding
- Filtering/pagination logic belongs in hooks (not components) for Suspense compatibility
- Query key naming convention prevents accidental cache reuse across domains
- IndexedDB + API merge pattern: adapter delegates to auth check, hook adds Suspense
- TanStack Router automatically hides Zod defaults from URLs

## Spec-Driven Process Feedback

- Research mapping was precise — all file targets matched implementation
- Phased approach prevented cascading TypeScript errors
- No blocking spec ambiguities encountered
- Manual testing checklist discovered edge cases early

## Pattern Recommendations

- Add query key scoping pattern to fe-data skill (separate namespaces per domain)
- Extend fe-routing skill with Zod URL param hiding examples
- Codify IndexedDB + API merge pattern for personal/community splits
- Document Suspense + client-side pagination acceptable use case

## Next Time

- Audit Link/navigate calls before modifying router.tsx
- Consider factory pattern for similar filter hooks to reduce duplication
- Add redirect handling for old URL patterns (user bookmarks)
- Establish pagination utilities in planning phase, not mid-implementation
