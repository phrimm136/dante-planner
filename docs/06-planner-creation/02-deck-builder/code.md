# Deck Builder - Code Review

## Implementation Summary

The Deck Builder feature allows users to build and configure their party composition for Mirror Dungeon runs. Users can equip identities and EGOs per sinner, manage deployment order (7 active + 5 backup), and view aggregated affinity/keyword statistics.

## Architecture Overview

### Component Hierarchy
```
PlannerMDNewPage
└── DeckBuilder
    ├── SinnerGrid
    │   └── SinnerDeckCard (×12)
    ├── StatusViewer
    ├── EntityToggle
    ├── SinnerFilter
    ├── KeywordFilter
    ├── SearchBar
    └── TierLevelSelector (wraps IdentityCard/EGOCard in list)
```

### State Management
- Equipment state and deployment order managed via React useState in DeckBuilder
- Equipment keyed by sinner name (PascalCase): `Record<string, SinnerEquipment>`
- Deployment order stored as array of sinner indices: `number[]`
- Filter states (sinners, keywords, search) managed locally

## New Files Created

### Types (`frontend/src/types/DeckTypes.ts`)
```typescript
// Tier types
type UptieTier = 1 | 2 | 3 | 4
type ThreadspinTier = 1 | 2 | 3 | 4

// Equipment configuration
interface EquippedIdentity { id: string; uptie: UptieTier; level: number }
interface EquippedEGO { id: string; threadspin: ThreadspinTier }
type EGOSlots = { [K in EGORank]?: EquippedEGO }
interface SinnerEquipment { identity: EquippedIdentity; egos: EGOSlots }

// Complete state
interface DeckState {
  equipment: Record<string, SinnerEquipment>
  deploymentOrder: number[]
  deploymentConfig: DeploymentConfig
}

// EA calculation types
interface AffinityCount { affinity: Affinity; generated: number; consumed: number }
interface KeywordCount { keyword: string; count: number }
```

### DeckBuilder (`frontend/src/components/deckBuilder/DeckBuilder.tsx`)
Main container component managing all deck state:
- Initializes default equipment (ID ending with 01 for each sinner)
- Handles identity/EGO equip via `handleEquipIdentity`/`handleEquipEgo`
- Manages deployment order toggle via `handleToggleDeploy`
- Renders SinnerGrid, StatusViewer, filters, and entity lists
- Uses `startTransition` for non-blocking state updates
- Uses `useRef` pattern to keep callback references stable

### SinnerGrid (`frontend/src/components/deckBuilder/SinnerGrid.tsx`)
Grid layout displaying 12 sinner cards:
- 2-6 column responsive grid
- Memoized identity and deployment order lookups
- Passes equipment data to SinnerDeckCard

### SinnerDeckCard (`frontend/src/components/deckBuilder/SinnerDeckCard.tsx`)
Individual sinner card displaying:
- Identity image with uptie frame
- Skill 1/2/3 affinity icons (colored backgrounds with attack type icons)
- 5 EGO rank slots (ZAYIN through ALEPH) with affinity-colored backgrounds
- Deployment order badge (#1-#7 deployed, #8-#12 backup)
- Click-to-toggle deployment order

### StatusViewer (`frontend/src/components/deckBuilder/StatusViewer.tsx`)
Displays aggregated statistics for deployed sinners:
- Affinity EA: Generated (skill affinities with weights 3/2/1) | Consumed (EGO costs)
- Keyword EA: Count of identities per status effect keyword
- Uses spec data hooks for affinity/keyword lookup
- Shows all STATUS_EFFECTS in order, even with 0 count (opacity-40)

### TierLevelSelector (`frontend/src/components/deckBuilder/TierLevelSelector.tsx`)
Hover popup for tier/level configuration:
- Uptie selector (1-4) for identities with level input
- Threadspin selector (1-4) for EGOs
- Lazy loading via IntersectionObserver (100px rootMargin)
- Custom `arePropsEqual` to prevent unnecessary re-renders

### EntityToggle (`frontend/src/components/deckBuilder/EntityToggle.tsx`)
Toggle switch between identity and ego list views:
- Two-button toggle with active state styling
- Memoized to prevent re-renders

## Modified Files

### Constants (`frontend/src/lib/constants.ts`)
- Renamed `BASE_LEVEL` → `MAX_LEVEL` (55)
- Renamed `SINS` → `AFFINITIES` (CRIMSON, SCARLET, AMBER, SHAMROCK, AZURE, INDIGO, VIOLET)
- Added `ATK_TYPES` (SLASH, PENETRATE, HIT)
- Added `DEFAULT_DEPLOYMENT_MAX` (7)

### Utils (`frontend/src/lib/utils.ts`)
- Added `getSinnerFromId(id)`: Extracts sinner name from 5-digit entity ID

### Asset Paths (`frontend/src/lib/assetPaths.ts`)
- Added `getIdentityImageFallbackPath()` for normal_info.webp fallback
- Updated `getUptieFramePath(star, uptie)` to accept uptie parameter
- Added `getAffinityIconPath()` with AFFINITY_TO_SIN_NAME mapping
- Fixed EGO paths to use lowercase `ego` instead of `EGO`

### Identity/EGO Cards
- Added `isSelected` prop and `onSelect` callback
- Added `memo()` wrapper for optimization
- Added `loading="lazy"` to images
- Added image fallback handling for identities

### Identity/EGO Lists
- Added `equippedIds` prop for selection display
- Added `onSelectIdentity`/`onSelectEgo` callbacks
- Changed from `identity.sinner` to `getSinnerFromId(identity.id)`
- Changed from `identity.keywords` to `identity.skillKeywordList`
- Sort equipped items to top of list

### Schemas
- Added `IdentitySpecListItemSchema` and `EGOSpecListItemSchema` for list data
- Added `AffinitySchema` for CRIMSON/SCARLET/etc validation
- Updated spec list schemas to use list-specific schemas

### Filter Components Consolidation
- Created shared `SinnerFilter` and `KeywordFilter` in `common/`
- Deleted duplicate identity/ego-specific filter components
- Added `memo()` wrappers for optimization

### useEntityListData Hook
- Changed EGO paths from `EGOSpecList` → `egoSpecList` (lowercase)
- Added field mapping for EGO: `egoType` → `rank`

### PlannerMDNewPage
- Integrated DeckBuilder component into planner creation flow

## Performance Optimizations

### Implemented
- `memo()` on filter components, cards, and grid components
- `useCallback` with stable dependencies for handlers passed to many components
- `useRef` pattern to avoid recreating callbacks on every render
- `useMemo` for computed data (identity maps, deployment order maps, filtered lists)
- `startTransition` for non-blocking state updates
- `IntersectionObserver` for lazy loading TierLevelSelector

### Removed (Unnecessary)
- Overly complex `arePropsEqual` with string join comparisons
- `useCallback` for handlers not passed to memoized children
- Extra memoization layers like `equippedIdentityIdsKey`
- Pre-computed intermediate structures like `sinnerEgoAffinities`

## Data Flow

1. **Loading**: `useEntityListData` fetches spec + i18n data via TanStack Query
2. **Equipment**: Stored per sinner with identity (id, uptie, level) and EGOs by rank
3. **Selection**: Clicking card in list opens TierLevelSelector, then calls `handleEquipIdentity`/`handleEquipEgo`
4. **Deployment**: Clicking SinnerDeckCard toggles deployment order via `handleToggleDeploy`
5. **Statistics**: StatusViewer computes EA from spec data for deployed sinners only

## ID Format

Entity IDs follow 5-digit format: `T SS II`
- T: Type (1=identity, 2=ego)
- SS: Sinner index (01-12)
- II: Entity index within sinner

Examples:
- `10101` → Identity, YiSang (01), first identity (01)
- `20305` → EGO, DonQuixote (03), fifth EGO (05)

Default equipment uses `{type}{sinner}01` pattern for each sinner.
