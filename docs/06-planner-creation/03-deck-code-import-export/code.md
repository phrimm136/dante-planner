# Code Documentation: Deck Code Import/Export

## What Was Done

- Created deck code utility module with encode/decode functions handling 560-bit binary format
- Implemented base64 + gzip + base64 encoding chain using pako library with OS=10 header
- Added import/export handlers with clipboard API integration and toast notifications
- Created confirmation dialog showing decoded deck summary and validation warnings
- Added i18n translation keys for all 4 languages (EN, JP, KR, CN)
- Placed Import/Export buttons left of Reset Order button in Formation section
- Integrated validation against identitySpecList and egoSpecList for invalid ID warnings

## Files Changed

- `/frontend/src/lib/deckCode.ts` (new)
- `/frontend/src/components/deckBuilder/DeckBuilder.tsx`
- `/frontend/package.json` (added pako, @types/pako)
- `/static/i18n/EN/common.json`
- `/static/i18n/JP/common.json`
- `/static/i18n/KR/common.json`
- `/static/i18n/CN/common.json`

## What Was Skipped

- Unit tests for deckCode utility (no testing guidelines specified in instructions)
- Non-English translation values (left as empty strings for translator to fill)

## Testing Results

- Build: PASSED (yarn build completed successfully)
- TypeScript compilation: PASSED (no type errors)

## Issues & Resolutions

- **Gzip library needed**: Installed pako for browser-compatible gzip compression
- **Gzip OS header**: Set OS=10 (0x0a) in gzip header for consistent "Ch" prefix output
- **Type assertion**: Used `as pako.DeflateFunctionOptions` to bypass incomplete type definitions
- **Invalid ID handling**: Shows warnings in dialog but allows import with fallback to defaults
- **Clipboard permissions**: Wrapped in try-catch for SecurityError handling with toast feedback
