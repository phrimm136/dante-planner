# Planner Creation Feature - Implementation Plan

**Last Updated:** 2025-12-22

---

## Executive Summary

This plan outlines the implementation of a comprehensive Mirror Dungeon Planner creation/edit feature for LimbusPlanner. The feature enables users to build and save complete run configurations including deck composition (identities + EGOs), start buffs, ego gifts across multiple selection modes, floor-specific theme packs, and per-section comments. The system supports both guest (IndexedDB) and authenticated (server-side) storage with draft auto-save functionality.

### Key Deliverables
1. **Deck Building Section** - Sinner-wise identity/EGO selection with deployment ordering
2. **Start Buff Section** - 10-card buff selector with enhancement levels
3. **Ego Gift Sections** - Start gifts, observed gifts, and comprehensive gift list
4. **Skill EA Section** - Sinner-wise skill swap configuration
5. **Floor Section** - Theme pack and floor-specific gift selection
6. **Comment System** - Reddit-like markdown editor per section
7. **Persistence Layer** - IndexedDB for guests, server sync for authenticated users
8. **Import/Export** - Deck code functionality

---

## Current State Analysis

### Existing Infrastructure (Leverage Points)

| Component | Status | Reusability |
|-----------|--------|-------------|
| IdentityCard, IdentityList | Complete | Direct reuse with minor extensions |
| EGOCard, EGOList | Complete | Direct reuse with minor extensions |
| EGOGiftCard, EGOGiftList | Complete | Direct reuse with extensions for selection |
| DetailPageLayout | Complete | Usable for section layouts |
| SearchBar, Sorter, IconFilter | Complete | Direct reuse |
| Type definitions (Identity, EGO, EGOGift) | Complete | Extend for planner context |
| i18n infrastructure | Complete | Add planner-specific translations |
| TanStack Query hooks | Complete | Pattern for new hooks |
| Zod validation schemas | Complete | Extend for planner data |
| Asset path utilities | Complete | Direct reuse |
| Authentication (Google OAuth) | Complete | Use for server persistence |

### Missing Components

| Component | Priority | Complexity |
|-----------|----------|------------|
| IndexedDB storage layer | P0 | Medium |
| Planner data types | P0 | Medium |
| Deck code encoder/decoder | P1 | Medium |
| Buff data and types | P0 | Medium |
| Theme pack data and types | P0 | Medium |
| Markdown editor component | P1 | Medium-High |
| Server API endpoints | P2 | High |
| Draft auto-save mechanism | P1 | Medium |

### Data Gaps

The following static data needs to be created or sourced:
- **Start Buffs**: 10 buffs with base, +, and ++ enhancement descriptions
- **Theme Packs**: Pack names, images, and associated gift lists
- **Start Gift Keywords**: 11 keyword categories with 3 gifts each

---

## Proposed Architecture

### Component Hierarchy

```
PlannerCreationPage
├── PlannerHeader
│   ├── CategorySelector (5F/10F/15F)
│   └── NameInput
├── DeckBuildingSection
│   ├── SinnerCardGrid (12 cards)
│   │   └── SinnerCard
│   │       ├── IdentityViewer
│   │       ├── SkillAffinityRow (S1/S2/S3)
│   │       ├── EGOSlotRow (5 slots)
│   │       └── DeploymentBadge
│   ├── DeckStatusPanel
│   │   ├── AffinityEAViewer
│   │   └── KeywordEAViewer
│   ├── IdentityEGOToggle
│   ├── UptieTierSelector (1-4)
│   ├── SelectionList (Identity or EGO)
│   ├── DeckActions
│   │   ├── ImportButton
│   │   ├── ExportButton
│   │   └── ResetButton
│   └── CommentEditor
├── StartBuffSection
│   ├── BuffCostIndicator
│   ├── BuffCardGrid (10 cards)
│   │   └── BuffCard
│   │       ├── BuffIcon
│   │       ├── BuffName
│   │       ├── BuffDescription
│   │       └── EnhancementButtons (+/++)
│   └── CommentEditor
├── EgoGiftSections
│   ├── StartGiftSection
│   │   ├── KeywordGrid (11 keywords × 3 gifts)
│   │   └── CommentEditor
│   ├── ObservedGiftSection
│   │   ├── GiftListWithSelection
│   │   ├── ObservedGiftViewer (3 slots)
│   │   └── CommentEditor
│   ├── SkillEASection
│   │   ├── SinnerSkillEAGrid
│   │   │   └── SinnerSkillEACard
│   │   │       ├── IdProfile
│   │   │       └── SkillEAViewer (S1/S2/S3 counts)
│   │   ├── SkillSwapPane
│   │   └── CommentEditor
│   └── ComprehensiveGiftSection
│       ├── GiftListWithHighlights
│       ├── EnhancementHoverPane
│       └── CommentEditor
├── FloorSection
│   ├── FloorThemePackViewer
│   │   └── FloorRow (per floor)
│   │       ├── FloorLabel
│   │       ├── ThemePackImage
│   │       └── ThemePackName
│   ├── ThemePackSelectorPane
│   ├── FloorGiftList
│   └── CommentEditor
└── ActionBar
    ├── SaveDraftButton
    ├── SaveButton
    └── PublishButton
```

### State Management Strategy

```typescript
// Core planner state (Zustand or Context)
interface PlannerState {
  // Metadata
  id?: string
  category: '5F' | '10F' | '15F'
  name: string

  // Deck
  sinners: SinnerDeck[]  // 12 sinners
  deploymentOrder: string[]  // sinner IDs in order
  uptieTier: 1 | 2 | 3 | 4

  // Buffs
  selectedBuffs: SelectedBuff[]

  // Gifts
  startGiftKeyword: string | null
  startGifts: string[]  // gift IDs (1-3 based on buff)
  observedGifts: [string?, string?, string?]
  skillEASwaps: SkillEASwap[]
  comprehensiveGifts: GiftWithEnhancement[]

  // Floors
  floors: FloorConfig[]

  // Comments
  comments: SectionComments

  // Meta
  isDirty: boolean
  lastSaved: Date | null
}

interface SinnerDeck {
  sinnerId: string
  identityId: string
  egoSlots: {
    zayin?: string
    teth?: string
    he?: string
    waw?: string
    aleph?: string
  }
}
```

### Storage Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    PlannerStorageService                     │
├─────────────────────────────────────────────────────────────┤
│  interface IPlannerStorage {                                 │
│    saveDraft(planner: PlannerState): Promise<void>          │
│    loadDraft(id?: string): Promise<PlannerState | null>     │
│    deleteDraft(id: string): Promise<void>                   │
│    listDrafts(): Promise<PlannerSummary[]>                  │
│    save(planner: PlannerState): Promise<string>             │
│    publish(id: string): Promise<void>                       │
│  }                                                          │
├─────────────────────────────────────────────────────────────┤
│                          │                                   │
│    ┌────────────────┐   │   ┌────────────────┐              │
│    │ IndexedDB      │   │   │ Server API     │              │
│    │ (Guest Mode)   │   │   │ (Auth Mode)    │              │
│    └────────────────┘   │   └────────────────┘              │
│                          │                                   │
└─────────────────────────────────────────────────────────────┘
```

---

## Implementation Phases

### Phase 1: Foundation (Effort: L)

**Objective:** Establish data layer, types, and core infrastructure

#### 1.1 Data Structure & Types
- [ ] Define `PlannerState` and related TypeScript interfaces
- [ ] Create Zod schemas for planner validation
- [ ] Design IndexedDB schema for planner storage
- [ ] Define buff data structure and create static data
- [ ] Define theme pack data structure and create static data

#### 1.2 Storage Infrastructure
- [ ] Implement IndexedDB wrapper service
- [ ] Create `usePlannerStorage` hook
- [ ] Implement auto-save mechanism with debouncing
- [ ] Add draft recovery on page load

#### 1.3 Base Page Setup
- [ ] Create `/planner/create` route
- [ ] Create `/planner/edit/$id` route
- [ ] Implement basic page layout with section placeholders
- [ ] Add category selector and name input
- [ ] Set up planner state context/store

### Phase 2: Deck Building (Effort: XL)

**Objective:** Implement complete identity/EGO deck building functionality

#### 2.1 Sinner Card Grid
- [ ] Create `SinnerCard` component with identity display
- [ ] Add skill affinity row (S1/S2/S3 with sin color backgrounds)
- [ ] Add EGO slot row (5 circular slots with sin color backgrounds)
- [ ] Implement deployment order badge with click to assign/unassign
- [ ] Create 12-sinner grid layout

#### 2.2 Selection System
- [ ] Create identity/EGO toggle component
- [ ] Implement uptie/threadspin tier selector (1-4)
- [ ] Extend `IdentityList` for selection mode (highlight selected, click to equip)
- [ ] Extend `EGOList` for selection mode with slot-aware equipping
- [ ] Add "selected" indicator overlay for equipped items
- [ ] Implement "equipped items first" sorting in list

#### 2.3 Status Panel
- [ ] Create affinity EA calculator (sum of skill sins + ego costs)
- [ ] Create keyword EA calculator (identities with keywords)
- [ ] Design and implement status panel UI

#### 2.4 Deck Actions
- [ ] Design deck code format (encoding scheme)
- [ ] Implement deck code encoder
- [ ] Implement deck code decoder with validation
- [ ] Create import modal with paste input
- [ ] Create copy-to-clipboard button
- [ ] Implement deployment order reset

### Phase 3: Start Buffs (Effort: M)

**Objective:** Implement buff selection with enhancement system

#### 3.1 Buff Data
- [ ] Create buff static data (10 buffs with descriptions)
- [ ] Add i18n translations for buff names and descriptions
- [ ] Define buff cost values and enhancement modifiers

#### 3.2 Buff UI
- [ ] Create `BuffCard` component
  - [ ] Icon image (upper-left)
  - [ ] Name with +/++ suffix (upper-right)
  - [ ] Description (middle, changes with enhancement)
  - [ ] Enhancement toggle buttons (bottom, mutually exclusive)
- [ ] Create buff grid layout (10 cards)
- [ ] Add cost indicator (upper-right of section)
- [ ] Implement selection state management

### Phase 4: Ego Gift Sections (Effort: XL)

**Objective:** Implement all ego gift selection modes

#### 4.1 Start Gift Section
- [ ] Create keyword grid component (11 keywords)
- [ ] Create vertical 3-gift column per keyword
- [ ] Implement single-keyword selection (radio-like)
- [ ] Implement 1-3 gift selection within keyword (based on buff)
- [ ] Link selection count to start buff configuration

#### 4.2 Observed Gift Section
- [ ] Extend `EGOGiftList` for multi-select mode (max 3)
- [ ] Create observed gift viewer (3 slots)
- [ ] Implement add/remove from selection via list click
- [ ] Implement remove from viewer via viewer click
- [ ] Add highlight styling for selected gifts

#### 4.3 Skill EA Section
- [ ] Create `SinnerSkillEACard` component
  - [ ] Identity profile image
  - [ ] S1/S2/S3 counts (default: 3/2/1)
- [ ] Create skill EA grid (12 sinners)
- [ ] Implement skill swap pane
  - [ ] S1->S2, S2->S3, S3->S1 swap buttons
  - [ ] Reset button
- [ ] Connect skill EA to affinity calculations

#### 4.4 Comprehensive Gift Section
- [ ] Extend `EGOGiftList` with highlight for pre-selected gifts
- [ ] Implement hover pane with enhancement levels
- [ ] Create enhancement level selector (clickable tiers)
- [ ] Implement selection/deselection via enhancement tier click
- [ ] Add gift to comprehensive list with chosen enhancement

### Phase 5: Floor Section (Effort: L)

**Objective:** Implement theme pack selection and floor-specific gifts

#### 5.1 Theme Pack Data
- [ ] Create theme pack static data (images, names, gift associations)
- [ ] Add i18n translations for theme pack names

#### 5.2 Theme Pack Viewer
- [ ] Create `FloorRow` component (label, image, name)
- [ ] Implement dynamic floor count based on category
- [ ] Create theme pack selector pane (modal/dropdown)
- [ ] Implement pack selection persistence

#### 5.3 Floor Gift List
- [ ] Create filtered gift list (theme pack + common gifts)
- [ ] Add placeholder text when no pack selected
- [ ] Integrate with comprehensive gift section

### Phase 6: Comment System (Effort: M)

**Objective:** Add markdown comment editors to each section

#### 6.1 Editor Component
- [ ] Implement markdown editor with preview
- [ ] Add image upload support (to CDN or base64)
- [ ] Implement draft saving (integrated with planner draft)
- [ ] Add formatting toolbar (bold, italic, links, etc.)

#### 6.2 Integration
- [ ] Add comment editor to deck building section
- [ ] Add comment editor to start buff section
- [ ] Add comment editor to each ego gift section
- [ ] Add comment editor to floor section

### Phase 7: Persistence & Actions (Effort: L)

**Objective:** Complete save/load/publish workflow

#### 7.1 Guest Persistence (IndexedDB)
- [ ] Implement full planner save to IndexedDB
- [ ] Implement planner list retrieval
- [ ] Implement planner load by ID
- [ ] Add draft recovery prompt on page load

#### 7.2 Authenticated Persistence
- [ ] Design server API endpoints for planner CRUD
- [ ] Implement save to server (authenticated users)
- [ ] Implement load from server
- [ ] Implement publish (make public)

#### 7.3 Action Bar
- [ ] Create floating/fixed action bar
- [ ] Implement save draft button
- [ ] Implement save button (final save)
- [ ] Implement publish button with confirmation
- [ ] Add loading/success/error states

### Phase 8: Polish & Testing (Effort: M)

**Objective:** Ensure quality and usability

#### 8.1 UX Improvements
- [ ] Add loading states for all async operations
- [ ] Implement error handling with user-friendly messages
- [ ] Add confirmation dialogs for destructive actions
- [ ] Ensure keyboard navigation
- [ ] Optimize for mobile/tablet

#### 8.2 Testing
- [ ] Unit tests for deck code encoder/decoder
- [ ] Unit tests for affinity/keyword calculators
- [ ] Integration tests for IndexedDB storage
- [ ] Component tests for key interactions
- [ ] E2E tests for full planner creation flow

#### 8.3 i18n
- [ ] Add all planner UI translations (EN, JP, KR, CN)
- [ ] Translate buff names and descriptions
- [ ] Translate theme pack names
- [ ] Ensure i18n for all dynamic content

---

## Risk Assessment & Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Missing game data (buffs, theme packs) | High | High | Create data extraction pipeline; use placeholder data initially |
| Complex state management | Medium | High | Use Zustand for global state; careful action design |
| IndexedDB browser compatibility | Low | Medium | Use idb library; implement localStorage fallback |
| Deck code format conflicts | Medium | Medium | Version the format; add validation |
| Performance with large gift lists | Medium | Medium | Virtualize lists; memoize calculations |
| Image upload complexity | Medium | Low | Start with base64; defer CDN integration |
| Server API delays | High | Medium | Prioritize IndexedDB; server sync can be P2 |

---

## Success Metrics

### Functional Metrics
- [ ] User can create a complete planner with all sections
- [ ] Draft auto-saves every 30 seconds
- [ ] Deck codes import/export correctly
- [ ] All 12 sinners can be configured
- [ ] Floor count matches category selection

### Performance Metrics
- [ ] Page load < 2 seconds
- [ ] Section interactions < 100ms response
- [ ] Auto-save < 500ms
- [ ] List virtualization handles 500+ items

### Quality Metrics
- [ ] 80%+ test coverage for utilities
- [ ] Zero TypeScript errors
- [ ] All i18n keys translated
- [ ] Responsive on 320px+ screens

---

## Dependencies

### External Dependencies
- `idb` - IndexedDB wrapper (npm package)
- Markdown editor library (TBD: `react-md-editor` or similar)
- Image upload solution (TBD)

### Internal Dependencies
- Buff static data must be created
- Theme pack static data must be created
- Start gift keyword mapping must be created
- Server API (for Phase 7.2) requires backend work

### Blocked Dependencies
- Publish functionality blocked on server API
- Server sync blocked on authentication enhancements

---

## Resource Requirements

### Development Effort
| Phase | Effort | Estimated Complexity |
|-------|--------|---------------------|
| Phase 1: Foundation | L | Setup, types, storage |
| Phase 2: Deck Building | XL | Core feature, most complex |
| Phase 3: Start Buffs | M | Straightforward UI |
| Phase 4: Ego Gift Sections | XL | Multiple sub-features |
| Phase 5: Floor Section | L | Moderate complexity |
| Phase 6: Comment System | M | External library integration |
| Phase 7: Persistence | L | IndexedDB + API |
| Phase 8: Polish | M | Testing, i18n, UX |

### Data Creation Effort
- Buff data: ~2 hours
- Theme pack data: ~4 hours
- Start gift keyword mapping: ~2 hours
- i18n translations: ~8 hours (all languages)

---

## Appendix: Deck Code Format Proposal

```
Version 1 Format:
[version:1][category:1][identities:12x5bytes][egos:12x5bytes][deployment:12bytes]

Encoding:
- Version: 1 byte (0x01)
- Category: 1 byte (0=5F, 1=10F, 2=15F)
- Identity: 5 bytes per sinner (id as base64)
- EGO: 5 bytes per sinner (5 slots packed)
- Deployment: 12 bytes (order array)

Total: ~130 bytes → ~175 chars base64
```

---

## Appendix: UI Wireframe References

### Deck Building Section Layout
```
┌─────────────────────────────────────────────────────────────────┐
│ [Category ▼] [Plan Name Input________________]                  │
├─────────────────────────────────────────────────────────────────┤
│ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ (12 sinners)   │
│ │ #1  │ │ #2  │ │     │ │ #3  │ │     │ │     │                │
│ │ ID  │ │ ID  │ │ ID  │ │ ID  │ │ ID  │ │ ID  │                │
│ │S1S2S│ │S1S2S│ │S1S2S│ │S1S2S│ │S1S2S│ │S1S2S│                │
│ │○○○○○│ │○○○○○│ │○○○○○│ │○○○○○│ │○○○○○│ │○○○○○│                │
│ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘                │
│ ... (remaining 6 sinners)                                       │
├─────────────────────────────────────────────────────────────────┤
│ Affinity EA: Wrath(5) Lust(3) Sloth(4) ...                     │
│ Keywords: Burn(3) Bleed(2) ...                                  │
├─────────────────────────────────────────────────────────────────┤
│ [Identity ○ EGO] [Uptie: 1 2 3 4]                              │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ [Search____________] [Sort ▼]                               │ │
│ │ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ (scrollable list)      │ │
│ │ │ ✓ │ │   │ │   │ │ ✓ │ │   │ │   │                        │ │
│ │ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘                        │ │
│ └─────────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│ [Import] [Export] [Reset Order]                                 │
├─────────────────────────────────────────────────────────────────┤
│ [Comment Editor]                                                │
└─────────────────────────────────────────────────────────────────┘
```
