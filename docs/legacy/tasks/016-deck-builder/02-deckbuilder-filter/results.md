# Results — Deck Builder Filter Bar Expansion

## What Was Done

### Core spec (all 5 phases complete)
- Extended `DeckFilterState` in the Zustand store with 8 new fields (attributes, atk, def, rank, egoType, seasons, unitKeywords, battleKeywords)
- Created `matchesDeckFilter` predicate with mode-aware field gating + 37 unit tests
- Extended `calculateActiveFilterCount` to cover the new fields
- Shrunk `EntityToggle` in place (dropped `h-14`, `p-2`, `flex-1`)
- Created `DeckFilterBar` component + 5 integration tests covering identity/ego mode, reset, mode-switch preservation, mobile chevron
- Wired into `DeckBuilderContent`: replaced the old toggle+sinner+keyword+search block and the inline filter predicates

### Post-ship UI polish (added during review)
- Granular `<Suspense>` boundary inside `DeckBuilderPane` so initial data load shows a local skeleton, not the planner page skeleton
- Border-wrapped icon filter categories for visual grouping; dropdowns constrained to bounded widths via `className` prop passthrough on three dropdown components
- Single-block render (desktop XOR mobile) via `useIsBreakpoint('min', 1024)` to halve the filter bar's mount cost
- `useIsBreakpoint` made synchronous via `useState` initializer to eliminate the first-frame layout flash
- `useSearchMappings` swapped to `useSearchMappingsDeferred` to match IdentityList's non-suspending pattern

### Performance optimizations (added during post-render profiling)
- Progressive rendering state (`deckVisibleCount`) moved from local `useState` into Zustand; only grids subscribe via atomic selector, so rAF ticks no longer cascade re-renders to the filter bar, sinner summary, or action bar
- Extracted `IdentityGrid` and `EgoGrid` components — each owns its card-map rendering and subscribes independently to the progressive counter
- Added `hasWarmedInactive` latch so the inactive tab renders empty initially, then mounts after first paint via `requestIdleCallback` (falling back to `setTimeout(100ms)`), persisting for the session
- Reset progressive count on `DeckBuilderContent` unmount so each dialog reopen starts at `BATCH_SIZE` (not whatever the previous session grew to)
- `CompactIdentityRow`, `StatusViewer`, `DeckBuilderActionBar` wrapped in `memo` with custom comparators that exclude callback identities (pattern matches `TierLevelSelector.tsx:220`)
- `SearchableMultiSelect` sort gated on `open` state so closed popovers skip `new Intl.Collator(...)` construction and the `Array.prototype.sort` pass

## Files Changed

### Created
- `frontend/src/components/deckBuilder/DeckFilterBar.tsx` — desktop single-row + mobile expandable card
- `frontend/src/components/deckBuilder/IdentityGrid.tsx` — identity card grid with atomic progressive subscription
- `frontend/src/components/deckBuilder/EgoGrid.tsx` — EGO card grid, same pattern
- `frontend/src/components/deckBuilder/__tests__/DeckFilterBar.test.tsx` — 5 tests
- `frontend/src/lib/deckFilter.ts` — `matchesDeckFilter(item, state, mode, searchMappings)` predicate
- `frontend/src/lib/__tests__/deckFilter.test.ts` — 37 tests
- `docs/tasks/016-deck-builder/02-deckbuilder-filter/requirements.md`
- `docs/tasks/016-deck-builder/02-deckbuilder-filter/plan.md`
- `docs/tasks/016-deck-builder/02-deckbuilder-filter/status.json`

### Modified
- `frontend/src/types/DeckTypes.ts` — extended `DeckFilterState` with 8 new `Set<T>` fields
- `frontend/src/stores/usePlannerEditorStore.tsx` — factory + actions + `deckVisibleCount`/`setDeckVisibleCount`, new `usePlannerEditorStoreApiSafe` export
- `frontend/src/hooks/use-is-breakpoint.ts` — synchronous initial value via `useState(() => getInitialMatches(...))`
- `frontend/src/components/common/SearchableMultiSelect.tsx` — sort gated on `open`
- `frontend/src/components/deckBuilder/EntityToggle.tsx` — dropped `h-14`/`p-2`/`flex-1` in place
- `frontend/src/components/deckBuilder/DeckBuilderContent.tsx` — 322 lines diff; store-driven progressive rendering, grid extraction, deferred search mappings, warmup latch, memo'd children wired in
- `frontend/src/components/deckBuilder/DeckBuilderPane.tsx` — local Suspense boundary
- `frontend/src/components/deckBuilder/StatusViewer.tsx` — custom memo comparator
- `frontend/src/components/deckBuilder/DeckBuilderActionBar.tsx` — memo with comparator excluding callbacks
- `frontend/src/components/deckBuilder/CompactIdentityRow.tsx` — memo with comparator excluding `onToggleDeploy`
- `frontend/src/components/filter/SeasonDropdown.tsx` — `className` passthrough
- `frontend/src/components/filter/UnitKeywordDropdown.tsx` — `className` passthrough
- `frontend/src/components/filter/BattleKeywordDropdown.tsx` — `className` passthrough
- `frontend/src/lib/__tests__/filterUtils.test.ts` — coverage for new filter sets

## Verification

- **Typecheck**: `yarn --cwd frontend typecheck` → EXIT=0
- **Tests**: `yarn --cwd frontend test --run` → 5676 passed, 1 skipped (99 test files)
- **Build**: `yarn --cwd frontend build` → EXIT=0
- **Manual**: user profiled multiple iterations via React DevTools Profiler; each round of optimization dropped the targeted component out of commit hotspots

## Issues & Resolutions

- **Initial render shows stale filter layout on desktop**: `useIsBreakpoint`'s initial `undefined` state evaluated as `false`, causing mobile layout to flash on first paint → made the hook synchronous via `useState(() => getInitialMatches(...))` checking `window.matchMedia` immediately
- **Dialog reopen renders all cards at once**: `deckVisibleCount` in the store persisted across pane close/open → reset on component unmount (cleanup effect) so next mount starts from `BATCH_SIZE`
- **Card hover causes `CompactIdentityRow` re-render**: "Parent re-rendered" shown in Profiler even though no obvious ancestor changed state; React Compiler's element-cache apparently invalidated for non-obvious reasons → custom memo comparator excluding `onToggleDeploy` (stable behavior, churning identity) blocked the cascade at the memo boundary
- **Progressive ticks cascade through filter bar / sinner row**: progressive `useState(visibleCount)` in `DeckBuilderContent` re-rendered the whole subtree each rAF tick → moved state to Zustand, only grids subscribe
- **`FilterSection.defaultExpanded` is a no-op**: the prop is declared but never read in the component body; I initially believed IdentityPage's dropdowns were deferred behind collapsed sections — they aren't. This invalidated an early optimization hypothesis and forced us to look elsewhere for dropdown cost
- **Dropdown 15ms cost**: traced to `Intl.Collator` construction + `Array.sort` happening on every render regardless of popover state → gated by `open` in `useMemo`
- **Test selectors broken after conditional mount**: `DeckFilterBar.test.tsx` used `.querySelector('.hidden.lg\\:flex')` and `.lg\\:hidden`; after the conditional render change, neither class existed → mocked `useIsBreakpoint` with `vi.fn(() => true)` and used `screen.getBy*` directly since only one block mounts

## Learnings

### Patterns that worked well
- **Pre-commit describe-plan-first rhythm**: for every substantive change (card extraction, useIsBreakpoint hoist, memo additions) the user explicitly approved a plan before code wrote. Prevented multiple reverts.
- **Profiler-driven optimization loop**: flame graph → identify hotspot → propose fix → apply → measure again. Three rounds of this compounded well; each round was a single-digit-line change.
- **Atomic Zustand selectors for high-frequency state**: moving `visibleCount` to the store and subscribing only from grids was the single biggest perf win. The pattern generalizes — any per-frame-ish state belongs in a store slice with atomic subscribers.
- **Custom memo comparators that exclude callback identities**: project-wide precedent at `TierLevelSelector.tsx:220` made this easy to justify and pattern-match.
- **LSP diagnostics as noise filter**: running `tsc --noEmit` after each multi-edit was the reliable truth source; LSP diagnostics repeatedly lagged the file system after alias or module resolution changes.

### Unexpected difficulties
- **React Compiler's "auto-memoization" doesn't prevent parent-cascade re-renders** in all cases. Manual `memo` + custom comparator was still needed for three components. The Compiler's cache keys include every input referenced in the JSX element; when inputs churn for non-obvious reasons, the cache fails silently.
- **Test file couplings**: changing the filter bar's layout (single block → conditional mount) broke 5 tests that used CSS-class selectors. Selector-by-role queries would have survived the refactor; CSS-class selectors were brittle.
- **Stale HMR on symbol-renaming edits**: Vite's HMR failed to reconcile when imports renamed inside a file (e.g., `useSearchMappings` → `useSearchMappingsDeferred`). Only hard reload fixed it. Worth knowing as a red herring during live perf profiling.

### What to do differently next time
- **Start with Zustand for any rAF/progressive state, not `useState`**: would have avoided the cascade refactor entirely.
- **Add atomic viewport detection to the shared hook early**: `useIsBreakpoint` flash-free initial value benefits every caller. Should have been part of the initial hook design, not a per-use fix.
- **Run tsc after every edit, not just at phase boundaries**: would have caught the missing `MAX_LEVEL` import sooner and avoided the brief broken-state between edits.

## Spec Divergence

### What Changed

- **`matchesDeckFilter` signature** — spec defined `(item, state, mode)`; actual signature is `(item, state, mode, searchMappings)`. The 4th parameter was necessary because the existing `DeckBuilderContent` search used `useSearchMappings()` reverse lookups (keyword/unit-keyword value maps) that can't be derived from `item` or `state` alone. Keeping the predicate pure required exposing the mappings as a parameter rather than hiding a hook call inside.
- **Layout "first-pass one row with flex-wrap"** — spec said to render all filters in one row with flex-wrap. Actual: we kept a single-row desktop layout but added a visual division — each icon filter category now sits inside a bordered box (`rounded-md border border-border/60 p-1`) for grouping. User requested this after the first render looked undifferentiated.
- **Dropdown widths** — spec mentioned bounded widths implicitly; actual implementation added `className` passthrough to `SeasonDropdown`, `UnitKeywordDropdown`, `BattleKeywordDropdown` threaded to `SearchableMultiSelect`. Three files touched that the spec didn't mention.
- **Search bar styling** — spec didn't specify; inline `className="h-10 p-1 w-56"` was applied to align height with dropdown triggers.

### What Was Added (Not in Spec)

- **Granular `<Suspense>` boundary inside `DeckBuilderPane`** — original pane opened with the planner page's top-level Suspense catching the first data load. User reported whole-page skeleton flash; fixed by local boundary.
- **Conditional render of desktop OR mobile block via `useIsBreakpoint`** — spec planned the mobile pattern but assumed both trees would mount via Tailwind `hidden`/`lg:hidden` classes. Profiling showed mounting both doubled the filter bar render cost; switched to conditional render.
- **`useIsBreakpoint` synchronous initial value** — added `useState(() => getInitialMatches(...))` so first render sees correct viewport. Benefits other callers too (`DetailPageLayout`, `use-image-upload`).
- **Deferred hooks** — swapped `useSearchMappings` → `useSearchMappingsDeferred` to match `IdentityList`'s pattern. Spec didn't anticipate the cascade cost.
- **Progressive state in Zustand** — spec kept `visibleCount` as local state. Profiling showed rAF ticks cascading through the whole subtree; moved to store with atomic subscription. This required creating `usePlannerEditorStoreApiSafe` + extracting `IdentityGrid`/`EgoGrid`.
- **`hasWarmedInactive` latch for inactive-tab card deferral** — spec's original intent was a full filter-predicate-driven visibility toggle; user requested inactive-tab deferral after perceiving slow open. Latch state pattern was the minimal way to implement it without destabilizing tab-switch UX.
- **Memo + custom comparator on `CompactIdentityRow` / `StatusViewer` / `DeckBuilderActionBar`** — spec didn't mention memoization. Needed after Profiler showed these components re-rendering on hover despite React Compiler.
- **`SearchableMultiSelect` open-gated sort** — out of task scope; applied because profile showed the closed-state cost dominating the filter bar's render time.

### What Was Dropped

- None — all spec acceptance criteria were met. The spec's `## Ambiguities > mobile active-filter count (mode-aware?)` was resolved by default (count every non-empty set regardless of mode); no follow-up needed.

### Wrong Assumptions

- **"`FilterSection.defaultExpanded` renders content conditionally"** — spec and brainstorm both assumed IdentityPage's sidebar deferred dropdown mounting via `defaultExpanded={false}`. The prop is declared in `FilterSection.tsx` but never consumed; children always render. This invalidated an early performance hypothesis and had to be corrected mid-session.
- **"Rendering both desktop and mobile blocks is free via Tailwind `hidden`"** — CSS hiding doesn't prevent React from mounting and reconciling both trees. Discovered via Profiler; fixed with `useIsBreakpoint` conditional render.
- **"React Compiler auto-memoization will handle parent-cascade re-renders"** — partially true. Compiler caches JSX elements well in simple cases but can silently invalidate caches for deep trees with many hook subscriptions. Manual `memo` + custom comparator was still required for three components.
- **"Inactive tab rendering is cheap because of CSS-hidden"** — false. React still mounts and reconciles every hidden card. Inactive tab must actually render `[]` (empty array) to be cheap.

### Prompting Retrospective

What the user could have asked during `/brainstorm` or `/spec` to surface these divergences earlier:

- **Predicate purity constraint**: "Does `matchesDeckFilter` need access to any hook-provided data (i18n mappings, translation lookups) that can't be derived from item or state alone?" — would have surfaced the `searchMappings` parameter pre-spec.
- **Dual-tree mount cost**: "When mobile and desktop layouts use Tailwind `hidden` to toggle visibility, do both React subtrees actually mount, or is it CSS-only?" — would have surfaced the conditional-render requirement during brainstorm instead of after profiling.
- **React Compiler coverage**: "Does React Compiler memoization reliably prevent parent-cascade re-renders for components that consume store subscriptions, or are there known bailout patterns in this codebase?" — would have prompted pre-emptive memo planning instead of reactive fixes.
- **Progressive rendering cascade**: "Where should `visibleCount` live to prevent rAF ticks from re-rendering sibling components?" — would have put progressive state in the store from day one.
- **Pane-open profile**: "What components render on dialog open, and which of them should NOT? List everything that shouldn't re-render and check memoization." — would have pre-identified `CompactIdentityRow`, `StatusViewer`, `DeckBuilderActionBar` as memoization targets.
- **FilterSection honesty**: "Does `FilterSection.defaultExpanded` actually toggle children visibility, or is it a visual label only?" — a 10-second source check would have saved an incorrect hypothesis.

### Spec Process Takeaway

**This spec systematically missed performance-adjacent integration constraints.** The functional behavior was well-scoped and implemented cleanly — but every significant post-build addition was a performance concern (Suspense granularity, dual-tree mounting, memo cascades, progressive-state cascade, closed-popover sort cost). Next time, add a "Performance Constraints & Known Cascade Paths" section to `/spec` for any UI feature that ships inside a dialog or dense layout; surface the memo plan alongside the component plan.
