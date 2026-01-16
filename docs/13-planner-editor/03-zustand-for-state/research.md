# Research: Zustand State Management for Planner Editor

## Spec Ambiguities
**NONE** - Spec is clear and actionable. All decisions documented.

---

## Spec-to-Code Mapping

| Requirement | Target File | Action |
|-------------|------------|--------|
| Instance-scoped Zustand store | `stores/usePlannerEditorStore.ts` | New file |
| Replace 23 useState with store | `routes/PlannerMDEditorContent.tsx` | Heavy modification |
| Hot state slice | Store: equipment, floorSelections, comprehensiveGiftIds, deploymentOrder | 4 fields |
| Warm state slice | Store: selectedBuffIds, selectedGiftIds, observationGiftIds, keywords, skillEA, deckFilter, giftKeyword | 7 fields |
| Cold state slice | Store: title, category, isPublished, visibleSections, sectionNotes | 5 fields |
| Dialog states | Local useState | 7 fields unchanged |
| DevTools middleware | Store creation | Required |
| SSE batch updates | usePlannerSync.ts | Single set() call |

---

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `usePlannerEditorStore.ts` | `hooks/useSseStore.ts`, `hooks/useFirstLoginStore.ts` | State+Actions interface, create pattern, selector usage |
| Modified components | `useSseStore.ts` selector examples | Single-field selectors: `store((s) => s.field)` |

---

## Pattern Reference Analysis

### useSseStore.ts (60 lines)
- Dependencies: zustand only
- Structure: State interface + Actions interface + Combined type
- Pattern: `create<Type>((set) => ({ ...state, ...actions }))`
- Selectors: Single-field to prevent cascading rerenders

### useFirstLoginStore.ts (50 lines)
- Simple boolean state with toggle actions
- Same creation pattern

### Key Difference for New Store
- Existing stores are global singletons
- New store needs instance-scoping (component-level creation)
- DevTools middleware addition required

---

## Existing Utilities to Reuse

| Category | Location | Items |
|----------|----------|-------|
| Planner state types | types/PlannerTypes.ts | PlannerState interface |
| Default equipment | PlannerMDEditorContent.tsx:193-216 | createDefaultEquipment(), createDefaultSkillEAState() |
| Deserialization | schemas/PlannerSchemas.ts | deserializeSets(), serializeSets() |
| Note defaults | schemas/NoteEditorSchemas.ts | createEmptyNoteContent() |
| Constants | lib/constants.ts | MD_CATEGORIES, FLOOR_COUNTS, SINNERS, DEFAULT_SKILL_EA |

---

## Gap Analysis

**Missing:**
- Instance-scoped store factory (existing stores are global)
- DevTools middleware setup
- Store provider for child component access

**Needs Modification:**
- PlannerMDEditorContent.tsx: 23 useState → store subscriptions
- ~15 child components: props → store selectors
- usePlannerSync.ts: Batch SSE updates

**Reusable:**
- createDefaultEquipment(), createDefaultSkillEAState()
- deserializeSets() for edit mode init
- All Zustand patterns from existing stores

---

## State Slice Design (24 useState → 4 slices + 7 local)

### Hot State (70% of mutations)
- equipment (Record<string, SinnerEquipment>)
- floorSelections (FloorThemeSelection[])
- comprehensiveGiftIds (Set<string>)
- deploymentOrder (number[])

### Warm State (25% of mutations)
- selectedKeywords (Set<string>)
- selectedBuffIds (Set<number>)
- selectedGiftIds (Set<string>)
- observationGiftIds (Set<string>)
- selectedGiftKeyword (string | null)
- skillEAState (Record<string, SkillEAState>)
- deckFilterState (DeckFilterState)

### Cold State (5% of mutations)
- title (string)
- category (MDCategory)
- isPublished (boolean)
- visibleSections (number)
- sectionNotes (Record<string, NoteContent>)

### Local useState (dialog states - per spec)
- isStartBuffPaneOpen, isStartGiftPaneOpen, isObservationPaneOpen
- isComprehensivePaneOpen, isDeckPaneOpen, importDialogOpen, isPublishing

### Transient (import workflow)
- pendingImport (DecodedDeck | null) - keep local

---

## SSE Sync Strategy

- Batch all server updates in single `set((state) => ({ ...allUpdates }))`
- Prevents 15+ individual rerenders per SSE event
- Requires modification to usePlannerSync.ts event handler

---

## Testing Requirements

### Manual UI Tests
- Performance: DevTools → record equipment tier change → target <15ms (vs 70ms)
- State isolation: React Profiler → verify only subscribed components rerender
- Category change: 5F→4F → 4 floor sections disappear
- Cascade selection: Gift with recipe → ingredients auto-selected

### Automated Tests
- Store initialization with defaults
- Edit mode planner data loading
- Granular subscription verification (hot/warm/cold isolation)
- usePlannerSave receives composed state unchanged
- SSE batch update atomicity

### Edge Cases
- Rapid mutations: No lost updates
- Multi-tab: Independent store instances
- Component unmount: Subscriptions cleaned
- Dialog close: Local state independent

---

## Technical Constraints (from spec decisions)

| Constraint | Decision |
|-----------|----------|
| Store scope | Instance per component (not global) |
| Dialog states | Local useState |
| Middleware | DevTools enabled |
| Selectors | Single-field only |
| Init timing | Lazy on mount |
| SSE batching | Single set() per event |
| usePlannerSave | Interface unchanged |

---

## Files to Modify

### New
- `frontend/src/stores/usePlannerEditorStore.ts`

### Heavy Modification
- `frontend/src/routes/PlannerMDEditorContent.tsx`

### Light Modification (props → selectors)
- DeckBuilderSummary.tsx, DeckBuilderPane.tsx
- StartBuffSection.tsx, StartBuffEditPane.tsx
- StartGiftSummary.tsx, StartGiftEditPane.tsx
- EGOGiftObservationSummary.tsx, EGOGiftObservationEditPane.tsx
- ComprehensiveGiftSummary.tsx, ComprehensiveGiftSelectorPane.tsx
- SkillReplacementSection.tsx
- FloorThemeGiftSection.tsx
- NoteEditor.tsx

### Potential Modification
- usePlannerSync.ts (SSE batch handling)
