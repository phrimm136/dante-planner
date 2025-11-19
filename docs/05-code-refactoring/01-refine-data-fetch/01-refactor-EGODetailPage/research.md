# Research: Refactor EGODetailPage

## Overview of Codebase

- EGODetailPage already uses useEntityDetailData hook from previous refactoring task (line 11, lines 20-21)
- IdentityDetailPage also uses useEntityDetailData hook following identical pattern (line 12, lines 21-22)
- Both pages use exact same data loading approach: destructure data, i18n, isPending, isError from hook
- Both pages pass entity type as first parameter ('identity' vs 'ego') and id from useParams as second parameter
- Both pages use identical loading state handling with LoadingState component when isPending is true
- Both pages use identical error state handling with ErrorState component when isError or data missing
- useEntityDetailData is generic hook supporting identity, ego, and egogift entity types via EntityType discriminator
- Hook uses TanStack Query with queryOptions for both data and i18n queries with 7-day staleTime
- Data and i18n queries are dependent with i18n enabled only when data query succeeds
- Hook returns typed data, i18n, isPending, isError, and error properties
- No manual useState or useEffect for data loading in either detail page
- Both pages only use useState for UI state (activeSkillSlot/activeSkillType)
- useEGOData, useIdentityData, useEGOGiftData hooks still exist but are for list page data (not detail data)
- List data hooks use useMemo pattern loading spec lists and name lists, different purpose than detail hooks
- Global error handling configured via QueryCache onError callback with toast notifications

## Codebase Structure

- Detail page routes: /frontend/src/routes/IdentityDetailPage.tsx and /frontend/src/routes/EGODetailPage.tsx
- Generic detail data hook: /frontend/src/hooks/useEntityDetailData.ts
- List data hooks: /frontend/src/hooks/useIdentityData.ts, useEGOData.ts, useEGOGiftData.ts (separate purpose)
- Common loading/error components: /frontend/src/components/common/LoadingState.tsx and ErrorState.tsx
- Entity type definitions: /frontend/src/types/IdentityTypes.ts, EGOTypes.ts, EGOGiftTypes.ts
- Static data paths: @static/data/identity/, @static/data/EGO/, @static/data/EGOGiftSpecList.json
- i18n data paths: @static/i18n/{language}/identity/, @static/i18n/{language}/EGO/, @static/i18n/{language}/gift/
- Both pages follow DetailPageLayout pattern with leftColumn and rightColumn props
- Entity-specific components in separate directories: /frontend/src/components/identity/, /frontend/src/components/ego/

## Gotchas and Pitfalls

- Task appears already completed - EGODetailPage already refactored to use useEntityDetailData in previous task
- No unused data hooks to remove - useEGOData is for list page, not detail page, still needed
- Instructions mention "erase data hooks not used anymore" but no detail page-specific hooks exist to remove
- IdentityDetailPage pattern already identical to EGODetailPage pattern, nothing to copy
- Both pages already consolidated, no useState/useEffect for data loading remains
- Hook uses empty string fallback when id undefined causing unnecessary query execution (known issue)
- Type assertions still present in hook (as IdentityData, as EGOData) without runtime validation
- If-else chain pattern in hook for entity type discrimination (scheduled for separate refactoring task)
- Non-null assertion on id parameter in component usage (id!) bypasses TypeScript safety
- No differentiation between error types in ErrorState display (all errors show same message)
