# Code Documentation: Fill Identity List

## What Was Done

- Created useIdentityData hook that loads and merges identitySpecList.json with language-specific identityNameList.json
- Built identityUtils library with helper functions to convert sinner names and generate image paths for all four layers
- Created IdentityCard component with four-layer CSS positioning for identity image, uptie frame, sinner bg, and sinner icon
- Updated IdentityList component to render cards in responsive CSS Grid layout with centered container and pt-4 padding
- Added identity detail route at /identity/$id in router configuration with placeholder detail page
- Configured @static path alias in tsconfig.app.json for importing JSON and image assets
- Configured publicDir in vite.config.ts to serve static directory
- Applied fixed card dimensions (w-40 h-56) and gap-4 spacing between cards for touch-friendly layout

## Files Changed

- frontend/tsconfig.app.json (modified - added @static path alias)
- frontend/vite.config.ts (modified - set publicDir: '../static')
- frontend/src/hooks/useIdentityData.ts (new)
- frontend/src/lib/identityUtils.ts (new)
- frontend/src/components/identity/IdentityCard.tsx (new)
- frontend/src/components/identity/IdentityList.tsx (modified)
- frontend/src/routes/IdentityDetailPage.tsx (new)
- frontend/src/lib/router.tsx (modified)

## What Was Skipped

- Image error handling and fallbacks not yet implemented - will show broken images if assets missing
- Only EN language has identityNameList.json translations, other languages fall back to ID
- Detail page is placeholder only with no actual content implementation

## Implementation Details

### IdentityCard Component
The card uses a 4-layer image composition approach:

1. **Layer 1 - Identity Image**:
   - Placed in a clipping container (absolute inset-0 with flex centering and overflow-hidden)
   - Sized at 88% width and height (w-[88%] h-[88%]) to fit within the frame's visible area
   - Uses object-cover to fill and crop the character portrait

2. **Layer 2 - Uptie Frame**:
   - Absolutely positioned overlay (inset-0) spanning full card dimensions
   - Uses object-contain to preserve frame aspect ratio and transparent padding
   - Frame has transparent center where character shows through, plus transparent outer padding

3. **Layer 3 - Sinner BG**:
   - Positioned in upper-right corner with -top-2 -right-2 offsets
   - Sized at w-14 h-14 to show full circle without cropping
   - Uses object-contain

4. **Layer 4 - Sinner Icon**:
   - Positioned in upper-right corner with -top-1 -right-1 offsets (closer to edge than BG)
   - Sized at w-12 h-12
   - Uses object-contain

### IdentityList Component
- Uses responsive CSS Grid for card layout
- Outer container: bg-muted border with p-6 padding
- Grid wrapper: pt-4 top padding to prevent clipping of sinner icons/bg
- Responsive grid columns:
  - 2 columns on mobile (default)
  - 3 columns on sm screens (640px+)
  - 4 columns on md screens (768px+)
  - 6 columns on lg screens (1024px+)
  - 8 columns on xl screens (1280px+)
- Cards centered in grid cells with `justify-items-center`
- gap-4 spacing between cards

## Testing Results

- TypeScript compilation passed with no errors using yarn tsc --noEmit
- All imports resolve correctly with @static alias configuration
- No runtime errors in type checking
- Images load correctly from /images/ paths served by Vite publicDir

## Issues & Resolutions

- Initial @static alias not recognized by TypeScript - resolved by adding @static/* path to tsconfig.app.json
- Images not loading - resolved by setting publicDir: '../static' in vite.config.ts and using /images/ paths
- Regex warning for bracket parsing - fixed by changing /[\[\]]/g to /[[\]]/g
- Image layering order corrected to identity, frame, sinner bg, sinner icon per user feedback
- Frame has transparent border/padding - identity image must be scaled smaller (88%) to fit within visible frame area
- Identity image extending beyond frame - fixed by using clipping container with 88% sizing and object-cover
- Sinner icon/bg getting cropped - added pt-4 padding to grid container to provide space for negative-offset elements
- Layout changed from horizontal scroll to responsive CSS Grid for better multi-row display
- Grid uses Tailwind breakpoints: 2 cols (mobile), 3 (sm), 4 (md), 6 (lg), 8 (xl) for responsive column counts
- Cards centered in grid cells with justify-items-center
