# Component Merge Plan: Identity & EGO Detail Pages

## Already Merged ✅

- **SkillImageComposite** → `/components/common/SkillImageComposite.tsx`
- **SkillDescription** → `/components/identity/SkillDescription.tsx` (reused by EGO)
- **CoinDisplay** → `/components/identity/CoinDisplay.tsx` (reused by EGO)

## Common Patterns to Extract

### 1. Loading State Component (IDENTICAL)

**Current Duplication:**
- IdentityDetailPage lines 59-67, 107-115
- EGODetailPage lines 58-65

**Proposed: `/components/common/LoadingState.tsx`**
```typescript
interface LoadingStateProps {
  message?: string
}
```

**Structure:**
- `container mx-auto p-8`
- `flex items-center justify-center h-64`
- Customizable message

---

### 2. Error State Component (IDENTICAL)

**Current Duplication:**
- IdentityDetailPage lines 69-81, 118-130
- EGODetailPage lines 68-80

**Proposed: `/components/common/ErrorState.tsx`**
```typescript
interface ErrorStateProps {
  title: string
  message: string
}
```

**Structure:**
- `container mx-auto p-8`
- `bg-destructive/10 border border-destructive rounded-lg p-6 text-center`
- Customizable title and message

---

### 3. Detail Page Layout Wrapper (IDENTICAL)

**Current Duplication:**
- IdentityDetailPage lines 132-135
- EGODetailPage lines 85-88

**Proposed: `/components/common/DetailPageLayout.tsx`**
```typescript
interface DetailPageLayoutProps {
  leftColumn: React.ReactNode
  rightColumn: React.ReactNode
}
```

**Structure:**
- `container mx-auto p-8`
- `grid grid-cols-1 lg:grid-cols-2 gap-6`
- `space-y-6` for both columns

---

### 4. Skill Tab Selector Component (VERY SIMILAR)

**Current Duplication:**
- IdentityDetailPage lines 213-254 (4 tabs)
- EGODetailPage lines 113-136 (2 tabs, conditional)

**Proposed: `/components/common/SkillTabSelector.tsx`**
```typescript
interface TabOption {
  key: string
  label: string
  hidden?: boolean
}

interface SkillTabSelectorProps {
  tabs: TabOption[]
  activeTab: string
  onTabChange: (tab: string) => void
}
```

**Structure:**
- `flex gap-2`
- Dynamic tab rendering
- Conditional visibility support

---

### 5. Skill Card Layout Component (IDENTICAL STRUCTURE)

**Current Duplication:**
- IdentitySkillCard lines 36-60
- EGOSkillCard lines 34-56

**Proposed: `/components/common/SkillCardLayout.tsx`**
```typescript
interface SkillCardLayoutProps {
  imageComposite: React.ReactNode
  infoPanel: React.ReactNode
  description: React.ReactNode
}
```

**Structure:**
- `border rounded-lg p-4 space-y-3`
- Top: `flex gap-4` (image + info)
- Bottom: description

---

### 6. Skill Info Panel Base Component (MOSTLY SIMILAR)

**Current Duplication:**
- IdentitySkillInfoPanel: Full component
- EGOSkillInfoPanel: Full component

**Differences:**
- Identity: Has skillEA count, isDefenseSkill check, displays attack weight only
- EGO: Has sanityCost field, always attack (no defense check), displays both attack weight AND sanity cost

**Proposed: `/components/common/SkillInfoPanelBase.tsx`**
```typescript
interface SkillInfoPanelBaseProps {
  coinEA: string
  skillName: string
  skillEA?: number // Optional for EGO
  totalLevel: number
  isDefenseSkill?: boolean
  sanityCost?: number // Optional for EGO
  stats: Array<{ label: string; value: number | string }>
}
```

**Structure:**
- CoinDisplay
- Skill name (+ optional EA count for Identity)
- Level with icon (attack/defense)
- Flexible stats display (attack weight for Identity, attack weight + sanityCost for EGO)

---

## Extraction Priority

### High Priority (Maximum Duplication)
1. **LoadingState** - Used 3 times (Identity x2, EGO x1)
2. **ErrorState** - Used 3 times (Identity x2, EGO x1)
3. **DetailPageLayout** - Used 2 times, core structure
4. **SkillCardLayout** - Used 2 times, identical structure

### Medium Priority (Significant Savings)
5. **SkillTabSelector** - Used 2 times, highly similar
6. **SkillInfoPanelBase** - Used 2 times, moderately similar

## Implementation Steps

1. **Create common layout components first** (LoadingState, ErrorState, DetailPageLayout)
2. **Refactor both detail pages** to use new common components
3. **Extract SkillCardLayout** and update both SkillCard components
4. **Extract SkillTabSelector** and update both detail pages
5. **Consider SkillInfoPanelBase** - May need different approach due to significant logic differences

## Estimated Impact

### Before:
- IdentityDetailPage: ~330 lines
- EGODetailPage: ~170 lines
- Identity components: ~200 lines
- EGO components: ~150 lines
- **Total: ~850 lines**

### After (Estimated):
- Common components: ~150 lines (new)
- IdentityDetailPage: ~200 lines (-130)
- EGODetailPage: ~100 lines (-70)
- Identity components: ~120 lines (-80)
- EGO components: ~80 lines (-70)
- **Total: ~650 lines (-200 lines, -23%)**

## Benefits

- **Consistency**: Identical UI patterns across Identity and EGO pages
- **Maintainability**: Single source of truth for common layouts
- **Testability**: Common components can be tested once
- **Developer Experience**: Faster development of future detail pages

## Risks

- **Over-abstraction**: May make simple components harder to understand
- **Props complexity**: Generic components may have many optional props
- **Type safety**: Generic components need careful TypeScript design

## Recommendation

**Proceed with High Priority items only** - These provide clear benefits with minimal abstraction complexity.

**Defer SkillInfoPanelBase** - The logic differences are significant enough that separate components may be clearer.
