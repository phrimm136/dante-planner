# Learning Reflection: Home Page Implementation

## What Was Easy

- Reusing existing card components - IdentityCard, EGOCard, PlannerCard had consistent interfaces
- Suspense boundary pattern - Existing page templates provided exact structure needed
- i18n infrastructure - Adding keys to all 4 language files was straightforward
- Hook composition - Combining spec hooks leveraged well-established TanStack Query patterns
- Tab state management - Shadcn/ui Tabs with local useState required minimal boilerplate

## What Was Challenging

- Date grouping logic - Normalizing mixed Identity/EGO updateDate fields required careful mapping
- Spec ambiguity on limits - "2-3 date groups, ~16 items" was soft, not hard constraint
- React 19 Compiler concerns - Manual memo() flagged by review; required understanding new rules
- Responsive layout edge cases - Two-column stacking at mobile required careful breakpoint testing
- Error boundary placement - Determining page vs section wrapping required architectural judgment

## Key Learnings

- Spec-driven decomposition works - Phased build (i18n → utility → hook → components) made rollback safe
- Pattern enforcement prevents bugs - Reading similar files first caught missing accessibility attributes
- Mixed entity sorting needs normalization - Different date field names require unified updateDate
- Extract shared formatters early - formatEntityReleaseDate extracted after second usage discovered
- Review-driven improvements yield better patterns - memo removal, aria-live weren't obvious initially

## Spec-Driven Process Feedback

- Research mapping was accurate - Spec-to-code table identified all required files correctly
- Plan execution order worked well - Dependency ordering was correct; integration was clean
- Ambiguities resolved correctly - Date format, Link pattern, Suspense assumptions aligned
- Checkpoint verification prevented rework - Manual checks caught issues early

## Pattern Recommendations

- Document "mixed entity" patterns with date field normalization requirements
- Add React 19 Compiler guidelines to skill - enforce memo removal unless profiling shows need
- Create generic groupByDate() utility in lib/dateUtils.ts for reuse
- Add accessibility checklist (aria-live, aria-label) to component templates

## Next Time

- Profile before manual optimization - Don't add memo() without DevTools Profiler data
- Plan Suspense boundaries earlier - Identify waterfall risks during plan phase
- Validate all i18n keys programmatically - Add test asserting matching key structures
- Consider accessibility patterns upfront - Include in component checklist before implementation
