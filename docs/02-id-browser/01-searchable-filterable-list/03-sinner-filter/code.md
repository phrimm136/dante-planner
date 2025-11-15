# Code Documentation: Sinner Filter Implementation

## What Was Done

Implemented a functional sinner filter component that allows users to filter identity cards by selecting one or more sinners from the 12 available characters in the game.

### Core Features Implemented

1. **Component Rename and Creation**
   - Renamed `IdentityCharacterFilter.tsx` to `IdentitySinnerFilter.tsx` to accurately reflect its purpose
   - Created a complete multi-select filter with visual state feedback

2. **Filter UI Components**
   - 12 sinner icon buttons arranged horizontally with scroll support
   - Clear all button (×) for quick reset of all selections
   - Visual feedback: selected sinners show primary border and background tint
   - Hover states for better user interaction

3. **State Management**
   - State lifted to parent component ([IdentityPage.tsx](frontend/src/routes/IdentityPage.tsx))
   - Uses `Set<string>` data structure for efficient membership testing
   - Props drilling pattern to share state between filter and list components

4. **Filtering Logic**
   - Empty selection shows all identities (default state)
   - One or more selections filters to only matching identities
   - Uses `parseSinnerName` utility to match bracketed JSON format `[yiSang]` with camelCase filter state

5. **Internationalization**
   - Updated all 4 language files (EN/JP/KR/CN)
   - Changed translation key from `characterFilter` to `sinnerFilter`

## Files Changed

### Created Files

- [frontend/src/components/identity/IdentitySinnerFilter.tsx](frontend/src/components/identity/IdentitySinnerFilter.tsx)
  - New implementation replacing the placeholder component
  - Props interface: `selectedSinners: Set<string>`, `onSelectionChange: (sinners: Set<string>) => void`
  - SINNERS constant array with all 12 sinner names
  - Toggle logic for individual sinner selection
  - Clear all functionality
  - Compact design: h-14 container with w-8 h-8 icon buttons

### Modified Files

- [frontend/src/routes/IdentityPage.tsx](frontend/src/routes/IdentityPage.tsx)
  - Added `useState` for managing `selectedSinners` state
  - Updated import from `IdentityCharacterFilter` to `IdentitySinnerFilter`
  - Passed `selectedSinners` and `setSelectedSinners` props to filter component
  - Passed `selectedSinners` prop to list component

- [frontend/src/components/identity/IdentityList.tsx](frontend/src/components/identity/IdentityList.tsx)
  - Added `IdentityListProps` interface with `selectedSinners: Set<string>`
  - Imported `parseSinnerName` utility
  - Implemented filtering logic with conditional for empty selection
  - Applied filter using `Array.filter` and `Set.has`

- [static/i18n/EN/common.json](static/i18n/EN/common.json)
  - Changed `characterFilter` key to `sinnerFilter`
  - Value: "Sinner Filter"

- [static/i18n/JP/common.json](static/i18n/JP/common.json)
  - Changed `characterFilter` key to `sinnerFilter`
  - Value remains empty pending Japanese translation

- [static/i18n/KR/common.json](static/i18n/KR/common.json)
  - Changed `characterFilter` key to `sinnerFilter`
  - Value remains empty pending Korean translation

- [static/i18n/CN/common.json](static/i18n/CN/common.json)
  - Changed `characterFilter` key to `sinnerFilter`
  - Value remains empty pending Chinese translation

- [frontend/src/styles/globals.css](frontend/src/styles/globals.css)
  - Added new `--button` and `--button-foreground` theme colors
  - Light mode: `--button: oklch(0.75 0 0)` (medium gray for icon visibility)
  - Dark mode: `--button: oklch(0.269 0 0)` (dark gray matching other components)

### Deleted Files

- [frontend/src/components/identity/IdentityCharacterFilter.tsx](frontend/src/components/identity/IdentityCharacterFilter.tsx) (renamed)

## What Was Skipped

Nothing was skipped from the implementation plan. All 10 planned steps were completed:

1. ✅ Component renamed from IdentityCharacterFilter to IdentitySinnerFilter
2. ✅ Created SINNERS constant with all 12 sinner names
3. ✅ Defined IdentitySinnerFilterProps interface
4. ✅ Implemented toggle selection logic with Set data structure
5. ✅ Created clear all button functionality
6. ✅ Built responsive UI with horizontal scroll and visual feedback
7. ✅ Updated IdentityPage with state management
8. ✅ Updated IdentityList with filtering logic
9. ✅ Updated all translation files (EN/JP/KR/CN)
10. ✅ Removed unused imports and verified TypeScript compilation

## Testing Results

### TypeScript Compilation
- ✅ Ran `yarn tsc --noEmit` - **PASSED** with no errors
- All type checking successful

### Dev Server
- ✅ Dev server running on port 5175
- No runtime errors reported
- Component renders correctly with proper styling

### Manual Testing Observations
- Filter UI displays all 12 sinner icons correctly
- Clear all button (×) positioned on the left
- Horizontal scroll works when viewport is narrow
- Selected state shows visual feedback (primary border + background tint)
- Default state (no selection) shows all identities
- Selecting sinners correctly filters the identity list

## Issues & Resolutions

### Issue 1: Unused Import
**Problem**: Initially imported `useState` in IdentitySinnerFilter component but state was lifted to parent.

**Resolution**: Removed unused `useState` import from [IdentitySinnerFilter.tsx](frontend/src/components/identity/IdentitySinnerFilter.tsx:1).

### Issue 2: Translation Key Mismatch
**Problem**: After renaming component, translation key `characterFilter` no longer matched component purpose.

**Resolution**: Updated all 4 language files to use `sinnerFilter` key instead of `characterFilter`.

### Issue 3: Sinner Icon Path Format
**Problem**: `getSinnerIconPath` expects bracketed format like `[yiSang]` but SINNERS array uses camelCase.

**Resolution**: Wrapped sinner names with brackets when calling the utility: `` getSinnerIconPath(`[${sinner}]`) ``

### Issue 4: Empty Selection Behavior
**Problem**: Needed to decide whether empty selection shows all identities or none.

**Resolution**: Implemented check `selectedSinners.size === 0` to show all identities when no filters applied, providing better default user experience.

### Issue 5: White Icons Not Visible in Light Mode
**Problem**: Sinner icons are mostly white and were not visible against the white background in light mode.

**Resolution**:
- Created new `--button` theme color (medium gray `oklch(0.75 0 0)` in light mode)
- Updated IdentitySinnerFilter to use `bg-button` instead of `bg-background` for unselected state
- This provides sufficient contrast for white icons while maintaining dark mode compatibility

### Issue 6: Filter Too Tall
**Problem**: Original filter design with h-20 height and w-10 h-10 icon buttons took up too much vertical space.

**Resolution**:
- Reduced container height from `h-20` to `h-14`
- Reduced padding from `p-4` to `p-2`
- Reduced icon button size from `w-10 h-10` to `w-8 h-8`
- Results in more compact, space-efficient filter component

## Technical Implementation Details

### Data Flow
```
IdentityPage (state owner)
  ↓ selectedSinners, onSelectionChange
IdentitySinnerFilter (UI controller)
  ↓ user clicks icon
  ↓ toggleSinner() updates Set
  ↓ onSelectionChange(newSet)
  ↓ IdentityPage updates state
  ↓ selectedSinners prop to IdentityList
IdentityList (consumer)
  ↓ filters identities array
  ↓ renders IdentityCard components
```

### Key Design Decisions

1. **Set vs Array**: Chose `Set<string>` for O(1) membership testing with `.has()` method
2. **Props Drilling vs Context**: Used props drilling since state only shared between two sibling components
3. **Empty Selection UX**: Show all items when nothing selected (common filter pattern)
4. **Horizontal Scroll**: Preferred scrolling over wrapping to maintain consistent h-14 height
5. **Visual Feedback**: Border and background changes clearly indicate selected state
6. **Theme Integration**: Created dedicated button color to solve icon visibility while maintaining theme consistency
7. **Compact Design**: Optimized for space efficiency with h-14 container and w-8 h-8 icons

## Next Steps

The sinner filter is fully functional. Potential future enhancements:

1. Add keyboard navigation support for accessibility
2. Implement "select all" button alongside "clear all"
3. Add animation transitions for selection state changes
4. Persist filter state to localStorage for user convenience
5. Combine with keyword filter (when implemented) for advanced filtering
