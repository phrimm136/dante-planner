# Implementation Plan: Deck Code Import/Export

## Clarifications Needed

No clarifications needed - requirements are clear.

## Task Overview

Implement deck code import/export functionality for the DeckBuilder component. Users can export their current deck configuration to a compressed base64 string copied to clipboard, and import deck codes by reading from clipboard with a confirmation dialog before applying. This enables easy sharing of deck configurations.

## Steps to Implementation

1. **Create deck code utility module**: Create `/frontend/src/lib/deckCode.ts` with encode/decode functions handling the 560-bit binary format, base64, and gzip compression
2. **Add translation keys**: Add import/export/error/success/warning message keys to all 4 language files (EN, JP, KR, CN) under `deckBuilder` namespace
3. **Create export handler**: Add async function in DeckBuilder to serialize equipment + deploymentOrder to deck code and copy to clipboard with toast feedback
4. **Create import handler**: Add async function to read clipboard, decode deck code, validate against identity/EGO spec lists, and collect warnings for invalid IDs
5. **Add confirmation dialog**: Create import confirmation dialog using Dialog component showing decoded deck summary and any warnings before applying
6. **Add UI buttons**: Place Import and Export buttons with icons left of Reset Order button, matching existing button styling
7. **Wire up state updates**: Connect import confirmation to update equipment and deploymentOrder state with startTransition

## Success Criteria

- Export button copies valid deck code to clipboard with success toast
- Export failure shows error toast (clipboard permission denied)
- Import button reads clipboard and shows confirmation dialog for valid codes
- Invalid identity/EGO IDs (not in spec lists) raise warnings in confirmation dialog
- Import of invalid/corrupted code shows non-intrusive error toast
- Confirmation dialog has Cancel/Apply buttons; Cancel dismisses, Apply updates deck
- All UI text uses i18n with translations in all 4 languages
- Buttons visually match Reset Order button styling
- Round-trip test: export then import produces identical deck state

## Assumptions Made

- **Deployment order encoding**: Value 0 means not deployed; values 1-12 represent deployment position
- **ID validation**: Will validate decoded IDs against identitySpecList.json and egoSpecList.json
- **Gzip library**: Will use pako or fflate for browser-compatible gzip compression/decompression
- **Button placement**: "Left to reset order" interpreted as Import, Export, then Reset Order (left-to-right reading order)
- **Warning behavior**: Invalid IDs show warning but still allow import; those slots will be empty/skipped
