# Execution Plan: Planner Export/Import

## Execution Overview

Local-only export/import feature for planner backup. No server interaction. Reuses:
- Compression: pako (from deckCode.ts)
- IndexedDB: cursor iteration (from usePlannerStorage.ts)
- Conflict resolution: BatchConflictDialog (relabel "Server" to "Imported")
- Section UI: SyncSection pattern (Suspense wrapper + skeleton)

## Dependency Analysis

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `constants.ts` | Medium | None | Types, Schemas, Component |
| `PlannerTypes.ts` | Medium | constants.ts | PlannerSchemas, Component |
| `PlannerSchemas.ts` | Medium | PlannerTypes.ts | Component validation |
| `PlannerExportImportSection.tsx` | Low | All above + hooks | SettingsPage |
| `SettingsPage.tsx` | Low | Component | None (page) |
| `common.json` (i18n) | Low | None | Component |

## Execution Order

### Phase 1: Data Layer

1. **`constants.ts`**: Add EXPORT_VERSION = 1 constant
   - Depends on: none
   - Enables: Types, Schemas, Component

### Phase 2: Types & Schemas

2. **`PlannerTypes.ts`**: Add ExportEnvelope and PlannerExportItem interfaces
   - Depends on: Step 1
   - Enables: Schemas, Component

3. **`PlannerSchemas.ts`**: Add ExportEnvelopeSchema for import validation
   - Depends on: Step 2
   - Enables: Component validation

### Phase 3: Component

4. **`PlannerExportImportSection.tsx`**: Create new settings section component
   - Depends on: Steps 1-3
   - Enables: SettingsPage integration
   - Pattern source: SyncSection.tsx
   - Compression: pako from deckCode.ts
   - Storage: cursor iteration from usePlannerStorage.ts
   - Conflicts: BatchConflictDialog with relabeled props

### Phase 4: Integration

5. **`SettingsPage.tsx`**: Import and render PlannerExportImportSection
   - Depends on: Step 4
   - Enables: Feature accessible
   - Location: Between SyncSection and NotificationSection

### Phase 5: i18n

6. **`static/i18n/EN/common.json`**: Add export/import UI strings
   - Depends on: none
   - Enables: Translated UI

7. **`static/i18n/KR/common.json`**: Add Korean translations
   - Depends on: Step 6

8. **`static/i18n/JP/common.json`**: Add Japanese translations
   - Depends on: Step 6

9. **`static/i18n/CN/common.json`**: Add Chinese translations
   - Depends on: Step 6

### Phase 6: Verification

10. **Manual testing**: Execute test plan from instructions.md
    - Depends on: Steps 1-9

## Verification Checkpoints

| After Step | Verify |
|------------|--------|
| 3 | TypeScript compiles without errors |
| 4 | Component renders in isolation |
| 5 | Section visible on Settings page |
| 9 | All i18n keys resolve |
| 10 | Full test plan passes |

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| Large file memory | 4 | Progress indicator, test with 500 planners |
| Corrupted gzip | 4 | try/catch with user error message |
| Invalid JSON | 4 | Zod safeParse, skip invalid entries |
| Duplicate import | 4 | BatchConflictDialog for ID collisions |
| Empty export | 4 | Check count before download, show message |

## Pre-Implementation Validation Gate

Before Step 4:
- [ ] EXPORT_VERSION constant exists
- [ ] ExportEnvelope type exists
- [ ] ExportEnvelopeSchema exists
- [ ] pako import verified (used in deckCode.ts)
- [ ] BatchConflictDialog accepts custom labels

## Rollback Strategy

All changes additive:
1. Component fails → Delete file, remove import from SettingsPage
2. Schema breaks → Revert PlannerSchemas.ts only
3. i18n breaks → Revert common.json files

## Pattern Reference Files

| Pattern | Source File | Lines |
|---------|-------------|-------|
| Suspense wrapper | SyncSection.tsx | Full file |
| pako gzip/ungzip | deckCode.ts | 140-150 |
| IndexedDB cursor | usePlannerStorage.ts | 253-320 |
| Conflict dialog | BatchConflictDialog.tsx | Full file |
