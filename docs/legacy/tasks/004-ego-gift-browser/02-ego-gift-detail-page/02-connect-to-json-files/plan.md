# Implementation Plan: Connect to JSON Files

## Clarifications Needed

No clarifications needed - requirements are clear

## Task Overview

Connect EGO Gift detail page components to JSON data files using two-phase loading pattern loading spec data from EGOGiftSpecList.json and translations from gift/{id}.json. Restructure layout from 3-column to 2-column grid moving acquisition section below cost in left column. Implement conditional rendering for enhancement panels based on descs array length supporting gifts with zero one or two enhancement levels.

## Steps to Implementation

1. **Expand EGOGiftI18n type definition**: Add descs array and obtain field to interface in EGOGiftTypes.ts matching actual JSON structure
2. **Implement two-phase data loading**: Add useParams to extract id, create two useEffect hooks for spec and translation loading with loading and error states
3. **Update GiftImage component**: Accept id prop and load image from static/images/gift/{id}.webp with onError fallback handling
4. **Update GiftName component**: Accept name prop from i18n data and display with existing styling
5. **Update CostDisplay component**: Accept cost prop from spec data and render with cost icon and value
6. **Update AcquisitionMethod component**: Accept obtain prop from i18n data and display acquisition text
7. **Refactor EnhancementLevels component**: Accept descs array prop and map over it to render dynamic number of EnhancementPanel components
8. **Update EnhancementPanel component**: Accept level index and description text props displaying enhancement details
9. **Restructure page layout**: Replace 3-column grid with DetailPageLayout component using 2-column structure with acquisition in left column
10. **Add loading and error state handling**: Render LoadingState while data loads and ErrorState if data fails to load

## Success Criteria

- Detail page successfully loads and displays data for valid gift IDs from JSON files
- Gift image displays from static/images/gift/{id}.webp with graceful fallback for missing images
- Gift name cost and acquisition method display correctly from spec and translation data
- Enhancement panels render conditionally showing only available levels based on descs array length
- Layout uses 2-column responsive grid with left column containing image name cost acquisition and right column containing enhancements
- LoadingState component displays while both spec and translation data load
- ErrorState component displays when data fails to load with appropriate error message
- Language switching works correctly loading appropriate translation file based on i18n.language
- Build completes without TypeScript errors confirming type definitions correct

## Assumptions Made

- Following two-phase data loading pattern from IdentityDetailPage with separate useEffect hooks for spec and translation
- Using DetailPageLayout component for 2-column responsive grid layout matching existing detail page patterns
- Enhancement level labels keep current format Level 0, Level +1, Level +2 temporarily as confirmed by user
- Missing images will show broken image icon using default img element behavior without custom fallback as confirmed by user
- Missing translation files will trigger error state for entire page as confirmed by user
- Empty descs array means gift has no enhancements and enhancement section hidden entirely as confirmed by user
- Loading state uses simple LoadingState component not skeleton UI as confirmed by user
- Field named obtain in JSON files despite task instructions mentioning acq using actual file structure
- Only EN language has translations currently so other languages will show error gracefully
