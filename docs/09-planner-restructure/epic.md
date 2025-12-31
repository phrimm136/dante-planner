# Epic: Planner Editor Restructure

## Problem Statement

The current PlannerMDNewPage uses inline editing where all configuration options are displayed on the main page. This causes:

1. **Page length bloat**: Too many controls visible, making the page longer than expected
2. **Accidental modifications**: Mis-clicks/touches on interactive elements modify state unintentionally
3. **Cognitive overload**: Too many UI controls visible at once

## Solution Overview

Transition to a **"View + Edit Pane"** pattern where:
- Main page shows **compact viewers** with read-only or limited interaction
- Clicking viewers opens **popup panes** for detailed editing
- Follows the proven **FloorTheme pattern** already used 15 times on this page

---

## Section Specifications

### 1. Deck Builder

#### Main Page (Viewer)
| Element | Behavior |
|---------|----------|
| SinnerGrid | Read-only display of 12 sinners with equipped identity/EGO |
| StatusViewer | Affinity EA and keyword EA counts (read-only) |
| Import/Export buttons | Opens import dialog / copies deck code |
| Deployment indicators | Shows current order (#1-#7 deploy, #8-#12 backup) but **no click-to-reorder** |

#### Edit Pane (Dialog)
| Element | Behavior |
|---------|----------|
| EntityToggle | Switch between Identity/EGO lists |
| SinnerFilter | Filter by sinner |
| KeywordFilter | Filter by keyword |
| SearchBar | Search by name |
| TierLevelSelector | Uptie/threadspin tier (1-4) |
| Identity/EGO List | Click to equip |
| Deployment Order | Click sinners to set/reset order |
| Reset Order button | Clear all deployment assignments |

---

### 2. Start Buff

#### Main Page (Viewer)
| Element | Behavior |
|---------|----------|
| 10 Buff Cards | Display all 10 buffs with current selection state (read-only) |
| Enhancement Buttons | **Hidden** - no enhancement buttons in viewer mode |
| Cost Indicator | Total cost of selected buffs (read-only display) |
| Selection state | Cards show selected/unselected visually via highlight |
| Click action | Clicking section opens edit pane |

#### Edit Pane (Dialog)
| Element | Behavior |
|---------|----------|
| 10 Buff Cards | Full cards with enhancement buttons (+, ++) |
| Selection Toggle | Clicking card toggles selection |
| Enhancement Buttons | Visible and functional |
| Cost Indicator | Real-time cost calculation |

---

### 3. Start Gift

#### Main Page (Viewer)
| Element | Behavior |
|---------|----------|
| Summary Row | Shows: selected keyword name + selected gift count (e.g., "Charge: 2 gifts") |
| Visual indicator | Keyword icon + gift icons if space permits |
| Click action | Opens edit pane |

#### Edit Pane (Dialog)
| Element | Behavior |
|---------|----------|
| 10 Keyword Rows | Full grid of keywords |
| 3 Gifts per Keyword | Selectable gifts per keyword row |
| Selection logic | Only one keyword selectable; 1-3 gifts within that keyword |
| Max selection indicator | Based on start buff selection |

---

### 4. Comprehensive EGO Gift

#### Main Page (Viewer)
| Element | Behavior |
|---------|----------|
| Selected Gift Cards | Grid of EGO gift cards showing **only selected gifts** |
| Filter Bar | KeywordFilter, Sorter, SearchBar - filters the **selected** gifts for browsing |
| Click action | Opens edit pane |

#### Edit Pane (Dialog - Large)
| Element | Behavior |
|---------|----------|
| Filter Bar | KeywordFilter, Sorter, SearchBar - filters **all available** gifts |
| Full Gift List | All comprehensive gifts (excluding start/observation/floor gifts) |
| Enhancement Selection | Hover shows available levels; click to select with enhancement |
| Selection indicators | Selected gifts highlighted |

**Size requirement**: Larger than FloorTheme panes (~80vh height or expandable)

---

### 5. Unchanged Sections

| Section | Reason |
|---------|--------|
| EGO Gift Observation | Already well-structured with left list + right showroom |
| Floor Theme Gift (×15) | Already follows the target pattern |
| Skill Replacement | Separate modal workflow, not inline |
| Note Editors | Independent rich text areas |

---

## Component Architecture

### Component Structure Pattern

**IMPORTANT:** To avoid double borders and duplicate wrappers, follow this structure:

```
Main Page:
  Section (with PlannerSection wrapper + clickable container)
    └── Content (pure content, no wrapper)

Edit Pane (Dialog):
  Dialog wrapper
    └── Content (pure content, no wrapper) ← Same content component, reused
```

**Implementation Pattern:**
1. Extract shared logic into a custom hook (`use*Content.ts`)
2. Create pure content component (`*Content.tsx`) that uses the hook
3. Section component (`*Section.tsx`) wraps Content with PlannerSection + click handler
4. Edit pane (`*EditPane.tsx`) wraps Content with Dialog only

Example (StartBuff):
- `useStartBuffGrid.ts` - shared state and logic
- `StartBuffGrid.tsx` - pure content, uses hook
- `StartBuffSection.tsx` - PlannerSection + clickable wrapper + StartBuffGrid
- `StartBuffEditPane.tsx` - Dialog + StartBuffGrid

### New Components to Create

| Component | Purpose | Location |
|-----------|---------|----------|
| `useStartBuffGrid.ts` | Shared state/logic hook | `components/startBuff/` |
| `StartBuffGrid.tsx` | Pure content (buff cards) | `components/startBuff/` |
| `StartBuffEditPane.tsx` | Dialog wrapper | `components/startBuff/` |
| `useDeckBuilderContent.ts` | Shared state/logic hook | `components/deckBuilder/` |
| `DeckBuilderContent.tsx` | Pure content | `components/deckBuilder/` |
| `DeckBuilderEditPane.tsx` | Dialog with filters, deploy order | `components/deckBuilder/` |
| `useStartGiftContent.ts` | Shared state/logic hook | `components/startGift/` |
| `StartGiftContent.tsx` | Pure keyword/gift content | `components/startGift/` |
| `StartGiftViewer.tsx` | Compact summary display | `components/startGift/` |
| `StartGiftEditPane.tsx` | Dialog with full content | `components/startGift/` |
| `EGOGiftComprehensiveEditPane.tsx` | Large dialog with filters | `components/egoGift/` |

### Components to Modify

| Component | Change |
|-----------|--------|
| `StartBuffSection.tsx` | Use StartBuffGrid, add viewMode/onClick props |
| `StartBuffCard.tsx` | Add `viewMode` prop to hide buttons |
| `DeckBuilder.tsx` | Use DeckBuilderContent, add viewMode/onClick props |
| `StartGiftSection.tsx` | Use StartGiftContent or StartGiftViewer |
| `EGOGiftComprehensiveListSection.tsx` | Filter to show only selected gifts |
| `PlannerMDNewPage.tsx` | Add pane visibility state; wire up dialogs |

---

## State Management

### Parent State (PlannerMDNewPage)

No changes to existing state structure:

```typescript
// Existing state - unchanged
const [equipment, setEquipment] = useState<Record<string, SinnerEquipment>>({})
const [deploymentOrder, setDeploymentOrder] = useState<number[]>([])
const [selectedBuffIds, setSelectedBuffIds] = useState<Set<number>>(new Set())
const [selectedKeyword, setSelectedKeyword] = useState<string | null>(null)
const [selectedGiftIds, setSelectedGiftIds] = useState<Set<string>>(new Set())
const [comprehensiveGiftIds, setComprehensiveGiftIds] = useState<Set<string>>(new Set())

// NEW: Pane visibility state
const [activePaneId, setActivePaneId] = useState<PaneId | null>(null)
type PaneId = 'deck' | 'buff' | 'gift' | 'comprehensive'
```

### Data Flow

```
PlannerMDNewPage (state owner)
    │
    ├── Viewer Components (receive state as props, read-only)
    │     └── onClick → setActivePaneId('section')
    │
    └── Edit Pane Components (receive state + setters as props)
          └── onSelect → setState callbacks
          └── onClose → setActivePaneId(null)
```

---

## UI Pattern Reference

### FloorTheme Pattern (Target)

```tsx
// Viewer (compact, read-only)
<FloorThemeGiftSection>
  <FloorLabel />
  <ThemePackViewer onClick={() => openPane('pack')} />
  <FloorGiftViewer onClick={() => openPane('gift')} />
</FloorThemeGiftSection>

// Pane (full editor)
<Dialog open={pane === 'pack'}>
  <ThemePackSelectorPane onSelect={handleSelect} onClose={closePane} />
</Dialog>
```

Apply this pattern to DeckBuilder, StartBuff, StartGift, and Comprehensive EGO Gift.

---

## Implementation Phases

### Phase 1: StartBuff
- Modify `StartBuffCard.tsx` to support `selectable` prop
- Create `StartBuffEditPane.tsx`
- Wire up in `PlannerMDNewPage.tsx`

### Phase 2: StartGift
- Create `StartGiftViewer.tsx`
- Create `StartGiftEditPane.tsx`
- Replace `StartGiftSection.tsx` usage

### Phase 3: DeckBuilder
- Create `DeckBuilderEditPane.tsx`
- Refactor `DeckBuilder.tsx` to extract filters
- Keep grid + status inline

### Phase 4: Comprehensive EGO Gift
- Create `EGOGiftComprehensiveEditPane.tsx`
- Modify `EGOGiftComprehensiveListSection.tsx` to filter selected only
- Add larger dialog sizing

---

## Acceptance Criteria

- [ ] Main page height reduced by ~40%
- [ ] No accidental state modifications from mis-clicks on main page
- [ ] All editing functionality preserved in panes
- [ ] StartBuff enhancement buttons work on main page
- [ ] Comprehensive gift filter/search works in both viewer (selected) and pane (all)
- [ ] Mobile responsive (panes work on small screens)
- [ ] Consistent visual pattern across all restructured sections
