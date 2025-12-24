# Code Documentation: Start Buff Section

## What Was Done
- Added 6 asset path helper functions for start buff images (icons, pane, highlight, star, enhancement)
- Created TypeScript types for StartBuff, BuffEffect, BattleKeywords with ID manipulation helpers
- Built TanStack Query hook `useStartBuffData` with MD version parameterization
- Created `useBattleKeywords` hook for battle keyword translations with Zod validation
- Created BattleKeywords Zod schema for runtime validation
- Implemented color tag parser and placeholder replacement with buffKeyword translation
- Created auto-sizing text component using transform scale with useLayoutEffect
- Built StartBuffCard with pane, icon, name, cost, description, enhancement buttons, highlight
- Created StartBuffSection container managing multi-selection state in 2-row grid
- Integrated into PlannerMDNewPage with state management

## Files Changed
- frontend/src/lib/assetPaths.ts
- frontend/src/types/StartBuffTypes.ts (new)
- frontend/src/hooks/useStartBuffData.ts (new)
- frontend/src/hooks/useBattleKeywords.ts (new)
- frontend/src/schemas/BattleKeywordsSchemas.ts (new)
- frontend/src/schemas/index.ts
- frontend/src/components/startBuff/formatBuffDescription.tsx (new)
- frontend/src/components/startBuff/AutoSizeText.tsx (new)
- frontend/src/components/startBuff/EnhancementButton.tsx (new)
- frontend/src/components/startBuff/StartBuffCard.tsx (new)
- frontend/src/components/startBuff/StartBuffSection.tsx (new)
- frontend/src/routes/PlannerMDNewPage.tsx
- static/i18n/*/common.json (EN, KR, JP, CN)

## What Was Skipped
- Index file for startBuff components - not needed for current implementation
- Zod schemas for startBuffsMD6.json - deferred for simpler initial implementation

## Testing Results
- TypeScript compilation: PASS
- Build: PASS

## Issues & Resolutions
- Button size change on click: fixed with wrapper pattern, border-image with outset for glow
- Text flickering: fixed with useLayoutEffect and hidden measurement element
- Enhancement vs selection: separated local enhancement state from card selection
- buffKeyword translation: battleKeywords.json loading with Zod validation
- Enhancement icon stretching: fixed with shrink-0 and height-based sizing (h-4 w-auto)
