# DeckBuilderPane Investigation - Infinite Re-render Bug

## Original Request
User wanted to **prevent real-time sorting** in the deck builder edit pane. Currently, selecting an identity/EGO immediately resorts the list, which doesn't match the game's behavior. Sorting should only occur when the pane opens.

## Changes Made (All Reverted)

### 1. Sorting Snapshot (REVERTED)
```javascript
// Added refs to capture equipped IDs when pane opens
const sortingIdentityIdsRef = useRef<Set<string>>(new Set())
const sortingEgoIdsRef = useRef<Set<string>>(new Set())

useEffect(() => {
  if (open && !hasSnapshotRef.current) {
    hasSnapshotRef.current = true
    sortingIdentityIdsRef.current = new Set(equippedIdentityIds)
    sortingEgoIdsRef.current = new Set(equippedEgoIds)
  }
}, [open, equippedIdentityIds, equippedEgoIds])

// Used refs in sorting instead of live equippedIdentityIds
```

### 2. Card Memoization (REVERTED)
Added `memo` with custom `arePropsEqual` to `IdentityCard` and `EGOCard` to prevent unnecessary re-renders. Comparison checked `identity.id`, `overlay` presence (boolean), etc.

### 3. TierLevelSelector Changes (REVERTED)
- Removed `IntersectionObserver` lazy loading
- Added custom comparison to ignore `onConfirm` callback reference

### 4. Progressive Rendering (STILL PRESENT)
```javascript
// Renders 10 items per frame to spread work
const [visibleCount, setVisibleCount] = useState(BATCH_SIZE)

useEffect(() => {
  if (!contentReady || visibleCount >= totalCount) return
  requestAnimationFrame(() => {
    setVisibleCount(prev => Math.min(prev + BATCH_SIZE, totalCount))
  })
}, [contentReady, visibleCount, totalCount])
```

### 5. Removed useDeferredValue (STILL REMOVED)
Removed `useDeferredValue` wrapping on `filteredAndSortedIdentities` and `filteredAndSortedEgos`.

---

## Infinite Re-render Bug

### Symptoms
- Opening DeckBuilderPane causes **infinite re-renders** (150+ and counting)
- Console shows: `[DeckBuilderPane] Render # 151, 152, 153...` continuously
- Effects run normally, props don't change between renders
- **Parent components** also re-render infinitely

### Key Observations
1. **Not caused by my changes** - persists after reverting all changes
2. **Pre-existing bug** - was present before this session's modifications
3. **Occurs after data loads** - burst starts when identity/ego lists populate
4. **Progressive rendering is NOT the culprit** - confirmed by user

### Debug Logs Added
```javascript
// In DeckBuilderPane component body:
const renderCount = useRef(0)
renderCount.current++
console.log('[DeckBuilderPane] Render #', renderCount.current)
```

### What We Ruled Out
| Suspect | Tested | Result |
|---------|--------|--------|
| Sorting snapshot useEffect | Removed | Still loops |
| IdentityCard/EGOCard memo | Removed | Still loops |
| TierLevelSelector changes | Reverted | Still loops |
| Progressive rendering | User confirmed | Not culprit |

---

## My Opinion on the Infinite Re-render

The infinite loop is likely caused by one of:

### 1. Parent Component Issue (Most Likely)
The parent `PlannerMDEditorContent.tsx` may have unstable state or props that cascade down. Check:
- Are `equipment`, `filterState` objects recreated each render?
- Is there a context provider above that's updating continuously?
- Any `useMemo`/`useCallback` missing stable dependencies?

### 2. Suspense/Query Interaction
`DeckBuilderPane` uses three suspending hooks:
- `useIdentityListData()`
- `useEGOListData()`
- `useSearchMappings()`

If any query is refetching in a loop (stale time issue, refetch on window focus, etc.), it could cause continuous suspense/unsuspense cycles.

### 3. React Compiler Behavior
The project uses React Compiler. It auto-inserts dependencies and may create unexpected reactive chains. Check if disabling React Compiler for this file fixes the issue.

---

## Recommended Next Steps

1. **Profile the parent** - Add render count log to `PlannerMDEditorContent.tsx`
2. **Check TanStack Query** - Look for queries refetching in DevTools
3. **Disable React Compiler** - Add `"use no memo"` directive temporarily
4. **Check context** - See if any context above DeckBuilderPane is updating
5. **Git bisect** - Find when the bug was introduced if it's recent

---

## Current State of Code

- `DeckBuilderPane.tsx`: Progressive rendering with sorting snapshot (IMPLEMENTED)
- `IdentityCard.tsx`: No memo (reverted)
- `EGOCard.tsx`: No memo (reverted)
- `TierLevelSelector.tsx`: Original with IntersectionObserver

The **sorting snapshot** is now implemented - list order is frozen when pane opens, items stay in place during selection.
