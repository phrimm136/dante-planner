# Task: PlannerSection Unified Component Migration

## Description

Create a unified `PlannerSection` component that enforces consistent styling across ALL sections in PlannerMDNewPage. The component must be maximally simple with only two props: `title` and `children`. No optional props allowed.

### Component Specification

```typescript
interface PlannerSectionProps {
  title: string
  children: React.ReactNode
}
```

### Rendered Structure

Every section must render identically:
- `<section className="space-y-4">` outer wrapper
- `<h2 className="text-xl font-semibold">` for title
- `<div className="bg-muted border border-border rounded-md p-6">` container for children

---

## Core Migration Pattern

### What to Extract from Each Section Component

각 섹션 컴포넌트에서:

1. **타이틀 요소 제거** - 기존 `<h2>`, `<h3>`, `<label>` 등 타이틀 역할 요소 삭제
2. **컨테이너 wrapper 제거** - 기존 `bg-muted`, `border`, `rounded-md` 등의 외부 컨테이너 삭제
3. **나머지 전체를 children으로** - 타이틀과 컨테이너를 제외한 모든 내용물을 그대로 유지
4. **PlannerSection으로 래핑** - 추출한 타이틀 텍스트는 `title` prop으로, 나머지는 `children`으로

### Before/After Example

**Before (StartBuffSection):**
```tsx
return (
  <div className="space-y-2">
    <label className="text-sm font-medium">{t('pages.plannerMD.startBuffs')}</label>  {/* 타이틀 */}
    <div className={`${SECTION_STYLES.gridDense} gap-3`}>  {/* 컨텐츠 */}
      {displayBuffs.map((buff) => (
        <StartBuffCard ... />
      ))}
    </div>
  </div>
)
```

**After:**
```tsx
return (
  <PlannerSection title={t('pages.plannerMD.startBuffs')}>
    {/* 타이틀과 외부 wrapper 제거, 내용물만 children으로 */}
    <div className={`${SECTION_STYLES.gridDense} gap-3`}>
      {displayBuffs.map((buff) => (
        <StartBuffCard ... />
      ))}
    </div>
  </PlannerSection>
)
```

### Before/After Example (Caption이 있는 경우)

**Before (EGOGiftObservationSection):**
```tsx
return (
  <SectionContainer
    title={t('pages.plannerMD.egoGiftObservation')}
    caption={<StarlightCostDisplay cost={currentCost} size="lg" />}  {/* caption prop */}
  >
    <div className="flex gap-4 justify-between">...</div>
    <div className="grid grid-cols-10 gap-4">...</div>
  </SectionContainer>
)
```

**After:**
```tsx
return (
  <PlannerSection title={t('pages.plannerMD.egoGiftObservation')}>
    {/* caption을 children 첫 요소로 이동 */}
    <div className="flex justify-end mb-4">
      <StarlightCostDisplay cost={currentCost} size="lg" />
    </div>
    {/* 기존 컨텐츠 그대로 */}
    <div className="flex gap-4 justify-between">...</div>
    <div className="grid grid-cols-10 gap-4">...</div>
  </PlannerSection>
)
```

### Before/After Example (SkillReplacementSection - inline 스타일)

**Before:**
```tsx
return (
  <div className="bg-muted border border-border rounded-md p-4">  {/* 인라인 컨테이너 */}
    <h2 className="text-xl font-semibold mb-4">  {/* 타이틀 */}
      {t('pages.plannerMD.skillReplacement.title')}
    </h2>
    <div className="grid grid-cols-2 sm:grid-cols-3 ...">  {/* 컨텐츠 */}
      {SINNERS.map(...)}
    </div>
    {selectedSinner && <SkillExchangeModal ... />}
  </div>
)
```

**After:**
```tsx
return (
  <PlannerSection title={t('pages.plannerMD.skillReplacement.title')}>
    {/* 타이틀과 컨테이너 제거, 내용물만 */}
    <div className="grid grid-cols-2 sm:grid-cols-3 ...">
      {SINNERS.map(...)}
    </div>
    {selectedSinner && <SkillExchangeModal ... />}
  </PlannerSection>
)
```

---

## Migration Checklist by Component

| Component | 제거할 요소 | title로 추출 | children으로 보낼 것 |
|-----------|-------------|--------------|---------------------|
| DeckBuilder | 외부 `<div className="space-y-6">` | 새로 추가: "Deck Builder" | 내부 컨테이너들 전체 |
| StartBuffSection | `<div className="space-y-2">`, `<label>` | t('pages.plannerMD.startBuffs') | grid div |
| StartGiftSection | `<div className="space-y-4">`, `<h2>`, `<span>` | t('pages.plannerMD.startGift') | counter div + keyword rows |
| EGOGiftObservationSection | `<SectionContainer>` wrapper | title prop 값 | caption을 첫 요소로 + 기존 children |
| EGOGiftComprehensiveListSection | `<SectionContainer>` wrapper | title prop 값 | 기존 children 그대로 |
| SkillReplacementSection | `<div className="bg-muted...">`, `<h2>` | t('pages.plannerMD.skillReplacement.title') | grid + modal |
| PlannerMDNewPage Floor Themes | `<h2>` | t('pages.plannerMD.floorThemes') | floor items loop |

---

## FloorThemeGiftSection 특별 처리

FloorThemeGiftSection은 "섹션"이 아니라 "행 아이템":

**PlannerMDNewPage에서:**
```tsx
// Before
<div className="space-y-4">
  <h2 className="text-xl font-semibold">{t('pages.plannerMD.floorThemes')}</h2>
  <div className="space-y-4">
    {floorIndices.map((i) => <FloorThemeGiftSection ... />)}
  </div>
</div>

// After
<PlannerSection title={t('pages.plannerMD.floorThemes')}>
  <div className="space-y-4">
    {floorIndices.map((i) => <FloorThemeGiftSection ... />)}
  </div>
</PlannerSection>
```

**FloorThemeGiftSection 자체:**
- `bg-muted rounded-lg` 유지 (행 아이템 스타일)
- `border border-border` 추가 (일관성)

---

## Research

- `frontend/src/components/common/SectionContainer.tsx` - Current implementation to replace
- `frontend/src/lib/constants.ts` lines 260-301 - SECTION_STYLES tokens
- `frontend/src/components/egoGift/EGOGiftObservationSection.tsx` - Caption pattern to convert

## Scope

Read for context:
- `frontend/src/components/common/SectionContainer.tsx`
- `frontend/src/components/deckBuilder/DeckBuilder.tsx`
- `frontend/src/components/startBuff/StartBuffSection.tsx`
- `frontend/src/components/startGift/StartGiftSection.tsx`
- `frontend/src/components/egoGift/EGOGiftObservationSection.tsx`
- `frontend/src/components/egoGift/EGOGiftComprehensiveListSection.tsx`
- `frontend/src/components/skillReplacement/SkillReplacementSection.tsx`
- `frontend/src/components/floorTheme/FloorThemeGiftSection.tsx`
- `frontend/src/routes/PlannerMDNewPage.tsx`

## Target Code Area

### New Files
- `frontend/src/components/common/PlannerSection.tsx`

### Modified Files
- `frontend/src/components/common/SectionContainer.tsx` - Add @deprecated
- `frontend/src/components/deckBuilder/DeckBuilder.tsx`
- `frontend/src/components/startBuff/StartBuffSection.tsx`
- `frontend/src/components/startGift/StartGiftSection.tsx`
- `frontend/src/components/egoGift/EGOGiftObservationSection.tsx`
- `frontend/src/components/egoGift/EGOGiftComprehensiveListSection.tsx`
- `frontend/src/components/skillReplacement/SkillReplacementSection.tsx`
- `frontend/src/routes/PlannerMDNewPage.tsx`
- `frontend/src/components/floorTheme/FloorThemeGiftSection.tsx`

## Testing Guidelines

### Manual UI Testing

1. Navigate to /planner/md/new
2. Verify all sections have identical structure:
   - h2 title (text-xl font-semibold)
   - bg-muted container with border and rounded-md
   - p-6 padding inside container
3. Verify Start Gifts counter appears INSIDE container (not next to title)
4. Verify EGO Gift Observation starlight cost appears INSIDE container
5. Verify Floor Themes section wraps all 15 floor rows
6. Verify each floor row has its own border
7. Toggle dark mode - verify consistency
8. Resize to mobile - verify layout doesn't break

### Automated Functional Verification

- [ ] PlannerSection renders `<section>` with `space-y-4`
- [ ] PlannerSection renders `<h2>` with `text-xl font-semibold`
- [ ] PlannerSection renders container with `bg-muted border border-border rounded-md p-6`
- [ ] All section components use PlannerSection as root wrapper
- [ ] SectionContainer has @deprecated JSDoc comment
- [ ] No component uses caption prop (moved to children)
- [ ] FloorThemeGiftSection has border-border class

### Edge Cases

- [ ] Empty section: Container renders correctly
- [ ] Very long title: Wraps without breaking
- [ ] Nested borders: No visual conflict between section and floor rows
