# Research: Planner Export/Import

## Spec-to-Code Mapping

| Requirement | File Path | Modification |
|------------|-----------|--------------|
| Export/import buttons | `PlannerExportImportSection.tsx` | CREATE |
| Export format schema | `PlannerSchemas.ts` | MODIFY - add ExportEnvelopeSchema |
| Export format types | `PlannerTypes.ts` | MODIFY - add ExportEnvelope interface |
| Settings page section | `SettingsPage.tsx` | MODIFY - add section |
| Export version constant | `constants.ts` | MODIFY - add EXPORT_VERSION |
| i18n strings | `static/i18n/*/common.json` | MODIFY |
| First-login hint | `SyncChoiceDialog.tsx` | ALREADY DONE |

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `PlannerExportImportSection.tsx` | `SyncSection.tsx` | Suspense wrapper + inner content + skeleton |
| `PlannerExportImportSection.tsx` | `deckCode.ts:140-150` | pako gzip/ungzip with base64 |
| `PlannerExportImportSection.tsx` | `usePlannerStorage.ts:253-320` | IndexedDB cursor iteration |
| `PlannerExportImportSection.tsx` | `BatchConflictDialog.tsx` | ConflictItem/ConflictResolution interfaces |

## Pattern Analysis

### SyncSection.tsx Structure
- Suspense wrapper with skeleton fallback
- Inner `*Content` component with hooks
- Auth check via `useAuthQuery()` - early return if not authenticated
- Toast notifications via `sonner`

### deckCode.ts Compression
- Import: `import pako from 'pako'`
- Encode: `pako.gzip(data, { header: { os: 10 } })` returns Uint8Array
- Decode: `pako.ungzip(compressed, { to: 'string' })`
- Flow: Binary → Base64 → Gzip → Base64

### usePlannerStorage.ts Cursor
- `openDB()` returns `Promise<IDBDatabase | null>`
- `store.openCursor()` with `onsuccess` callback
- `parseStorageKey(key)` splits `planner:md:deviceId:plannerId`
- Validation via `SaveablePlannerSchema.safeParse()`

### BatchConflictDialog.tsx Integration
- ConflictItem: `{ id, localPlanner, serverPlanner }`
- ConflictResolution: `{ id, choice: 'overwrite' | 'discard' | 'both' }`
- Relabel "Server" → "Imported" in labels

## Existing Utilities

| Category | Location | Functions |
|----------|----------|-----------|
| Storage | `lib/storage.ts` | getItem, setItem, removeItem, clear |
| IndexedDB | `hooks/usePlannerStorage.ts` | openDB, listPlanners, savePlanner, loadPlanner |
| Compression | `lib/deckCode.ts` | pako.gzip, pako.ungzip |
| Constants | `lib/constants.ts` | PLANNER_STORAGE_KEYS, PLANNER_SCHEMA_VERSION |
| Validation | `schemas/PlannerSchemas.ts` | SaveablePlannerSchema |
| Device ID | `hooks/usePlannerStorage.ts` | getOrCreateDeviceId |

## Gap Analysis

**Create:**
- `PlannerExportImportSection.tsx` - New settings section component
- Export envelope types and schema
- EXPORT_VERSION constant
- i18n strings for UI

**Modify:**
- `SettingsPage.tsx` - Add section between SyncSection and NotificationSection
- `constants.ts` - Add EXPORT_VERSION = 1
- `PlannerTypes.ts` - Add ExportEnvelope, PlannerExportItem
- `PlannerSchemas.ts` - Add ExportEnvelopeSchema

**Reuse:**
- Cursor iteration from listPlanners()
- Gzip from deckCode.ts
- Conflict resolution from BatchConflictDialog
- Suspense pattern from SyncSection
- Validation from SaveablePlannerSchema

## Testing Requirements

### Manual UI Tests
- Export creates .danteplanner file
- Export strips deviceId from keys
- Import decompresses and parses
- Import reconstructs keys with current deviceId
- Import detects conflicts
- BatchConflictDialog opens for 2+ conflicts
- Partial import on validation failures
- No server sync during import

### Edge Cases
- Empty export shows message
- Corrupted gzip shows error
- Invalid JSON shows error
- Mixed valid/invalid imports valid, reports skipped
- Large file shows progress indicator

## Technical Constraints

- File extension: `.danteplanner` (gzipped JSON)
- Export version: 1 (for future migration support)
- Conflict semantics: Local = source of truth, Imported = incoming
- No automatic server sync - manual only
- Partial import on failures (continue with valid planners)
