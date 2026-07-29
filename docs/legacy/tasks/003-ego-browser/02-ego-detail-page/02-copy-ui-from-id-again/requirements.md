# Task: Apply Identity Detail Page Patterns to EGO Detail Page

## Description

Refactor the EGO detail page to match the Identity detail page architecture, implementing granular Suspense boundaries that prevent full page re-suspension during language changes. The page should display EGO information (header, sin costs, resistances, skills, passives) with user-controlled threadspin level selection.

**Threadspin = Uptie**: EGO uses "threadspin" terminology but the concept is identical to Identity's "uptie" system (levels 1-4, progressive unlocking, **tier button click selection**).

**Key Requirements**:

- **Language change behavior**: When user switches language, only text elements (names, descriptions) should show loading skeletons. The layout, images, stats panels, and state (threadspin level, selected skill type) must remain visible and stable.

- **Threadspin control**: User selects threadspin level (1-4) by **clicking tier buttons** (same as Identity uptie buttons). Passive availability changes based on threadspin level following inheritance rules (empty arrays inherit from previous level). Skills merge data up to selected threadspin.

- **Skill type tabs**: Two skill types (Awakening, Erosion). Erosion tab should be visible but disabled (dimmed with `opacity-50`, non-clickable) when the EGO has no erosion skills. Use SkillTabButton component with `isLocked` prop for this.

- **Passive display**: Single "Passives" section (no battle/support split like Identity). Show effective passives at current threadspin plus locked preview passives from higher levels. EGO passives are simpler than Identity - no affinity condition icons, no enhanced variant deduplication.

- **Mobile support**: Tabs for Skills and Passives (no Sanity tab). Threadspin selector appears above tabs on mobile view.

- **Component-based rendering**: Replace inline JSX with dedicated I18n wrapper components that fetch i18n data and suspend independently.

## Research

- Study `IdentityDetailPage.tsx` structure (lines 34-411):
  - Shell component pattern with spec-only data
  - State management (uptie, level, activeSkillSlot)
  - **`getEffectivePassives()` logic**: Walk backwards from current tier until finding non-empty array
  - **`getLockedPassives()` logic**: Preview passives from higher tiers with lock indicator
  - **`getPassiveInfo()` function**: Extract type/variant from passive ID (EGO doesn't need this - simpler IDs)
  - How leftColumn/rightColumn/mobileTabsContent are structured

- Study Identity I18n components:
  - `IdentityHeaderI18n.tsx`: How it wraps base component with i18n fetch
  - `SkillI18n.tsx`: Granular Suspense per skill card
  - `PassiveI18n.tsx`: Two-tier Suspense (name + description separate), `isLocked` prop handling

- Analyze current EGO implementation gaps:
  - `useEGODetailData()` fetches spec + i18n together → causes full re-suspension
  - Threadspin hardcoded to 3 (should be state-controlled)
  - Skills/passives rendered inline without Suspense wrappers
  - No passive locking logic (should show locked preview like Identity)
  - No mobile tabs support

- Check EGO data structure:
  - Passive list array length (4 elements for threadspin 1-4)
  - **Passive inheritance**: Empty arrays mean "inherit from previous tier" (same as Identity uptie)
  - Passive IDs are `string` type (not `number` like Identity)
  - No conditions field in passive data (simpler than Identity)
  - No enhanced variant types (no type=0/1/2 distinction)
  - Erosion skills can be empty array

- Verify DetailEntitySelector behavior:
  - Already supports `entityType="ego"` with threadspin button clicks
  - Tier buttons (not slider) for threadspin 1-4
  - No level selector for EGO (only Identity has level)

- Review `MobileDetailTabs.tsx`:
  - How it handles 2-tab vs 3-tab mode (via optional `sanityContent` prop)
  - Whether it supports disabled tabs (currently no - needs extension)
  - TabsTrigger from shadcn/ui has built-in `disabled` prop with opacity-50 styling

## Scope

**Files to READ for context**:

- `/home/user/github/LimbusPlanner/frontend/src/routes/IdentityDetailPage.tsx` - Pattern template
- `/home/user/github/LimbusPlanner/frontend/src/components/identity/IdentityHeaderI18n.tsx` - Header wrapper pattern
- `/home/user/github/LimbusPlanner/frontend/src/components/identity/SkillI18n.tsx` - Skill wrapper pattern
- `/home/user/github/LimbusPlanner/frontend/src/components/identity/PassiveI18n.tsx` - Passive wrapper pattern (including `isLocked` handling)
- `/home/user/github/LimbusPlanner/frontend/src/hooks/useIdentityDetailData.ts` - Hook split pattern (spec vs i18n)
- `/home/user/github/LimbusPlanner/frontend/src/components/identity/SkillTabButton.tsx` - Tab button with disabled state
- `/home/user/github/LimbusPlanner/frontend/src/components/common/MobileDetailTabs.tsx` - Mobile tabs component
- `/home/user/github/LimbusPlanner/frontend/src/components/common/DetailPageLayout.tsx` - Layout wrapper
- `/home/user/github/LimbusPlanner/frontend/src/components/common/DetailEntitySelector.tsx` - Tier button selector
- `/home/user/github/LimbusPlanner/frontend/src/lib/constants.ts` - Check for MAX_ENTITY_TIER.ego, MIN_ENTITY_TIER.ego
- `/home/user/github/LimbusPlanner/static/data/ego/20101.json` - Sample EGO data structure (passive inheritance)
- `/home/user/github/LimbusPlanner/static/i18n/EN/ego/20101.json` - Sample EGO i18n structure

## Target Code Area

**Files to CREATE**:

- `/home/user/github/LimbusPlanner/frontend/src/components/ego/EGOHeaderI18n.tsx` - Header with i18n name suspension
- `/home/user/github/LimbusPlanner/frontend/src/components/ego/EGOSkillCardI18n.tsx` - Skills section with granular Suspense
- `/home/user/github/LimbusPlanner/frontend/src/components/ego/EGOPassiveCardI18n.tsx` - Passive card with i18n suspension and `isLocked` prop

**Files to MODIFY**:

- `/home/user/github/LimbusPlanner/frontend/src/hooks/useEGODetailData.ts` - Add `useEGODetailSpec()` and `useEGODetailI18n()` hooks
- `/home/user/github/LimbusPlanner/frontend/src/components/common/MobileDetailTabs.tsx` - Add disabled tabs support, rename sanityContent → thirdTabContent
- `/home/user/github/LimbusPlanner/frontend/src/routes/IdentityDetailPage.tsx` - Update MobileDetailTabs prop names
- `/home/user/github/LimbusPlanner/frontend/src/routes/EGODetailPage.tsx` - Complete refactor to match Identity pattern (add passive locking logic)

## System Context (Senior Thinking)

**Feature domain**: EGO Browser (from architecture-map Quick Reference)

**Core files in this domain**:
- Routes: `EGOPage.tsx`, `EGODetailPage.tsx`
- Hooks: `useEGOListData.ts`, `useEGODetailData.ts`
- Components: `components/ego/*`
- Search: `useSearchMappings.ts`

**Cross-cutting concerns touched**:
- **Validation**: `schemas/EGOSchemas.ts` (Zod validation for spec + i18n data)
- **i18n**: `static/i18n/{lang}/ego/*.json`, `lib/i18n.ts`
- **Theme**: Uses shadcn/ui components (inherits theme)
- **Detail Layout**: Shared `DetailPageLayout`, `DetailEntitySelector`, `MobileDetailTabs`
- **Constants**: `lib/constants.ts` (MAX_ENTITY_TIER, MIN_ENTITY_TIER, DETAIL_PAGE ratios)
- **Asset Paths**: `lib/assetPaths.ts` (getEGOIconPath, getAffinityIconPath, getEGOTierIconPath)

**Pattern alignment**: Identity/EGO/Gift browser pattern (all three follow same architecture)

## Impact Analysis

**Files being modified with impact level**:

| File | Impact Level | Reason |
|------|-------------|---------|
| `useEGODetailData.ts` | Medium | Hook refactor - adds new exports, deprecates old one |
| `EGODetailPage.tsx` | High (isolated) | Main page refactor but page-isolated |
| `MobileDetailTabs.tsx` | Medium | Shared component used by Identity + EGO detail pages |
| `IdentityDetailPage.tsx` | Low | Only prop rename for MobileDetailTabs |
| `EGOHeaderI18n.tsx` (new) | Low | New component, no dependencies |
| `EGOSkillCardI18n.tsx` (new) | Low | New component, wraps existing EGOSkillCard |
| `EGOPassiveCardI18n.tsx` (new) | Low | New component, no external dependencies |

**What depends on these files**:

- `useEGODetailData.ts`: Currently used by `EGODetailPage.tsx` only
  - Deprecated function kept for backward compatibility
  - New split hooks don't affect existing code

- `MobileDetailTabs.tsx`: Used by `IdentityDetailPage.tsx`, will be used by `EGODetailPage.tsx`
  - Breaking change: `sanityContent` → `thirdTabContent`
  - Must update IdentityDetailPage before deploying

- `EGODetailPage.tsx`: Standalone route, no other files depend on it
  - Page-isolated change, no ripple effects

**Potential ripple effects**:

- **Hook refactor**: Existing `useEGODetailData()` marked deprecated but still works → no breaking change
- **MobileDetailTabs**: Prop rename is breaking → must update IdentityDetailPage in same commit
- **Query keys**: New hooks use different query keys (`['ego', id]` vs `['ego', id, 'i18n', language]`) → no cache collision

**High-impact files to watch**:

- `lib/constants.ts`: Already contains `MAX_ENTITY_TIER.ego = 4`, `MIN_ENTITY_TIER.ego = 1` → no changes needed
- `DetailPageLayout.tsx`: Shared layout component → no changes needed (reused as-is)
- `DetailEntitySelector.tsx`: Already supports `entityType="ego"` → no changes needed

## Risk Assessment

**Edge cases**:

- **Empty erosion skills**: EGO with `"erosion": []` should show disabled tab, not crash
  - Handle via `hasErosion` boolean check before rendering
  - SkillTabButton with `isLocked={!hasErosion}` for dimmed styling

- **Passive inheritance**: EGO passives use same inheritance as Identity (empty = inherit from previous)
  - Example: passiveList = `[[], ["2010111"], [], []]` means passive unlocks at threadspin 2, inherited by 3-4
  - Must implement `getEffectivePassives()` that walks backwards until finding non-empty array
  - Must implement `getLockedPassives()` for higher-tier passive preview

- **Passive locking display**: Unlike Identity, EGO doesn't have enhanced variant deduplication
  - Simpler logic: Just show all passives from higher tiers as locked (no variant filtering)
  - No `getPassiveInfo()` function needed (EGO IDs don't encode type/variant)

- **Threadspin state persistence**: State must survive language change
  - Risk: If shell component re-suspends, state resets
  - Mitigation: Use `useEGODetailSpec()` (no language key) in shell

- **Passive ID type mismatch**: EGO uses `string` IDs, Identity uses `number`
  - Components must accept `passiveId: string` for EGO
  - No type coercion needed (keep as string throughout)

**Performance concerns**:

- **Language change**: Only text elements should re-suspend
  - Verify with logging: `useEGODetailSpec()` should NOT log on language change
  - Layout, stats, state must remain visible

- **Skill data merging**: Merging threadspin data happens on every render
  - Already optimized in existing `EGOSkillCard.getMergedSkillData()`
  - No new performance cost

**Backward compatibility**:

- **Deprecated hook**: `useEGODetailData()` still works for any code not yet updated
- **MobileDetailTabs**: Prop rename breaks IdentityDetailPage
  - Fix: Update Identity in same commit/PR

**Security considerations**:

- Static data only (no user input)
- No new security surface area

**Confirmed requirements from user**:

- ✅ **Threadspin = Uptie**: Same concept, levels 1-4, **tier button click** selection
- ✅ **Passive locking**: Show locked previews from higher threadspins (like Identity)
- ✅ **Passive inheritance**: Empty arrays inherit from previous level
- ✅ **Erosion tab handling**: Disabled but visible (dimmed, non-clickable)
- ✅ **Passive section header**: Single "Passives" header (no battle/support split)

## Testing Guidelines

### Manual UI Testing

#### Threadspin Button Selection Test
1. Navigate to EGO detail page
2. **Verify**: Threadspin selector shows 4 tier buttons (1, 2, 3, 4)
3. **Verify**: Threadspin 4 is selected by default (highlighted)
4. Click threadspin 1 button
5. **Verify**: Button 1 becomes highlighted, others unhighlighted
6. **Verify**: Passives update to threadspin 1 state
7. **Verify**: Skills update with merged data for level 1 only
8. Click threadspin 2 button
9. **Verify**: Passive unlocks (moves from locked to effective)
10. Click threadspin 3, then 4
11. **Verify**: Each click updates button highlight and content

#### Language Change Stability Test
1. Navigate to any EGO detail page (e.g., `/ego/20101`)
2. Click threadspin 2 button
3. Note the selected skill type (Awakening/Erosion)
4. Open language selector in header
5. Switch from EN → KR
6. **Verify**: Only the EGO name shows skeleton during transition
7. **Verify**: Header image, rank badge, sin panels remain visible
8. **Verify**: Threadspin button 2 stays highlighted (does not reset to 4)
9. **Verify**: Selected skill type (Awakening/Erosion) persists
10. Switch to JP language
11. **Verify**: Same stability behavior
12. Switch back to EN
13. **Verify**: Threadspin state still at button 2

#### Passive Locking Display Test
1. Navigate to EGO detail page
2. Click threadspin 1 button
3. Scroll to Passives section
4. **Verify**: "Effective Passives" section shows no passives or is empty
5. **Verify**: "Locked Passives" section shows passive "2010111" with lock icon
6. **Verify**: Locked passive has `opacity-50` styling
7. **Verify**: Locked passive name and description are visible (not hidden)
8. Click threadspin 2 button
9. **Verify**: Passive moves from "Locked" to "Effective" section
10. **Verify**: Lock icon disappears, opacity returns to full
11. Click threadspin 3 button
12. **Verify**: Passive remains in "Effective" section (inherited)
13. Click threadspin 4 button
14. **Verify**: Passive still in "Effective" section (inherited)

#### Erosion Skills Test (EGO without erosion)
1. Navigate to EGO without erosion skills (e.g., most WAW/ALEPH)
2. **Verify**: Awakening tab is active by default
3. **Verify**: Erosion tab is visible but dimmed (opacity-50)
4. **Verify**: Erosion tab shows disabled styling
5. Attempt to click Erosion tab
6. **Verify**: Tab does not respond to click (pointer-events-none)
7. **Verify**: Awakening skills remain displayed
8. Hover over disabled Erosion tab
9. **Verify**: No hover effect (cursor-not-allowed)

#### Mobile Tabs Test
1. Resize browser to mobile width (<768px)
2. Navigate to EGO detail page
3. **Verify**: Left column content (header, sin panels) appears at top
4. **Verify**: Threadspin selector appears above tabs
5. **Verify**: Threadspin selector shows 4 tier buttons
6. **Verify**: Two tabs visible: "Skills" and "Passives"
7. **Verify**: Skills tab is active by default
8. Tap Passives tab
9. **Verify**: Tab switches, passives content displays
10. **Verify**: Both effective and locked passives show correctly
11. **Verify**: Threadspin selector remains visible and functional
12. Click threadspin 2 button
13. **Verify**: Passive sections update (locked → effective transition)
14. Tap Skills tab
15. **Verify**: Returns to skills view

#### Component Isolation Test (Granular Suspense)
1. Open browser DevTools Network tab
2. Navigate to EGO detail page (EN language)
3. Wait for full load
4. Clear network log
5. Switch language to KR
6. **Verify**: Only i18n JSON files are fetched (no spec data re-fetch)
7. Observe page during load
8. **Verify**: Header image stays visible, only name shows skeleton
9. **Verify**: Threadspin tier buttons stay visible and clickable
10. **Verify**: Skill attribute icons stay visible, only name/desc show skeletons
11. **Verify**: Passive lock icons stay visible, only text shows skeletons

### Automated Functional Verification

#### Data Fetching
- [ ] **Spec stability**: `useEGODetailSpec(id)` query key has no language component
- [ ] **I18n suspension**: `useEGODetailI18n(id)` query key includes `i18n.language`
- [ ] **No re-fetch on language change**: Spec data not re-requested when language switches
- [ ] **I18n fetch on language change**: I18n data fetched for new language

#### State Management
- [ ] **Threadspin persistence**: State survives language change (shell component doesn't re-suspend)
- [ ] **Skill type persistence**: Active skill tab (awaken/erosion) survives language change
- [ ] **Threadspin range**: Buttons allow 1-4, corresponds to Threadspin type
- [ ] **Default threadspin**: Starts at 4 (max) like Identity starts at uptie 4

#### Passive Logic (Critical - New Implementation)
- [ ] **Inheritance walk-back**: `getEffectivePassives()` walks backwards from current threadspin until finding non-empty array
- [ ] **Empty array handling**: Empty passive array at current level inherits from previous level
- [ ] **Locked passives**: `getLockedPassives()` correctly identifies passives from higher threadspins
- [ ] **No duplication**: Same passive doesn't appear in both effective and locked sections
- [ ] **String IDs**: Passive IDs are strings, components accept `passiveId: string`
- [ ] **No variant filtering**: Unlike Identity, EGO shows all higher-tier passives as locked (no enhanced variant deduplication)

#### Skill Rendering
- [ ] **Threadspin merging**: Skills merge data from indices 0 to (threadspin - 1)
- [ ] **Erosion detection**: `hasErosion` correctly identifies empty erosion arrays
- [ ] **Tab locking**: Erosion tab uses `isLocked={!hasErosion}` prop
- [ ] **Empty state**: Shows message when erosion selected but no skills available

#### Mobile Tabs
- [ ] **2-tab mode**: MobileDetailTabs renders 2 tabs when `thirdTabContent` omitted
- [ ] **Grid layout**: Uses `grid-cols-2` for EGO (not `grid-cols-3`)
- [ ] **Tab content**: Both tabs render correct content sections

### Edge Cases

#### Missing Data
- [ ] **Empty erosion array**: Gracefully shows disabled tab, no crash
- [ ] **All empty passive arrays**: Shows "No passives" for effective, no locked section if all tiers empty
- [ ] **Missing i18n**: Falls back to skill/passive ID string (existing pattern)
- [ ] **Invalid threadspin**: Button clicks constrained to 1-4 range

#### Passive Inheritance Edge Cases
- [ ] **First level empty**: If passiveList = `[[], ["20101"], [], []]`, threadspin 1 shows no effective passives
- [ ] **Middle level empty**: Threadspin 3 inherits from threadspin 2 correctly
- [ ] **Last level empty**: Threadspin 4 inherits from most recent non-empty level

#### Type Safety
- [ ] **Threadspin type**: Cast `number` state to `Threadspin` type for props
- [ ] **Passive ID type**: `string` type used throughout (no number coercion)
- [ ] **Skill type**: `'awaken' | 'erosion'` literal type enforced

#### Language Switch Edge Cases
- [ ] **Mid-transition button click**: Clicking threadspin button during language load doesn't crash
- [ ] **Rapid language switches**: Multiple quick switches don't cause race conditions
- [ ] **Component unmount**: Switching away from page during i18n load doesn't error

#### Mobile Responsive
- [ ] **Breakpoint transition**: Resizing from desktop to mobile preserves state
- [ ] **Tab accessibility**: Tabs are keyboard-navigable on mobile
- [ ] **Selector visibility**: Threadspin buttons don't overflow on small screens
- [ ] **Locked passives on mobile**: Both effective and locked sections render in Passives tab

### Integration Points

#### DetailPageLayout Integration
- [ ] **Left column**: EGO header + sin panels render in left column correctly
- [ ] **Right column**: DetailRightPanel with selector + scrollable content works
- [ ] **Mobile layout**: mobileTabsContent renders tabs correctly at <768px

#### DetailEntitySelector Integration
- [ ] **Tier buttons**: Shows 4 buttons for threadspin 1-4
- [ ] **Button highlighting**: Selected button has correct visual state
- [ ] **No level slider**: EGO doesn't show level selector (only Identity)
- [ ] **Sticky behavior**: Selector stays visible when scrolling

#### MobileDetailTabs Integration (After Extension)
- [ ] **IdentityDetailPage compatibility**: Identity page works with renamed props
- [ ] **Disabled tab styling**: shadcn/ui TabsTrigger `disabled` prop applies opacity-50
- [ ] **Third tab flexibility**: Works for both "Sanity" (Identity) and future use cases

#### Shared Components
- [ ] **SkillTabButton**: Works with EGO attribute types, `isLocked` prop functions
- [ ] **DetailEntitySelector**: `entityType="ego"` renders threadspin buttons correctly
- [ ] **FormattedDescription**: Keyword formatting works in EGO passives/skills

#### Constants Usage
- [ ] **MAX_ENTITY_TIER.ego**: Value is 4, matches data structure
- [ ] **MIN_ENTITY_TIER.ego**: Value is 1, button range starts at 1
- [ ] **No hardcoded values**: All tier limits, colors use constants

### Regression Prevention

#### Identity Detail Page
- [ ] **Still works after MobileDetailTabs change**: Prop rename doesn't break Identity
- [ ] **No performance regression**: Identity page still only suspends text on language change
- [ ] **State persistence**: Identity uptie/level state still survives language change

#### Existing EGO Components
- [ ] **EGOSkillCard**: Still works, now wrapped with i18n fetch
- [ ] **SinCostPanel**: Still renders correctly with spec data
- [ ] **SinResistancePanel**: Still renders correctly with spec data
- [ ] **EGOHeader**: Base component still works when wrapped by EGOHeaderWithI18n
