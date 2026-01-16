# Task: Planner Export/Import in Settings Page

## Description

Add a planner export/import section to the Settings page that allows users to backup and restore their locally-stored planners without server interaction.

### Export Functionality
- Export button iterates all planners in IndexedDB
- Constructs JSON with planner array (strips deviceId from keys for portability)
- Wraps in metadata envelope with export version, timestamp, and source device ID
- Compresses with gzip (pako library)
- Prompts user for save location with `.danteplanner` file extension
- Shows progress indicator for large exports

### Import Functionality
- Import button opens file picker accepting `.danteplanner` files
- Decompresses and parses JSON
- Performs light structural validation (Zod) - no version rejection
- Compares planner IDs against existing local planners
- No conflicts: saves directly to IndexedDB
- Conflicts detected: opens BatchConflictDialog for resolution
- Partial import on validation failures (import valid, skip invalid)
- Shows summary after import (imported: N, skipped: M, errors: K)
- Does NOT trigger server sync - all server interactions are manual post-import

### Export Format
```json
{
  "exportVersion": 1,
  "exportedAt": "2026-01-16T12:00:00.000Z",
  "sourceDeviceId": "abc-123-def",
  "planners": [
    {
      "id": "planner-uuid-1",
      "metadata": { /* title, status, timestamps, published */ },
      "config": { /* type, category */ },
      "content": { /* deck, buffs, gifts, etc. */ }
    }
  ]
}
```

### First-Login Dialog Update
- Modify SyncChoiceDialog to inform users about export option
- Add hint text: "You can also export your planners from the Settings page to backup or transfer them."
- Helps users choosing "Keep Local Only" understand they have backup/transfer options

### Key Decisions
- **Device ID**: Strip on export, reconstruct with current device's ID on import
- **Published flag**: Include in export (informational badge only, not functional)
- **Conflict semantics**: Local = source of truth, Imported = incoming/augmented
- **BatchConflictDialog**: Reuse existing dialog, relabel "Server" to "Imported"

## Research

- IndexedDB cursor iteration pattern in `lib/storage.ts`
- Gzip compression pattern in `lib/deckCode.ts` (pako usage)
- Settings section UI pattern in `components/settings/SyncSection.tsx`
- BatchConflictDialog props and conflict resolution flow
- Existing planner storage operations in `hooks/usePlannerStorage.ts`
- File download/upload browser APIs (Blob, URL.createObjectURL, File input)

## Scope

Files to READ for context:
- `frontend/src/lib/storage.ts` - IndexedDB operations
- `frontend/src/lib/deckCode.ts` - pako compression pattern
- `frontend/src/hooks/usePlannerStorage.ts` - planner storage operations
- `frontend/src/components/settings/SyncSection.tsx` - section UI pattern
- `frontend/src/components/dialogs/BatchConflictDialog.tsx` - conflict dialog
- `frontend/src/components/dialogs/SyncChoiceDialog.tsx` - first-login dialog
- `frontend/src/types/PlannerTypes.ts` - SaveablePlanner type
- `frontend/src/schemas/PlannerSchemas.ts` - validation schemas
- `frontend/src/routes/SettingsPage.tsx` - page structure
- `frontend/src/lib/constants.ts` - PLANNER_STORAGE_KEYS pattern

## Target Code Area

Files to CREATE or MODIFY:
- `frontend/src/components/settings/PlannerExportImportSection.tsx` (new)
- `frontend/src/routes/SettingsPage.tsx` (modify - add section)
- `frontend/src/components/dialogs/SyncChoiceDialog.tsx` (modify - add export hint)
- `frontend/src/lib/constants.ts` (modify - add EXPORT_VERSION constant)
- `frontend/src/schemas/PlannerSchemas.ts` (modify - add export format schema)
- `frontend/src/types/PlannerTypes.ts` (modify - add export format types)
- `static/i18n/*/common.json` (modify - add i18n strings)

## System Context (Senior Thinking)

- **Feature domain**: Settings page / Planner storage
- **Core files in this domain**:
  - `routes/SettingsPage.tsx`
  - `hooks/usePlannerStorage.ts`
  - `lib/storage.ts`
  - `components/dialogs/BatchConflictDialog.tsx`
- **Cross-cutting concerns**:
  - IndexedDB storage (planner persistence)
  - Compression (pako, same as deck codes)
  - Validation (Zod schemas)
  - i18n (settings UI strings)
  - Conflict resolution (existing dialog reuse)

## Impact Analysis

- **Files being modified**:
  - `SettingsPage.tsx` (Low - page isolated, just adding section)
  - `SyncChoiceDialog.tsx` (Low - adding informational text only)
  - `constants.ts` (Medium - shared, but adding new constant only)
  - `PlannerSchemas.ts` (Medium - validation, adding new schema)
  - `PlannerTypes.ts` (Medium - types, adding new interfaces)
  - `i18n common.json` (Low - adding strings)
- **What depends on these files**:
  - constants.ts: Many components, but new constant won't affect existing
  - PlannerSchemas.ts: Planner save/sync flows
  - PlannerTypes.ts: All planner-related components
- **Potential ripple effects**: Minimal - new functionality, not modifying existing behavior
- **High-impact files to watch**: None directly touched

## Risk Assessment

- **Edge cases**:
  - Empty export (no planners): Show message, prevent empty file download
  - Large file import: Browser memory limits (~25MB uncompressed for 500 planners)
  - Malformed JSON: Catch parse errors, show user-friendly message
  - Invalid planner structure: Skip invalid, continue with valid ones
  - Same file imported twice: Triggers conflict dialog for duplicate IDs
- **Performance concerns**:
  - Large exports need progress indicator
  - IndexedDB cursor iteration is async, may need batching
- **Backward compatibility**: N/A - new feature
- **Security considerations**:
  - Validate imported data with Zod before storing
  - No server interaction during import (manual sync only)

## Testing Guidelines

### Manual UI Testing

**First-Login Dialog:**
1. Clear browser storage or use incognito mode
2. Log in with Google OAuth
3. Verify SyncChoiceDialog appears
4. Verify three text blocks in description:
   - Main description about local storage and cloud sync
   - Privacy note about server storage
   - Export hint about Settings page backup option
5. Choose either option and verify dialog closes

**Export Flow:**
1. Navigate to Settings page
2. Scroll to "Export / Import" section
3. Verify Export and Import buttons are visible
4. Click Export button
5. Verify progress indicator appears (if planners exist)
6. Verify file save dialog opens
7. Save file with `.danteplanner` extension
8. Verify file is created and non-empty
9. Open file in text editor after gunzip - verify JSON structure

**Import Flow (No Conflicts):**
1. Create a fresh browser profile or clear IndexedDB
2. Navigate to Settings page
3. Click Import button
4. Select a valid `.danteplanner` file
5. Verify progress indicator appears
6. Verify success message with import count
7. Navigate to My Planners page
8. Verify imported planners appear

**Import Flow (With Conflicts):**
1. Have existing planners in IndexedDB
2. Export planners to file
3. Modify a local planner title
4. Import the same file
5. Verify BatchConflictDialog opens
6. Verify "Imported" label (not "Server")
7. Select different resolutions for conflicts
8. Click "Resolve All"
9. Verify planners updated according to choices

**Import Flow (Partial Failure):**
1. Manually corrupt one planner entry in export file
2. Import the corrupted file
3. Verify error summary shows skipped count
4. Verify valid planners were still imported

### Automated Functional Verification

- [ ] First-login dialog shows export hint text
- [ ] Export creates valid gzipped JSON with correct structure
- [ ] Export strips deviceId from planner keys
- [ ] Export includes all planners from IndexedDB
- [ ] Import accepts only `.danteplanner` files
- [ ] Import decompresses and parses JSON correctly
- [ ] Import reconstructs keys with current deviceId
- [ ] Import detects conflicts by comparing planner IDs
- [ ] Import opens BatchConflictDialog when conflicts exist
- [ ] Import saves non-conflicting planners directly
- [ ] Import applies conflict resolutions correctly
- [ ] Import continues on validation failures (partial import)
- [ ] Import does NOT trigger server sync

### Edge Cases

- [ ] Empty export: Shows message "No planners to export"
- [ ] No planners after import: Shows appropriate message
- [ ] Invalid file format: Shows error "Invalid file format"
- [ ] Corrupted gzip: Shows error "Failed to decompress file"
- [ ] Invalid JSON: Shows error "Failed to parse file"
- [ ] Mixed valid/invalid planners: Imports valid, reports skipped count
- [ ] Cancel file dialog: No action taken, no error shown
- [ ] Large file (100+ planners): Progress indicator visible, no timeout

### Integration Points

- [ ] SyncChoiceDialog: Export hint visible in first-login flow
- [ ] BatchConflictDialog: Receives conflicts in expected format
- [ ] IndexedDB: Planners saved with correct key structure
- [ ] Planner list: Imported planners appear after navigation
- [ ] Sync (auth users): No automatic sync triggered, manual sync works after import
