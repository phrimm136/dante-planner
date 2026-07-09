# Local Save Research

## Clarifications Resolved
- **Device ID storage**: IndexedDB (same layer as planners)
- **Recovery dialog**: Only for drafts; saved planners load silently or go to list
- **Corrupted data**: Toast + offer fresh start (delete corrupted, notify user)

## Spec-to-Code Mapping
- **Auto-save (2s debounce)** → New `usePlannerAutosave.ts` hook
- **IndexedDB storage** → Existing `storage.ts` + new `usePlannerStorage.ts` wrapper
- **Draft recovery dialog** → Modify `PlannerMDNewPage.tsx` with useState + mount check
- **Save button + toast** → Add to `PlannerMDNewPage.tsx`, use sonner
- **Draft limit (3 max)** → `usePlannerStorage.ts` with guest check + auto-delete oldest
- **Metadata tracking** → New types in `PlannerTypes.ts`
- **Set↔Array serialization** → Utility functions in schemas/hook
- **Zod validation on load** → New `PlannerSchemas.ts` with .strict()

## Spec-to-Pattern Mapping
- **IndexedDB CRUD** → Follow `storage.ts` pattern (SSR-safe, error handling)
- **Auth detection** → Use `useAuthQuery.ts` (returns null for guests)
- **Dialog component** → Use shadcn AlertDialog pattern
- **Toast notifications** → Import from sonner (existing in project)
- **Type/schema pair** → Follow `NoteEditorSchemas.ts` + `NoteEditorTypes.ts` pattern
- **Debounce** → useRef for timeout, cleanup on unmount

## Gap Analysis

**To Create:**
- `types/PlannerTypes.ts` - SaveablePlanner, PlannerMetadata, PlannerContent
- `schemas/PlannerSchemas.ts` - Zod schemas with Set serialization
- `hooks/usePlannerStorage.ts` - IndexedDB CRUD + draft limit
- `hooks/usePlannerAutosave.ts` - Debounced auto-save

**To Modify:**
- `lib/constants.ts` - Add AUTO_SAVE_DEBOUNCE_MS, MAX_GUEST_DRAFTS, PLANNER_SCHEMA_VERSION
- `routes/PlannerMDNewPage.tsx` - Save button, draft recovery dialog, hook integration

**Can Reuse:**
- `storage.ts` - SSR-safe IndexedDB utility (no changes needed)
- sonner - Toast library already installed
- shadcn AlertDialog - For draft recovery prompt
- `useAuthQuery` - Guest detection

## Technical Constraints
- SSR safety: Check `typeof window !== 'undefined'`
- Set serialization: Convert to arrays for JSON, restore on load
- DeviceId: Generate UUID on first access, store in IndexedDB
- Debounce: useRef + cleanup to prevent memory leaks
- Zod: All loaded data must pass .safeParse()
- Guest limit: Only enforce when useAuthQuery returns null
