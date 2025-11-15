# Implementation Plan: Fill Identity List

## Task Overview

Replace the placeholder IdentityList component with actual identity data by loading and merging JSON files from static directory. Create individual identity card components with four-layer image composition (identity, frame, sinner bg, sinner icon) arranged in a horizontally scrollable, center-aligned list. Each card navigates to a detail page when clicked.

## Steps to Implementation

1. **Create data loading hook**
   - Build custom hook to import and merge identitySpecList.json with language-specific identityNameList.json
   - Handle missing translations by falling back to identity ID when name unavailable

2. **Create utility for sinner name conversion**
   - Build helper function to convert bracket notation sinner names to camelCase for image paths
   - Map star ratings to frame and background image filenames

3. **Build IdentityCard component**
   - Create component that receives identity data as props and renders layered images using CSS positioning
   - Layer order: identity image (bottom), uptie frame, sinner bg (upper-right small), sinner icon (upper-right top)
   - Wrap card in Link component for navigation to detail page

4. **Update IdentityList component**
   - Replace placeholder with horizontally scrollable container using flexbox
   - Map through identity data to render IdentityCard components with appropriate gaps
   - Center the scrollable content while allowing left-aligned items

5. **Add detail page route**
   - Register new route in router.tsx for individual identity detail pages at /identity/:id
   - Create placeholder IdentityDetailPage component for navigation destination

6. **Configure image paths**
   - Use @static alias for all image imports to ensure build compatibility
   - Construct dynamic paths for identity images, frames, backgrounds, and sinner icons based on data

7. **Add responsive sizing and spacing**
   - Apply fixed dimensions to cards to prevent layout shifts during image loading
   - Add adequate gaps between cards to prevent mis-clicks on touch devices

8. **Handle edge cases**
   - Add fallback handling for missing images or translation data
   - Ensure proper error states don't break the entire list rendering

## Success Criteria

- Identity cards display with correct four-layer image composition in proper z-index order
- List scrolls horizontally with centered content and left-aligned items
- Cards have adequate spacing to prevent accidental clicks on adjacent items
- Clicking a card navigates to /identity/:id detail page route
- Missing translations fall back gracefully to identity ID
- Component works across all four language settings (EN, JP, KR, CN)
- Images load from correct static paths using @static alias
- Layout remains stable during image loading with fixed card dimensions
