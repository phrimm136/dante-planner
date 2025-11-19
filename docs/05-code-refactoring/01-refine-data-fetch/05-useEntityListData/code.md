# Code Documentation: useEntityListData Hook

## What Was Done

- Created generic useEntityListData hook following useEntityDetailData pattern with entity type discriminator and ENTITY_CONFIG mapping
- Implemented dynamic imports for spec list and i18n name list data files enabling code splitting optimization
- Built hierarchical query key factory with entityListQueryKeys for spec, i18n, and combined list queries
- Refactored all three list pages to call useEntityListData hook and pass data as props maintaining consistent pattern
- Updated IdentityList and EGOList components to accept data as props instead of calling hooks internally
- Moved Identity list interface from useIdentityData hook to IdentityTypes.ts for proper type organization
- Removed deprecated useIdentityData, useEGOData, useEGOGiftData hooks after successful migration

## Files Changed

- /home/user/LimbusPlanner/frontend/src/hooks/useEntityListData.ts
- /home/user/LimbusPlanner/frontend/src/routes/IdentityPage.tsx
- /home/user/LimbusPlanner/frontend/src/routes/EGOPage.tsx
- /home/user/LimbusPlanner/frontend/src/routes/EGOGiftPage.tsx
- /home/user/LimbusPlanner/frontend/src/components/identity/IdentityList.tsx
- /home/user/LimbusPlanner/frontend/src/components/ego/EGOList.tsx
- /home/user/LimbusPlanner/frontend/src/components/identity/IdentityCard.tsx
- /home/user/LimbusPlanner/frontend/src/types/IdentityTypes.ts

## What Was Skipped

- EGOGiftList component already followed props pattern so only page import needed updating
- Query key structure uses single combined query merging spec and i18n rather than dependent queries since lists need both data types simultaneously
- Filtering and sorting logic remains in list components per clarification maintaining separation of concerns

## Testing Results

- TypeScript compilation succeeded without errors after hook migration and interface reorganization
- Production build completed successfully with dynamic imports generating separate chunks for list data files
- All three entity types compile with proper type inference using generic TListItem parameter
- Vite warnings about chunk sizes and static imports are pre-existing unrelated to refactoring

## Issues & Resolutions

- Issue: Identity interface was defined in useIdentityData hook causing import errors after hook deletion
- Resolution: Moved Identity interface to IdentityTypes.ts following same pattern as EGO and EGOGift types
- Issue: IdentityCard component still importing Identity from deleted useIdentityData hook
- Resolution: Updated import to use IdentityTypes.ts ensuring all type imports centralized in types directory
- Issue: Pages had inconsistent data flow with Identity and EGO calling hooks internally while EGOGift received props
- Resolution: Standardized all pages to call useEntityListData and pass data as props eliminating pattern inconsistency
- Issue: List hooks used static imports preventing code splitting optimization
- Resolution: Implemented dynamic imports in createSpecListQueryOptions and createI18nNameListQueryOptions functions
- Issue: Generic type parameter needed for hook to support different list item types across three entities
- Resolution: Added TListItem generic defaulting to any with specific types provided at call sites for type safety
