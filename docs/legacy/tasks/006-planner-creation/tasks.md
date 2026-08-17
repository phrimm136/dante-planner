# Planner Creation - Task Checklist

**Last Updated:** 2025-12-22

---

## Phase 1: Foundation

### 1.1 Data Structures & Types
**Effort:** M | **Priority:** P0

- [ ] **[P0/S]** Create `frontend/src/types/PlannerTypes.ts`
  - [ ] Define `PlannerCategory` type ('5F' | '10F' | '15F')
  - [ ] Define `SinnerDeck` interface (sinnerId, identityId, egoSlots)
  - [ ] Define `EgoSlots` interface (zayin, teth, he, waw, aleph)
  - [ ] Define `SelectedBuff` interface (buffId, enhancement: 0|1|2)
  - [ ] Define `SkillEASwap` interface (sinnerId, s1, s2, s3)
  - [ ] Define `GiftWithEnhancement` interface (giftId, enhancement)
  - [ ] Define `FloorConfig` interface (floor, themePackId, gifts)
  - [ ] Define `SectionComments` interface (per-section comment strings)
  - [ ] Define `PlannerState` interface (complete planner state)
  - [ ] Define `PlannerSummary` interface (for list views)
  - **Acceptance:** All types compile; comprehensive coverage of epic requirements

- [ ] **[P0/S]** Create `frontend/src/schemas/plannerSchemas.ts`
  - [ ] Create Zod schema for PlannerState
  - [ ] Create Zod schema for SinnerDeck
  - [ ] Create Zod schema for SelectedBuff
  - [ ] Add validation helpers (validatePlanner)
  - **Acceptance:** All planner data can be validated at runtime

- [ ] **[P0/M]** Create buff static data
  - [ ] Create `static/data/buffs/buffSpecList.json` (10 buffs)
  - [ ] Create individual buff data files with costs
  - [ ] Create i18n files (EN, JP, KR, CN) for buff names/descriptions
  - [ ] Include base, +, and ++ enhancement descriptions
  - **Acceptance:** 10 buffs with complete data and translations

- [ ] **[P0/M]** Create theme pack static data
  - [ ] Create `static/data/themePacks/themePackSpecList.json`
  - [ ] Include pack names, image references, associated gifts
  - [ ] Create i18n files for theme pack names
  - **Acceptance:** Theme packs match in-game data

- [ ] **[P1/S]** Define start gift keyword mapping
  - [ ] Create `static/data/startGifts.json` (11 keywords × 3 gifts)
  - **Acceptance:** All 11 keywords mapped to 3 gifts each

### 1.2 Storage Infrastructure
**Effort:** M | **Priority:** P0

- [ ] **[P0/M]** Create IndexedDB wrapper
  - [ ] Create `frontend/src/lib/indexedDB.ts`
  - [ ] Define database schema (planners table)
  - [ ] Implement open/close connection
  - [ ] Implement CRUD operations (create, read, update, delete)
  - [ ] Implement list with pagination
  - [ ] Add error handling and retry logic
  - **Acceptance:** Can store and retrieve planner objects

- [ ] **[P0/M]** Create `frontend/src/hooks/usePlannerStorage.ts`
  - [ ] Implement `saveDraft` function
  - [ ] Implement `loadDraft` function
  - [ ] Implement `deleteDraft` function
  - [ ] Implement `listDrafts` function
  - [ ] Add loading and error states
  - **Acceptance:** Hook provides complete storage API

- [ ] **[P1/S]** Implement auto-save mechanism
  - [ ] Create debounced save function (30s interval)
  - [ ] Trigger save on section blur
  - [ ] Add isDirty tracking
  - [ ] Show save status indicator
  - **Acceptance:** Changes auto-saved without user action

- [ ] **[P1/S]** Implement draft recovery
  - [ ] Check for existing draft on page load
  - [ ] Show recovery prompt modal
  - [ ] Allow dismiss or restore
  - **Acceptance:** User can recover unsaved work

### 1.3 Base Page Setup
**Effort:** M | **Priority:** P0

- [ ] **[P0/S]** Create planner routes
  - [ ] Add `/planner/create` route in router.tsx
  - [ ] Add `/planner/edit/$id` route in router.tsx
  - [ ] Create route components with lazy loading
  - **Acceptance:** Routes accessible and render placeholder

- [ ] **[P0/M]** Create `frontend/src/routes/planner/create.tsx`
  - [ ] Set up basic page layout
  - [ ] Add section containers with placeholders
  - [ ] Add page title and navigation
  - **Acceptance:** Page renders with all section placeholders

- [ ] **[P0/S]** Implement PlannerHeader component
  - [ ] Create category selector (5F/10F/15F dropdown)
  - [ ] Create name input field
  - [ ] Connect to planner state
  - **Acceptance:** User can set category and name

- [ ] **[P0/M]** Set up planner state context
  - [ ] Create PlannerContext with Provider
  - [ ] Implement useReducer for state management
  - [ ] Define all action types
  - [ ] Initialize with default state
  - **Acceptance:** State accessible throughout planner pages

---

## Phase 2: Deck Building Section

### 2.1 Sinner Card Grid
**Effort:** L | **Priority:** P0

- [ ] **[P0/L]** Create `SinnerCard` component
  - [ ] Display identity image (reuse IdentityCard core)
  - [ ] Add skill affinity row (3 colored circles for S1/S2/S3)
  - [ ] Add EGO slot row (5 circular slots with sin colors)
  - [ ] Add deployment badge (order number, click handler)
  - [ ] Handle empty/default states
  - **Acceptance:** Card shows identity, skills, EGOs, deployment order

- [ ] **[P0/M]** Create SkillAffinityRow component
  - [ ] Display 3 skill icons with sin-colored backgrounds
  - [ ] Use existing sin color mapping
  - [ ] Show skill type icon (slash/pierce/blunt)
  - **Acceptance:** Skills visually distinguish sin affinities

- [ ] **[P0/M]** Create EGOSlotRow component
  - [ ] Display 5 circular EGO slots (by rank)
  - [ ] Show circular EGO image when equipped
  - [ ] Show sin-colored background based on EGO
  - [ ] Handle empty slots (placeholder)
  - **Acceptance:** EGO equipment visible for all 5 ranks

- [ ] **[P0/M]** Create DeploymentBadge component
  - [ ] Show order number (#1-#12)
  - [ ] Visual distinction for deployed (#1-#7) vs backup (#8-#12)
  - [ ] Click to toggle assignment/unassignment
  - [ ] Reorder logic when removed from middle
  - **Acceptance:** Order correctly updates on interaction

- [ ] **[P0/S]** Create SinnerCardGrid layout
  - [ ] Arrange 12 cards in responsive grid
  - [ ] Handle mobile layout (2 columns)
  - [ ] Desktop layout (6 columns)
  - **Acceptance:** Grid responsive across breakpoints

### 2.2 Selection System
**Effort:** L | **Priority:** P0

- [ ] **[P0/S]** Create IdentityEGOToggle component
  - [ ] Toggle button between Identity and EGO modes
  - [ ] Update list content based on selection
  - [ ] Persist toggle state in component
  - **Acceptance:** Toggle switches list content

- [ ] **[P0/S]** Create UptieTierSelector component
  - [ ] 4 tier buttons (1-4)
  - [ ] Single selection (radio behavior)
  - [ ] Connect to planner state
  - **Acceptance:** Tier selection affects skill data displayed

- [ ] **[P0/M]** Extend IdentityList for selection mode
  - [ ] Add `selectionMode` prop
  - [ ] Add `selectedId` prop for current selection
  - [ ] Add `onSelect` callback
  - [ ] Highlight selected identity
  - [ ] Move selected to top of list
  - [ ] Filter by current sinner
  - **Acceptance:** Click selects identity for current sinner

- [ ] **[P0/M]** Extend EGOList for selection mode
  - [ ] Add `selectionMode` prop
  - [ ] Add `selectedEgos` prop (5 slots)
  - [ ] Add `onSelect` callback with rank awareness
  - [ ] Smart equip logic (fill empty or replace same rank)
  - [ ] Highlight equipped EGOs
  - [ ] Move equipped to top
  - [ ] Filter by current sinner
  - **Acceptance:** Click equips EGO to appropriate slot

- [ ] **[P0/S]** Add selection indicator overlay
  - [ ] Create checkmark/highlight overlay component
  - [ ] Apply to selected items in list
  - **Acceptance:** Clear visual for selected items

### 2.3 Status Panel
**Effort:** M | **Priority:** P1

- [ ] **[P1/M]** Create AffinityEAViewer component
  - [ ] Calculate sin totals from all selected identities
  - [ ] Include EGO costs in calculation
  - [ ] Display 7 sin types with counts
  - [ ] Color-code by sin
  - **Acceptance:** Accurate affinity totals displayed

- [ ] **[P1/M]** Create KeywordEAViewer component
  - [ ] Aggregate keywords from all identities
  - [ ] Count occurrences per keyword
  - [ ] Display keyword list with counts
  - **Acceptance:** Keywords correctly aggregated

- [ ] **[P1/S]** Create DeckStatusPanel layout
  - [ ] Combine AffinityEAViewer and KeywordEAViewer
  - [ ] Responsive layout
  - **Acceptance:** Status panel shows all calculated values

### 2.4 Deck Actions
**Effort:** M | **Priority:** P1

- [ ] **[P1/M]** Design deck code format
  - [ ] Document encoding specification
  - [ ] Include version byte for compatibility
  - [ ] Test encoding capacity
  - **Acceptance:** Format documented and validated

- [ ] **[P1/M]** Create `frontend/src/lib/deckCode.ts`
  - [ ] Implement `encodeDeck` function
  - [ ] Implement `decodeDeck` function
  - [ ] Add validation for decoded data
  - [ ] Handle version migration
  - [ ] Add comprehensive error handling
  - **Acceptance:** Roundtrip encode/decode produces identical data

- [ ] **[P1/S]** Create ImportDeckModal
  - [ ] Text input for deck code
  - [ ] Validate button
  - [ ] Error display for invalid codes
  - [ ] Apply button to load deck
  - **Acceptance:** Can import valid deck codes

- [ ] **[P1/S]** Create ExportDeckButton
  - [ ] Generate code from current state
  - [ ] Copy to clipboard
  - [ ] Show success toast
  - **Acceptance:** Deck code copied to clipboard

- [ ] **[P1/S]** Create ResetOrderButton
  - [ ] Confirmation dialog
  - [ ] Reset all deployment orders
  - **Acceptance:** Deployment order cleared on confirm

---

## Phase 3: Start Buffs Section

### 3.1 Buff Data
**Effort:** S | **Priority:** P0
(Covered in Phase 1.1)

### 3.2 Buff UI
**Effort:** M | **Priority:** P0

- [ ] **[P0/M]** Create BuffCard component
  - [ ] Icon image display (upper-left)
  - [ ] Name with +/++ suffix (upper-right)
  - [ ] Description text (middle, changes with enhancement)
  - [ ] Enhancement buttons (+, ++) mutually exclusive
  - [ ] Selection state styling
  - **Acceptance:** Card shows all buff info, enhancement toggleable

- [ ] **[P0/S]** Create BuffCardGrid layout
  - [ ] 10 buff cards in grid
  - [ ] Responsive (2 cols mobile, 5 cols desktop)
  - **Acceptance:** All 10 buffs visible and selectable

- [ ] **[P0/S]** Create BuffCostIndicator component
  - [ ] Calculate total cost of selected buffs
  - [ ] Include enhancement cost modifiers
  - [ ] Display prominently (upper-right)
  - **Acceptance:** Cost updates as buffs selected

- [ ] **[P0/S]** Create StartBuffSection container
  - [ ] Header with section title
  - [ ] Cost indicator position
  - [ ] Buff grid
  - [ ] Comment editor slot
  - **Acceptance:** Complete section layout

---

## Phase 4: Ego Gift Sections

### 4.1 Start Gift Section
**Effort:** M | **Priority:** P0

- [ ] **[P0/M]** Create KeywordGiftColumn component
  - [ ] Keyword header
  - [ ] 3 vertical gift slots
  - [ ] Selection state per gift
  - **Acceptance:** Column displays keyword and its 3 gifts

- [ ] **[P0/M]** Create StartGiftGrid component
  - [ ] 11 keyword columns
  - [ ] Horizontal scrolling on mobile
  - [ ] Single keyword selection (radio)
  - [ ] 1-3 gift selection within keyword (based on buff)
  - **Acceptance:** Can select keyword and gifts within it

- [ ] **[P0/S]** Create StartGiftSection container
  - [ ] Section header
  - [ ] Link to buff selection count
  - [ ] Grid component
  - [ ] Comment editor slot
  - **Acceptance:** Section integrates with buff state

### 4.2 Observed Gift Section
**Effort:** M | **Priority:** P0

- [ ] **[P0/M]** Extend EGOGiftList for multi-select
  - [ ] Add `multiSelectMode` prop
  - [ ] Add `maxSelections` prop (3)
  - [ ] Add `selectedIds` prop
  - [ ] Add `onSelectionChange` callback
  - [ ] Toggle selection on click
  - [ ] Highlight selected gifts
  - **Acceptance:** Can select up to 3 gifts from list

- [ ] **[P0/M]** Create ObservedGiftViewer component
  - [ ] Display 3 selected gift slots
  - [ ] Show gift card in each slot
  - [ ] Click to remove from selection
  - [ ] Empty slot placeholder
  - **Acceptance:** Shows selected gifts, click removes

- [ ] **[P0/S]** Create ObservedGiftSection container
  - [ ] Two-column layout (list left, viewer right)
  - [ ] Section header
  - [ ] Comment editor slot
  - **Acceptance:** Complete section with selection flow

### 4.3 Skill EA Section
**Effort:** M | **Priority:** P1

- [ ] **[P1/M]** Create SinnerSkillEACard component
  - [ ] Identity profile image
  - [ ] S1/S2/S3 count display (default 3/2/1)
  - [ ] Click to open swap pane
  - **Acceptance:** Card shows current skill EA distribution

- [ ] **[P1/M]** Create SkillSwapPane component
  - [ ] S1->S2 button (decrement S1, increment S2)
  - [ ] S2->S3 button
  - [ ] S3->S1 button
  - [ ] Reset button (back to 3/2/1)
  - [ ] Close button
  - **Acceptance:** Can modify skill EA distribution

- [ ] **[P1/S]** Create SinnerSkillEAGrid
  - [ ] 12 sinner cards
  - [ ] Modal/popover for swap pane
  - **Acceptance:** All sinners configurable

- [ ] **[P1/S]** Create SkillEASection container
  - [ ] Section header
  - [ ] Grid component
  - [ ] Comment editor slot
  - **Acceptance:** Complete section layout

### 4.4 Comprehensive Gift Section
**Effort:** L | **Priority:** P1

- [ ] **[P1/M]** Extend EGOGiftList for highlight mode
  - [ ] Add `highlightedIds` prop (from other sections)
  - [ ] Distinct styling for highlighted vs selectable
  - [ ] Allow clicking non-highlighted to add
  - **Acceptance:** Pre-selected gifts visually distinct

- [ ] **[P1/M]** Create EnhancementHoverPane component
  - [ ] Show on gift hover
  - [ ] Display enhancement levels (0, 1, 2, ...)
  - [ ] Clickable levels to select with enhancement
  - [ ] Click active level to deselect
  - [ ] Show enhancement descriptions
  - **Acceptance:** Can select gift with specific enhancement

- [ ] **[P1/S]** Create ComprehensiveGiftSection container
  - [ ] Section header
  - [ ] Extended gift list with highlights
  - [ ] Comment editor slot
  - **Acceptance:** Can add gifts beyond other sections

---

## Phase 5: Floor Section

### 5.1 Theme Pack Data
**Effort:** S | **Priority:** P0
(Covered in Phase 1.1)

### 5.2 Theme Pack Viewer
**Effort:** M | **Priority:** P1

- [ ] **[P1/M]** Create FloorRow component
  - [ ] Floor label (1F, 2F, ...)
  - [ ] Theme pack image (clickable)
  - [ ] Theme pack name
  - [ ] Click opens selector
  - **Acceptance:** Shows floor with current pack selection

- [ ] **[P1/M]** Create ThemePackSelector component
  - [ ] Grid/list of all theme packs
  - [ ] Pack image and name
  - [ ] Click to select
  - [ ] Close on selection
  - **Acceptance:** Can select theme pack for floor

- [ ] **[P1/M]** Create FloorThemePackViewer component
  - [ ] Dynamic floor count based on category
  - [ ] List of FloorRow components
  - [ ] Scroll for many floors
  - **Acceptance:** All floors configurable

### 5.3 Floor Gift List
**Effort:** M | **Priority:** P1

- [ ] **[P1/M]** Create FloorGiftList component
  - [ ] Filter gifts by selected theme pack
  - [ ] Include common (non-pack) gifts
  - [ ] Show placeholder when no pack selected
  - [ ] Selection mode for adding to comprehensive list
  - **Acceptance:** Shows relevant gifts for floor

- [ ] **[P1/S]** Create FloorSection container
  - [ ] Two-column layout (viewer left, list right)
  - [ ] Section header
  - [ ] Comment editor slot
  - **Acceptance:** Complete floor configuration section

---

## Phase 6: Comment System

### 6.1 Editor Component
**Effort:** M | **Priority:** P1

- [ ] **[P1/M]** Install and configure markdown editor
  - [ ] Add `@uiw/react-md-editor` dependency
  - [ ] Create wrapper component
  - [ ] Style to match app theme
  - **Acceptance:** Editor renders with preview mode

- [ ] **[P1/M]** Create CommentEditor component
  - [ ] Markdown editing with preview toggle
  - [ ] Formatting toolbar (bold, italic, links, lists)
  - [ ] Auto-save integration
  - [ ] Character limit indicator
  - **Acceptance:** Can write and preview markdown

- [ ] **[P2/M]** Add image upload support
  - [ ] Drag-and-drop images
  - [ ] Convert to base64 or upload to CDN
  - [ ] Insert image markdown
  - **Acceptance:** Images can be embedded in comments

### 6.2 Integration
**Effort:** S | **Priority:** P1

- [ ] **[P1/S]** Add CommentEditor to DeckBuildingSection
- [ ] **[P1/S]** Add CommentEditor to StartBuffSection
- [ ] **[P1/S]** Add CommentEditor to StartGiftSection
- [ ] **[P1/S]** Add CommentEditor to ObservedGiftSection
- [ ] **[P1/S]** Add CommentEditor to SkillEASection
- [ ] **[P1/S]** Add CommentEditor to ComprehensiveGiftSection
- [ ] **[P1/S]** Add CommentEditor to FloorSection
  - **Acceptance:** All sections have comment editors

---

## Phase 7: Persistence & Actions

### 7.1 Guest Persistence (IndexedDB)
**Effort:** S | **Priority:** P0
(Covered in Phase 1.2)

### 7.2 Authenticated Persistence
**Effort:** L | **Priority:** P2

- [ ] **[P2/L]** Design server API endpoints
  - [ ] POST /api/planners - Create planner
  - [ ] GET /api/planners/:id - Get planner
  - [ ] PUT /api/planners/:id - Update planner
  - [ ] DELETE /api/planners/:id - Delete planner
  - [ ] POST /api/planners/:id/publish - Publish planner
  - [ ] GET /api/planners - List user's planners
  - **Acceptance:** API spec documented

- [ ] **[P2/M]** Implement server sync in storage hook
  - [ ] Detect authentication state
  - [ ] Sync to server when authenticated
  - [ ] Handle conflicts (last-write-wins)
  - **Acceptance:** Authenticated users sync to server

- [ ] **[P2/S]** Implement publish functionality
  - [ ] Mark planner as public
  - [ ] Generate shareable URL
  - **Acceptance:** Published planners viewable by others

### 7.3 Action Bar
**Effort:** S | **Priority:** P0

- [ ] **[P0/S]** Create ActionBar component
  - [ ] Fixed/floating position at bottom
  - [ ] Save Draft button
  - [ ] Save button
  - [ ] Publish button (if authenticated)
  - **Acceptance:** Actions accessible from any scroll position

- [ ] **[P0/S]** Implement button states
  - [ ] Loading state during save
  - [ ] Success feedback (toast/icon)
  - [ ] Error handling with retry
  - **Acceptance:** User knows save status

- [ ] **[P1/S]** Add confirmation dialogs
  - [ ] Confirm before publish
  - [ ] Confirm before discard changes
  - **Acceptance:** Destructive actions require confirmation

---

## Phase 8: Polish & Testing

### 8.1 UX Improvements
**Effort:** M | **Priority:** P1

- [ ] **[P1/S]** Add loading states to all sections
- [ ] **[P1/S]** Implement error boundaries per section
- [ ] **[P1/S]** Add tooltips for complex interactions
- [ ] **[P1/M]** Implement keyboard navigation
  - [ ] Tab through sections
  - [ ] Arrow keys in grids
  - [ ] Enter to select
- [ ] **[P1/M]** Optimize mobile layout
  - [ ] Collapsible sections
  - [ ] Touch-friendly tap targets
  - [ ] Horizontal scrolling where needed
- [ ] **[P1/S]** Add unsaved changes warning on navigate away

### 8.2 Testing
**Effort:** M | **Priority:** P1

- [ ] **[P1/M]** Unit tests for deckCode.ts
  - [ ] Test encode/decode roundtrip
  - [ ] Test version handling
  - [ ] Test error cases
- [ ] **[P1/M]** Unit tests for calculators
  - [ ] Test affinity calculator
  - [ ] Test keyword aggregator
  - [ ] Test buff cost calculator
- [ ] **[P1/M]** Unit tests for planner reducer
  - [ ] Test all action types
  - [ ] Test state transitions
- [ ] **[P1/S]** Integration tests for IndexedDB
  - [ ] Test CRUD operations
  - [ ] Test draft recovery
- [ ] **[P2/L]** E2E tests for full flow
  - [ ] Create planner flow
  - [ ] Edit planner flow
  - [ ] Import/export flow

### 8.3 i18n
**Effort:** M | **Priority:** P1

- [ ] **[P1/M]** Add EN translations
  - [ ] Section titles
  - [ ] Button labels
  - [ ] Placeholder text
  - [ ] Error messages
- [ ] **[P1/M]** Add JP translations
- [ ] **[P1/M]** Add KR translations
- [ ] **[P1/M]** Add CN translations
- [ ] **[P1/S]** Test all supported languages
  - **Acceptance:** All UI text translated in 4 languages

---

## Summary Metrics

| Phase | Tasks | P0 | P1 | P2 | Effort |
|-------|-------|----|----|----|----|
| Phase 1: Foundation | 18 | 14 | 4 | 0 | L |
| Phase 2: Deck Building | 22 | 17 | 5 | 0 | XL |
| Phase 3: Start Buffs | 4 | 4 | 0 | 0 | M |
| Phase 4: Ego Gifts | 14 | 6 | 8 | 0 | XL |
| Phase 5: Floors | 6 | 0 | 6 | 0 | L |
| Phase 6: Comments | 9 | 0 | 8 | 1 | M |
| Phase 7: Persistence | 9 | 2 | 3 | 4 | L |
| Phase 8: Polish | 17 | 0 | 15 | 2 | M |
| **Total** | **99** | **43** | **49** | **7** | - |

---

## Quick Start Order

For fastest path to working MVP:

1. Phase 1.1: Types and schemas
2. Phase 1.3: Base page setup
3. Phase 2.1: Sinner card grid
4. Phase 2.2: Selection system (Identity first)
5. Phase 3.2: Buff UI
6. Phase 4.1: Start gift section
7. Phase 4.2: Observed gift section
8. Phase 1.2: Storage infrastructure
9. Phase 7.3: Action bar

This order produces a functional (if incomplete) planner that can save drafts.
