# Implementation: Planner Export/Import

## What Was Done
- Added EXPORT_VERSION, EXPORT_FILE_EXTENSION, EXPORT_MAX_FILE_SIZE constants
- Created ExportEnvelope and PlannerExportItem interfaces in PlannerTypes.ts
- Added ExportEnvelopeSchema with light Zod validation for imports
- Built PlannerExportImportSection.tsx with export/import/conflict resolution
- Integrated section into SettingsPage.tsx between Sync and Notifications
- Added full i18n translations (EN, KR, JP, CN) including fileTooLarge key
- Applied code review fixes: file size limit (10MB), deviceId validation, error logging

## Files Changed
- `frontend/src/lib/constants.ts`
- `frontend/src/types/PlannerTypes.ts`
- `frontend/src/schemas/PlannerSchemas.ts`
- `frontend/src/components/settings/PlannerExportImportSection.tsx` (new)
- `frontend/src/routes/SettingsPage.tsx`
- `static/i18n/EN/common.json`
- `static/i18n/KR/common.json`
- `static/i18n/JP/common.json`
- `static/i18n/CN/common.json`

## Verification Results
- Checkpoint 3 (TypeScript): pass - no errors in export/import files
- Checkpoint 4 (Component renders): pass - skeleton + content render
- Checkpoint 5 (Settings page): pass - section visible between Sync and Danger Zone
- Checkpoint 9 (i18n keys): pass - all exportImport.* keys added
- Build: pass (pre-existing errors in other files unrelated to this feature)
- Manual test: pass - Export shows toast, Import opens file chooser

## Issues & Resolutions
- Background agents hit permission errors → Implemented directly in main context
- pako header option not in DefinitelyTyped → Used type assertion with comment
- No Progress component exists → Built custom div-based progress bar
- Pre-existing SINNERS import error blocks full e2e test → Noted in status.md
- Code review identified security gaps → Added 10MB file size limit, deviceId check

## Pre-existing Issues Found (out of scope)
- SINNERS not imported in PlannerMDEditorContent.tsx:197
- isPublishing declared twice in PlannerMDEditorContent.tsx:511
- useRef type issue in usePlannerEditorStore.tsx:383
