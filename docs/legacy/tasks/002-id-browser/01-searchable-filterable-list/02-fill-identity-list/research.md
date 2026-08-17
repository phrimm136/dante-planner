# Research Document: Fill Identity List

## Overview of Codebase

- Current IdentityList component is placeholder with centered text
- Data comes from two JSON sources requiring merge: identitySpecList.json (specs) and identityNameList.json (translations)
- Vite config has @static alias pointing to /static directory for easy imports
- Static JSON files can be imported directly as modules in React components
- i18n uses react-i18next with language stored in i18n.language property
- Only EN has identityNameList.json currently, other languages missing
- TanStack Router uses Link component for navigation with to prop
- TanStack Query available for data fetching with useQuery hook pattern
- Theme system uses CSS variables for colors ensuring light/dark compatibility
- Tailwind utilities available for flexbox, grid, overflow, spacing
- Button component available with asChild prop for wrapping Link elements
- No existing card component pattern in codebase to reference
- Router currently has no detail page route for individual identities
- Image imports in Vite return URL strings for use in src attributes
- Container pattern uses container mx-auto p-8 for page layouts

## Codebase Structure

- Identity components live in frontend/src/components/identity/
- Static data files in /static/data/ directory
- Translation files in /static/i18n/{LANG}/ directories
- Identity images organized as /static/images/identity/{id}/gacksung_info.png
- Frame images in /static/images/formation/{star}Star4UptieFrame.png pattern
- Sinner backgrounds in /static/images/formation/{star}StarSinnerBG.png pattern
- Sinner icons in /static/images/sinners/{sinnerName}.webp format
- Router configuration in frontend/src/lib/router.tsx
- Custom hooks would go in frontend/src/hooks/ directory
- Type definitions in frontend/src/types/ if needed
- Each identity folder contains 14 image variants including gacksung_info.png specified
- Sinner names in spec use bracket notation like [yiSang] but files use camelCase yiSang.webp

## Gotchas and Pitfalls

- Identity spec uses bracket notation for sinner names requiring conversion to camelCase for image paths
- Missing identityNameList.json in JP/KR/CN languages requires fallback handling
- Four-layer image composition needs careful z-index ordering: identity (bottom), frame, sinner bg (upper-right small), icon (upper-right small, top)
- Image paths must use @static alias not relative paths for build compatibility
- No detail route exists yet for /identity/:id requiring router.tsx modification
- Horizontal scroll requires overflow-x-auto with flex container having w-max or similar
- Card sizing needs fixed dimensions to prevent layout shifts during image loading
- Centering scrollable content requires nested container structure
- Star rating maps to frame/background image filenames as number plus Star suffix
- Dynamic image imports may need type assertions for TypeScript
- JSON import types may differ between dev and build requiring careful handling
- Missing images should have fallback or error handling to prevent broken UI
- Touch targets need adequate spacing to prevent mis-clicks per instructions
