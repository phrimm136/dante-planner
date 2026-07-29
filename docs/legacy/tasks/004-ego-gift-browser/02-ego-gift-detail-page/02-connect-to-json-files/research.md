# Research: Connect to JSON Files

## Overview of Codebase

- EGO Gift detail page currently uses six placeholder components in components/gift directory with hardcoded data and TODO comments
- Two-phase data loading pattern used by IdentityDetailPage and EGODetailPage loading spec data first then translations separately
- useParams hook from TanStack Router extracts id parameter from route /ego-gift/$id for dynamic imports
- Dynamic import pattern uses template literals loading data from static/data/{domain}/{id}.json for specs
- Translation files dynamically imported from static/i18n/{lang}/gift/{id}.json based on i18n.language from useTranslation hook
- LoadingState and ErrorState components provide consistent loading and error handling across detail pages
- Enhancement data stored in descs array with variable length indicating number of enhancement levels per gift
- Gift 0 has three enhancement levels gift 1 has two levels requiring conditional rendering based on descs.length
- Current EnhancementLevels component hardcodes three panels needing refactor to map over descs array dynamically
- EGOGiftSpecList.json contains cost keywords tier and themePack fields per gift accessed by numeric id string keys
- Translation files contain name field descs array for enhancement descriptions and obtain field for acquisition method
- Only EN language currently has gift detail translation files other languages will trigger error state gracefully
- DetailPageLayout component already implements responsive 2-column grid matching new layout requirement perfectly
- Current 3-column layout needs restructuring to 2-column moving acquisition section below cost in left column
- Field named obtain in actual JSON files despite task instructions referencing acq requiring use of obtain key

## Codebase Structure

- Gift placeholder components located at frontend/src/components/gift/ with GiftImage GiftName CostDisplay EnhancementPanel EnhancementLevels AcquisitionMethod
- Main detail page route at frontend/src/routes/EGOGiftDetailPage.tsx composing all sub-components in grid layout
- Gift spec data centralized in static/data/EGOGiftSpecList.json with nested objects keyed by gift id
- Translation files organized at static/i18n/EN/gift/{id}.json with separate file per gift containing name descs obtain
- Gift name list for browser at static/i18n/EN/EGOGiftNameList.json used by useEGOGiftData hook not detail page
- Type definitions in frontend/src/types/EGOGiftTypes.ts defining EGOGiftSpec EGOGiftI18n and EGOGift interfaces
- Common components at frontend/src/components/common/ providing DetailPageLayout LoadingState ErrorState for reuse
- Route registration in frontend/src/lib/router.tsx defining /ego-gift/$id path with dynamic id parameter
- Images expected at static/images/gift/{id}.webp per task instructions but directory does not exist yet
- Reference implementations at frontend/src/routes/IdentityDetailPage.tsx and EGODetailPage.tsx showing data loading patterns

## Gotchas and Pitfalls

- Static images directory static/images/gift/ does not exist requiring fallback or placeholder image handling
- EGOGiftI18n interface only defines name field missing descs array and obtain field requiring type expansion before implementation
- Gift 2 exists in spec list but has no corresponding translation file at static/i18n/EN/gift/2.json requiring error handling
- Enhancement count varies per gift with no explicit field requiring inference from descs.length to conditionally render panels
- Only EN translations exist currently so language switching to JP KR or CN will cause import errors needing graceful fallback
- Loading state must wait for both spec AND translation to load before rendering setting isLoading false only after i18n loads
- Dynamic import paths require exact template literal format with backticks and language variable from i18n context
- Field naming inconsistency between task instructions saying acq but actual JSON files using obtain key name
- EnhancementPanel component has placeholder styling with colored backgrounds needing removal when integrating real data
- useParams returns id as string requiring consistent string comparison when accessing EGOGiftSpecList object keys
- No loading skeleton for images causing layout shift when image loads requiring min-height or aspect ratio reservation
- Enhancement level labels currently hardcoded as Level 0 Level +1 Level +2 needing dynamic generation based on index
- Route params strict false setting means id could be undefined requiring null check before data loading
- Translation import errors logged to console but still set isLoading false preventing infinite loading state
- DetailPageLayout expects leftColumn and rightColumn props as ReactNode requiring proper component composition structure
