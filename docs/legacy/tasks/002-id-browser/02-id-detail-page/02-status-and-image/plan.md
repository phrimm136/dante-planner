# Implementation Plan: Status and Image Section

## Clarifications Needed

No clarifications needed - requirements are clear with user feedback provided.

## Task Overview

Refactor the identity detail page header and status sections to display grade/name inline, add image swap/expand functionality with 1-star fallback, and implement three status panels (Status, Resistance, Stagger) with proper icons, calculations, and i18n-aware resistance category labels. Extract reusable components and utilities to maintain code quality and follow project conventions.

## Steps to Implementation

1. **Create utility functions**: Add resistance category calculator, stagger threshold calculator, and image path helpers to identityUtils.ts
2. **Define TypeScript types**: Create interfaces for identity detail data structure, resistance categories, and component props
3. **Build header component**: Create component with inline grade icon and name display, positioned identity image with stacked button overlays
4. **Implement image toggle logic**: Add state management for gacksung/normal swap with 1-star detection and button disable logic
5. **Build Status panel component**: Create panel with centered title and horizontal layout for HP/Speed/Defense with icons and values
6. **Build Resistance panel component**: Create panel with centered title, horizontal layout for slash/pierce/blunt with icons, category labels, and color coding
7. **Build Stagger panel component**: Create panel with centered title, horizontal layout for thresholds showing percentage and calculated HP values
8. **Build Traits display component**: Create component to parse bracket notation traits and display translated values with background separation
9. **Integrate components into IdentityDetailPage**: Replace placeholder content with new components, wire up data loading and i18n
10. **Add error handling**: Implement error state for missing identity data with proper error message display

## Success Criteria

- Grade icon and identity name display inline in header section
- Image swap button toggles between gacksung and normal variants, disabled for 1-star identities with proper fallback
- Swap and expand buttons are stacked vertically and sized appropriately for easy clicking
- Expand button opens current identity image in new browser tab
- Status panel shows HP, Speed, and Defense horizontally with proper icons
- Resistance panel shows slash/pierce/blunt with icons, category names (i18n-aware), and color-coded values
- Stagger panel shows threshold percentages with calculated HP values truncated correctly
- Multiple traits display separated by backgrounds in horizontal layout
- Missing trait translations show original bracketed notation as fallback
- Missing identity data throws clear error message
- All section titles centered and properly styled
- Responsive layout maintains structure on mobile and desktop

## Assumptions Made

- Default image variant is gacksung, falling back to normal for 1-star or missing files
- Resistance category colors will be implemented using Tailwind text color classes
- Button stacking uses absolute positioning with appropriate spacing for touch targets
- Traits with backgrounds suggest pill/badge-style components with spacing between them
- Error state for missing ID will use try-catch or conditional rendering with error message component
