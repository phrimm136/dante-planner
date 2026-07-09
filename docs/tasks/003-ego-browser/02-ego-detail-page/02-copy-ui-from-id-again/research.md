# Research: EGO Detail Page Pattern Application

## Spec Ambiguities
**NONE** - Specification is complete with concrete examples and test cases.

---

## Spec-to-Code Mapping

**Core Requirements:**
- Threadspin state management: Convert hardcoded `threadspinIndex = 3` to clickable tier buttons (1-4)
- Granular Suspense: Split `useEGODetailData()` → `useEGODetailSpec()` (stable) + `useEGODetailI18n()` (suspends)
- Header i18n wrapper: New `EGOHeaderI18n.tsx` wraps `EGOHeader` with name-only suspension
- Skills i18n wrapper: New `EGOSkillCardI18n.tsx` wraps skill cards with granular Suspense
- Passives with locking: New `EGOPassiveCardI18n.tsx` with `getEffectivePassives()` + `getLockedPassives()` logic
- Passive inheritance: Walk backwards from current threadspin until non-empty array
- Erosion tab disabled: Use `SkillTabButton` with `isLocked={!hasErosion}`
- Mobile tabs: Extend `MobileDetailTabs.tsx` with `disabled` prop support

**Already Exists (Reuse):**
- DetailEntitySelector: Already supports `entityType="ego"` with tier buttons
- Constants: `MAX_ENTITY_TIER.ego = 4`, `MIN_ENTITY_TIER.ego = 1`
- FormattedDescription: Keyword formatting utility
- Asset path helpers: `getEGOIconPath()`, `getAffinityIconPath()`
- Schemas: `EGODataSchema`, `EGOI18nSchema`

---

## Pattern Enforcement

**Files to Create:**

| New File | Must Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `EGOHeaderI18n.tsx` | `IdentityHeaderI18n.tsx` | Simple wrapper, `useIdentityDetailI18n()` call, return name only |
| `EGOSkillCardI18n.tsx` | `SkillI18n.tsx` (Identity) | Wrap base component with Suspense, fetch i18n inside |
| `EGOPassiveCardI18n.tsx` | `PassiveI18n.tsx` (Identity lines 87-130) | Two-tier Suspense: name + desc separate, `isLocked` prop, string IDs |

**Files to Modify:**

| File | Must Read First | Pattern to Copy |
|------|-----------------|-----------------|
| `useEGODetailData.ts` | `useIdentityDetailData.ts` | Export 3 hooks: spec, i18n, combined (deprecated) |
| `EGODetailPage.tsx` | `IdentityDetailPage.tsx` (lines 34-411) | Shell pattern with state, passive logic, no inline JSX |
| `MobileDetailTabs.tsx` | Current implementation | Add `disabled` prop, rename `sanityContent` → `thirdTabContent` |
| `IdentityDetailPage.tsx` | Current usage | Update MobileDetailTabs props (rename only) |

---

## Existing Utilities

**Formatters:**
- `FormattedDescription` - Used for passive/skill descriptions with keyword highlighting

**Asset Paths:**
- `getEGOIconPath()` - EGO rank icons
- `getAffinityIconPath()` - Skill/passive affinity colors
- `getEGOTierIconPath()` - Threadspin tier buttons (already in DetailEntitySelector)

**Constants:**
- `MAX_ENTITY_TIER.ego = 4`
- `MIN_ENTITY_TIER.ego = 1`
- All tier/level constants already defined

**Hooks:**
- `useEGODetailData()` - To split into spec + i18n variants
- Pattern exists in `useIdentityDetailData.ts` for reference

**Schemas:**
- `EGODataSchema` - Validates spec JSON
- `EGOI18nSchema` - Validates i18n JSON
- No new schemas needed

---

## Gap Analysis

**Currently Missing:**
- `EGOHeaderI18n.tsx` component
- `EGOSkillCardI18n.tsx` component
- `EGOPassiveCardI18n.tsx` component
- `useEGODetailSpec()` hook
- `useEGODetailI18n()` hook
- Threadspin state management in EGODetailPage
- Passive locking logic (`getEffectivePassives`, `getLockedPassives`)

**Needs Modification:**
- `EGODetailPage.tsx` - Major refactor (inline → component-based, add state, passive logic)
- `useEGODetailData.ts` - Hook split (add spec/i18n variants, deprecate combined)
- `MobileDetailTabs.tsx` - Add disabled tabs support
- `IdentityDetailPage.tsx` - Update MobileDetailTabs prop names

**Can Reuse:**
- `DetailPageLayout` - Layout wrapper (no changes)
- `DetailEntitySelector` - Tier button selector (no changes)
- `DetailRightPanel` - Right column with sticky selector (no changes)
- `SkillTabButton` - Skill type tabs with disabled state (no changes)
- `FormattedDescription` - Keyword formatter (no changes)
- All constants, asset helpers, schemas (no changes)

---

## Testing Requirements

### Manual UI Tests (Human Verification)

**Threadspin Button Selection:**
- Verify 4 tier buttons render (1, 2, 3, 4)
- Default selection: Button 4 highlighted
- Click button 1: Highlight moves, passive/skill content updates
- Click through 1→2→3→4: State updates correctly

**Language Change Stability:**
- Set threadspin to 2, select Erosion tab
- Switch language EN → KR → JP
- Verify: Only name text shows skeleton
- Verify: Tier buttons, sin panels, tab selection stay visible
- Verify: Threadspin 2 persists, Erosion tab stays active

**Passive Locking Logic:**
- Threadspin 1: Passive "2010111" in Locked section (opacity-50, lock icon)
- Threadspin 2: Passive moves to Effective section
- Threadspin 3-4: Passive remains in Effective (inherited)

**Erosion Tab Disabled:**
- EGO without erosion: Tab visible but dimmed (opacity-50)
- Tab non-clickable (pointer-events-none)
- No hover effect (cursor-not-allowed)
- Awakening tab fully interactive

**Mobile Tabs (<768px):**
- 2 tabs visible: "Skills" and "Passives"
- Threadspin selector above tabs
- Effective + Locked passives both render in Passives tab
- State persists on tab switching

### Automated Functional Verification

**Data Fetching:**
- Spec query key: `['ego', id]` (no language component)
- I18n query key: `['ego', id, 'i18n', language]` (includes language)
- Language change: Spec NOT re-fetched, i18n IS re-fetched

**State Management:**
- Threadspin state: Number type, range 1-4
- Skill type state: `'awaken' | 'erosion'` persists on language change
- No re-suspension in shell component on language change

**Passive Logic:**
- `getEffectivePassives()`: Walks backwards until non-empty array
- Empty array inheritance: `[[], ["2010111"], [], []]` at level 3 returns `["2010111"]`
- `getLockedPassives()`: Returns passives from higher threadspins
- No duplication: Passive not in both effective + locked
- String IDs: No type coercion, accept `passiveId: string`

**Skill Rendering:**
- Skill data merging: Merge indices 0 to (threadspin - 1)
- Empty erosion handling: `hasErosion` boolean check, disabled tab
- Tab locking: `isLocked` prop on SkillTabButton

### Edge Cases

**Missing Data:**
- Empty erosion array: Tab disabled (not hidden, not crash)
- All passive arrays empty: "No passives" message
- Missing i18n: Fallback to passive/skill ID string

**Passive Inheritance:**
- First level empty: Threadspin 1 shows no effective passives
- Middle level empty: Threadspin 3 inherits from threadspin 2
- Last level empty: Threadspin 4 inherits from most recent non-empty

**Language Switch During Interaction:**
- Clicking threadspin button during language load: No crash
- Rapid language switches: No race conditions
- Component unmount during i18n load: No error

**Mobile Responsive:**
- Breakpoint transition (<768px): State persists
- Tab keyboard navigation: Accessible
- Threadspin buttons: No overflow on small screens

---

## Technical Constraints

**Query Key Language Component:**
- Spec queries must NOT include `i18n.language` to avoid re-fetch on language change
- Solution: Separate `useEGODetailSpec()` hook with `['ego', id]` key only

**Passive ID Type:**
- EGO uses `string` IDs, Identity uses `number` IDs
- Solution: Accept `passiveId: string` throughout EGO components

**Passive Inheritance:**
- Empty arrays mean "inherit from previous level" (same as Identity uptie)
- Solution: Copy Identity's `getEffectivePassives()` walk-back logic (lines 58-65)

**Threadspin = Uptie:**
- Same concept, different terminology
- Solution: Use DetailEntitySelector with `entityType="ego"` (already supports tier buttons)

**TabsTrigger Disabled:**
- shadcn/ui TabsTrigger supports `disabled` prop (built-in opacity-50 styling)
- Solution: Pass `disabled` prop to TabsTrigger in MobileDetailTabs

**React Compiler:**
- Manual `memo`, `useCallback` forbidden
- Solution: Let React Compiler handle optimization automatically

---

## Key Pattern Decisions

**Shell Component Pattern (from IdentityDetailPage):**
- Shell uses `useEGODetailSpec()` (no language dependency)
- All state lives in shell: `threadspin`, `skillType`
- Child components use `useEGODetailI18n()` with Suspense
- Result: State persists, layout stays visible during language change

**Passive Logic (Simpler than Identity):**
- Identity: Enhanced variants (type 0/1/2), deduplication, condition icons
- EGO: Simple string IDs, no variants, no conditions
- Shared: Inheritance walk-back, locked preview from higher tiers
- Implementation: Copy `getEffectivePassives()`, simplify `getLockedPassives()` (no variant filtering)

**Mobile Tabs Extension:**
- Rename `sanityContent` → `thirdTabContent` for generic naming
- Add `disabled` prop support for each tab
- Backward compat: Keep `sanityContent` as alias or update Identity in same commit

**Breaking Changes:**
- MobileDetailTabs prop rename (affects IdentityDetailPage)
- useEGODetailData() deprecated but kept for backward compatibility

---

## Implementation Priority

1. **Hook split** (useEGODetailData.ts) - Foundation for granular Suspense
2. **I18n wrapper components** (3 new files) - Enable component-based rendering
3. **MobileDetailTabs extension** - Shared component, affects both Identity + EGO
4. **IdentityDetailPage update** - Fix breaking change from MobileDetailTabs
5. **EGODetailPage refactor** - Main implementation with passive logic

**Total Estimated Scope:** ~300 lines new code, ~150 lines refactored, ~20 lines updated (Identity)
