# Planner Creation - Key Context

**Last Updated:** 2025-12-22

---

## Critical Files Reference

### Core Implementation Files (To Create)

| File | Purpose | Priority |
|------|---------|----------|
| `frontend/src/routes/planner/create.tsx` | Main planner creation page | P0 |
| `frontend/src/routes/planner/edit.$id.tsx` | Edit existing planner page | P1 |
| `frontend/src/types/PlannerTypes.ts` | Planner TypeScript interfaces | P0 |
| `frontend/src/schemas/plannerSchemas.ts` | Zod validation schemas | P0 |
| `frontend/src/hooks/usePlannerStorage.ts` | IndexedDB storage hook | P0 |
| `frontend/src/hooks/usePlannerState.ts` | Planner state management | P0 |
| `frontend/src/lib/deckCode.ts` | Deck code encoder/decoder | P1 |
| `frontend/src/lib/indexedDB.ts` | IndexedDB wrapper utility | P0 |
| `static/data/buffs/` | Start buff static data | P0 |
| `static/data/themePacks/` | Theme pack static data | P0 |

### Existing Reusable Components

| Component | Path | Usage in Planner |
|-----------|------|------------------|
| `IdentityCard` | `frontend/src/components/identity/IdentityCard.tsx` | Base for sinner card identity display |
| `IdentityList` | `frontend/src/components/identity/IdentityList.tsx` | Identity selection list |
| `EGOCard` | `frontend/src/components/ego/EGOCard.tsx` | EGO slot display |
| `EGOList` | `frontend/src/components/ego/EGOList.tsx` | EGO selection list |
| `EGOGiftCard` | `frontend/src/components/egoGift/EGOGiftCard.tsx` | Gift display in all sections |
| `EGOGiftList` | `frontend/src/components/egoGift/EGOGiftList.tsx` | Gift selection lists |
| `SearchBar` | `frontend/src/components/common/SearchBar.tsx` | Search in lists |
| `Sorter` | `frontend/src/components/common/Sorter.tsx` | Sort controls |
| `IconFilter` | `frontend/src/components/common/IconFilter.tsx` | Keyword filtering |
| `DetailPageLayout` | `frontend/src/components/common/DetailPageLayout.tsx` | Section layouts |

### Type Definition Files

| File | Key Types |
|------|-----------|
| `frontend/src/types/IdentityTypes.ts` | `Identity`, `IdentityData`, `SkillData` |
| `frontend/src/types/EGOTypes.ts` | `EGO`, `EGOData`, `EGORank` |
| `frontend/src/types/EGOGiftTypes.ts` | `EGOGift`, `EGOGiftData`, `EGOGiftSpec` |

### Hook Files

| File | Purpose |
|------|---------|
| `frontend/src/hooks/useEntityListData.ts` | Pattern for list data loading |
| `frontend/src/hooks/useEntityDetailData.ts` | Pattern for detail data loading |

### Configuration Files

| File | Purpose |
|------|---------|
| `frontend/src/lib/router.tsx` | Route definitions (add planner routes) |
| `frontend/src/lib/constants.ts` | SINNERS, SINS, KEYWORD_ORDER |
| `frontend/src/lib/assetPaths.ts` | All asset path generators |

---

## Key Architecture Decisions

### 1. State Management: Context + useReducer (vs Zustand)

**Decision:** Use React Context with useReducer for planner state

**Rationale:**
- Planner state is local to planner pages only
- No need for cross-component access outside planner
- Keeps dependencies minimal
- Familiar pattern for team

**Alternative Considered:** Zustand
- Would add a dependency
- Overkill for localized state
- Could revisit if state becomes truly global

### 2. Storage Strategy: IndexedDB First

**Decision:** Use IndexedDB as primary storage, server as secondary

**Rationale:**
- Enables offline functionality for guests
- Faster drafts (no network latency)
- Server sync can be deferred
- Natural fallback if server is unavailable

**Implementation:**
```typescript
// Storage priority
1. Check IndexedDB for draft
2. If authenticated, sync with server
3. Auto-save to IndexedDB every 30s
4. Server sync on explicit save (authenticated)
```

### 3. Component Composition: Extension vs Wrapper

**Decision:** Extend existing components with props, create wrapper components for planner-specific behavior

**Rationale:**
- Don't modify existing components' core behavior
- Add selection modes via props
- Create new wrapper components for complex compositions

**Example:**
```typescript
// IdentityList already has filtering/sorting
// Add selectionMode prop for planner context
<IdentityList
  items={identities}
  selectionMode={true}
  selectedId={currentSelection}
  onSelect={handleSelect}
/>
```

### 4. Deck Code: Custom Base64 Format

**Decision:** Use custom compact binary format with base64 encoding

**Rationale:**
- Short, shareable codes
- Version field for future compatibility
- No external dependencies
- Can include all deck configuration

**Format Spec:**
```
Byte 0: Version (0x01)
Byte 1: Category (0-2)
Bytes 2-61: 12 sinners × 5 bytes each (identity ID packed)
Bytes 62-121: 12 sinners × 5 bytes each (5 EGO slots packed)
Bytes 122-133: Deployment order (12 bytes)
→ Base64 encoded: ~180 characters
```

### 5. Comment Editor: react-md-editor

**Decision:** Use `@uiw/react-md-editor` library

**Rationale:**
- Well-maintained, popular library
- Built-in preview mode
- Customizable toolbar
- Good TypeScript support
- Image upload support via custom handlers

### 6. Auto-Save Strategy: Debounced with Immediate on Blur

**Decision:** Debounce saves at 30s interval, immediate save on section blur

**Rationale:**
- Prevents excessive writes
- Captures work when user moves between sections
- Balance between data safety and performance

---

## Domain Knowledge

### Sinner System

| Index | Sinner | ID Prefix |
|-------|--------|-----------|
| 0 | YiSang | 101xx |
| 1 | Faust | 102xx |
| 2 | DonQuixote | 103xx |
| 3 | Ryoshu | 104xx |
| 4 | Meursault | 105xx |
| 5 | HongLu | 106xx |
| 6 | Heathcliff | 107xx |
| 7 | Ishmael | 108xx |
| 8 | Rodion | 109xx |
| 9 | Sinclair | 110xx |
| 10 | Outis | 111xx |
| 11 | Gregor | 112xx |

**Default Identity/EGO:** IDs ending in `01` (e.g., 10101, 10201, ... for identities)

### EGO Rank System

| Rank | Display | Slots |
|------|---------|-------|
| Zayin | O | Slot 1 |
| Teth | Ø | Slot 2 |
| He | ה | Slot 3 |
| Waw | ו | Slot 4 |
| Aleph | א | Slot 5 |

### Skill System

- Each identity has 3 skills: S1, S2, S3
- Default Skill EA: S1=3, S2=2, S3=1
- Skills have sin affinity (Wrath, Lust, etc.)
- Uptie levels: 1, 2, 3, 4 (affects stats)
- Threadspin levels: 3, 4 (for EGO)

### Deployment System

- 7 deployed sinners (active in combat)
- 5 backup sinners (reserves)
- Order affects combat priority
- All 12 must be assigned

### Mirror Dungeon Categories

| Category | Floors | Theme Packs |
|----------|--------|-------------|
| 5F | 1-5 | 5 packs |
| 10F | 1-10 | 10 packs |
| 15F | 1-15 | 15 packs |

### EGO Gift Tiers

| Tier | Display | Typical Cost |
|------|---------|--------------|
| 1 | Common | Low |
| 2 | Uncommon | Medium |
| 3 | Rare | High |
| 4 | Very Rare | Very High |
| 5 | Legendary | Extreme |
| EX | Special | Varies |

---

## Dependencies & External Libraries

### Required (New)

| Package | Version | Purpose |
|---------|---------|---------|
| `idb` | ^8.x | IndexedDB Promise wrapper |
| `@uiw/react-md-editor` | ^4.x | Markdown editor |

### Already Available

| Package | Used For |
|---------|----------|
| `@tanstack/react-query` | Data fetching, caching |
| `@tanstack/react-router` | Routing |
| `zod` | Schema validation |
| `i18next` | Internationalization |
| `tailwindcss` | Styling |
| `lucide-react` | Icons |

---

## Integration Points

### With Existing Pages

1. **Identity Detail Page** - Link to "Add to planner"
2. **EGO Detail Page** - Link to "Add to planner"
3. **EGO Gift Page** - Reuse list component
4. **Header** - Add planner navigation link

### With Authentication

```typescript
// In planner save logic
const { isAuthenticated, user } = useAuth()

if (isAuthenticated) {
  // Save to server + IndexedDB
  await savePlannerToServer(plannerState)
} else {
  // Save to IndexedDB only
  await savePlannerToIndexedDB(plannerState)
}
```

### With i18n

New translation keys needed:
```json
{
  "pages": {
    "planner": {
      "create": {
        "title": "Create Planner",
        "category": { "5F": "5 Floors", "10F": "10 Floors", "15F": "15 Floors" },
        "sections": {
          "deck": "Deck Building",
          "buffs": "Start Buffs",
          "startGifts": "Start Gifts",
          "observedGifts": "Observed Gifts",
          "skillEA": "Skill EA",
          "comprehensiveGifts": "Comprehensive Gifts",
          "floors": "Floor Setup"
        },
        "actions": {
          "save": "Save",
          "saveDraft": "Save Draft",
          "publish": "Publish",
          "import": "Import Deck",
          "export": "Export Deck",
          "reset": "Reset"
        }
      }
    }
  }
}
```

---

## Risk Factors & Mitigations

### Data Risks

| Risk | Mitigation |
|------|------------|
| Buff data not available | Create placeholder data; add data extraction task |
| Theme pack data not available | Create placeholder data; defer floor section |
| IndexedDB quota exceeded | Implement cleanup for old drafts |

### Technical Risks

| Risk | Mitigation |
|------|------------|
| State complexity leads to bugs | Comprehensive type safety; unit tests for reducers |
| Performance with large lists | Virtualization (react-window); memoization |
| Draft conflicts (local vs server) | Last-write-wins with conflict UI |

### UX Risks

| Risk | Mitigation |
|------|------------|
| Overwhelming interface | Progressive disclosure; collapsible sections |
| Lost work on navigation | Warn on dirty state; auto-save |
| Mobile usability | Prioritize desktop; responsive design |

---

## Testing Strategy

### Unit Tests (Vitest)

- Deck code encoder/decoder
- Affinity calculator functions
- Keyword aggregator functions
- IndexedDB operations (mocked)
- Reducer actions

### Integration Tests

- Full planner save/load cycle
- Deck import/export roundtrip
- State persistence across page reloads

### E2E Tests (If applicable)

- Create new planner flow
- Edit existing planner flow
- Publish planner flow

---

## Open Questions

1. **Server API Design** - What endpoints needed? REST vs GraphQL?
2. **Image Upload** - Direct to CDN or via server proxy?
3. **Planner Sharing** - URL format? Short codes?
4. **Version Migration** - How to handle planner format changes?
5. **Collaborative Editing** - Future consideration for real-time?

---

## Reference Links

- [Epic Document](./epic.md)
- [Implementation Plan](./planner-creation-plan.md)
- [Task Checklist](./planner-creation-tasks.md)
