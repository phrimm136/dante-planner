# Task: Local Save System for Planner

## Description

Implement a save system for the `/planner/md/new` page that persists planner state locally for guests and prepares for server sync for authenticated users.

### Core Functionality

**Save Button**
- Add a "Save" button to PlannerMDNewPage
- Guests: Save to IndexedDB with status `saved`
- Authenticated users: For now, also save to IndexedDB (backend API to be added later)
- Show toast notification on successful save

**Auto-save as Draft**
- Auto-save planner state to IndexedDB on any state change
- Use 2-second debounce to batch rapid changes
- Drafts have status `draft` and `savedAt: null`
- Auto-save happens silently (no toast)

**Draft Recovery**
- When user returns to `/planner/md/new`, check for existing draft
- Show "Continue editing?" dialog if draft exists
- Options: "Continue" (load draft) or "Start Fresh" (discard draft)

**Draft Limit for Guests**
- Maximum 3 drafts stored locally
- When limit exceeded, auto-delete oldest draft (by `lastModifiedAt`)
- No limit enforcement needed for authenticated users (server handles)

### Data Structure

**Metadata to store:**
- `id`: UUID (generated client-side for guests)
- `status`: 'draft' | 'saved'
- `version`: Schema version number (start at 1) for future migrations
- `createdAt`: ISO timestamp
- `lastModifiedAt`: ISO timestamp (updated on every save)
- `savedAt`: ISO timestamp (null for drafts, set on explicit save)
- `userId`: null for guests
- `deviceId`: Persistent device identifier for conflict detection

**Content to store:**
All state from PlannerMDNewPage including:
- title, category, selectedKeywords
- selectedBuffIds, selectedGiftKeyword, selectedGiftIds
- observationGiftIds, comprehensiveGiftIds
- equipment, deploymentOrder, skillEAState
- floorSelections (per-floor theme and gift selections)
- sectionNotes (all note editor content)

**Serialization Requirements:**
- JavaScript `Set` objects must be converted to arrays for JSON storage
- On load, arrays must be converted back to Sets
- Use Zod schemas for validation on load (handle corrupted/old data)

### Future Considerations (Not in scope)

- Backend API for authenticated user saves
- Guest-to-user migration prompt after login
- Cross-device sync for authenticated users

## Research

- Existing IndexedDB utility: `frontend/src/lib/storage.ts`
- Current planner state structure: `frontend/src/routes/PlannerMDNewPage.tsx`
- Existing types: `frontend/src/types/DeckTypes.ts`, `ThemePackTypes.ts`, `NoteEditorTypes.ts`
- Auth hook pattern: `frontend/src/hooks/useAuthQuery.ts`
- Zod schema patterns: `frontend/src/schemas/` directory
- Constants patterns: `frontend/src/lib/constants.ts`
- Toast notifications: Uses `sonner` library

## Scope

Files to READ for context:
- `frontend/src/routes/PlannerMDNewPage.tsx` - Current state structure
- `frontend/src/lib/storage.ts` - IndexedDB utility
- `frontend/src/types/DeckTypes.ts` - Equipment types
- `frontend/src/types/ThemePackTypes.ts` - Floor selection types
- `frontend/src/types/NoteEditorTypes.ts` - Note content types
- `frontend/src/schemas/NoteEditorSchemas.ts` - Zod schema patterns
- `frontend/src/hooks/useAuthQuery.ts` - Auth hook pattern
- `frontend/src/lib/constants.ts` - Constants pattern

## Target Code Area

Files to CREATE:
- `frontend/src/types/PlannerTypes.ts` - SaveablePlanner, PlannerMetadata, PlannerContent types
- `frontend/src/schemas/PlannerSchemas.ts` - Zod schemas for validation
- `frontend/src/hooks/usePlannerStorage.ts` - IndexedDB CRUD operations
- `frontend/src/hooks/usePlannerAutosave.ts` - Debounced auto-save hook

Files to MODIFY:
- `frontend/src/lib/constants.ts` - Add storage keys and limits
- `frontend/src/routes/PlannerMDNewPage.tsx` - Add Save button, integrate hooks, draft recovery dialog

## Testing Guidelines

### Manual UI Testing

1. Navigate to `/planner/md/new`
2. Verify page loads with empty form (no draft exists initially)
3. Change the category to "10F"
4. Wait 3 seconds (debounce + buffer)
5. Open browser DevTools → Application → IndexedDB → danteplanner → planner
6. Verify a draft entry exists with status "draft"
7. Change the title to "Test Planner"
8. Wait 3 seconds
9. Verify the draft entry's `lastModifiedAt` is updated
10. Refresh the page
11. Verify "Continue editing?" dialog appears
12. Click "Continue"
13. Verify category is "10F" and title is "Test Planner"
14. Click the "Save" button
15. Verify toast shows "Saved!"
16. Check IndexedDB - verify entry now has status "saved" and `savedAt` is set
17. Refresh page
18. Verify draft recovery dialog still shows saved planner
19. Click "Start Fresh"
20. Verify form is reset to defaults
21. Make changes and create 3 more drafts (navigate away/refresh between each)
22. Create a 4th draft
23. Verify oldest draft was auto-deleted (only 3 remain)

### Functional Verification

- [ ] Auto-save triggers: Changes to any state field trigger debounced auto-save
- [ ] Debounce works: Rapid changes result in single save after 2s pause
- [ ] Set serialization: Sets are saved as arrays and restored as Sets
- [ ] Metadata tracking: `lastModifiedAt` updates on every save
- [ ] Draft vs Saved: Status correctly reflects draft (auto) vs saved (explicit)
- [ ] Device ID persistence: Same deviceId across page reloads
- [ ] Schema version: Version field is set to 1

### Edge Cases

- [ ] Empty planner: Can save a planner with no changes (default values)
- [ ] Large notes: Note content with images/long text saves correctly
- [ ] Corrupted data: Invalid JSON in IndexedDB shows error, allows fresh start
- [ ] Schema migration: Old schema version data loads with migration (future-proof)
- [ ] Browser storage full: Graceful error handling with user feedback
- [ ] SSR safety: Storage operations don't break server-side rendering

### Integration Points

- [ ] Existing storage.ts: Uses the existing IndexedDB utility correctly
- [ ] Auth context: Can detect guest vs authenticated user (for future)
- [ ] Toast notifications: Uses sonner for save confirmations
- [ ] Suspense boundaries: Draft recovery dialog works with existing Suspense
