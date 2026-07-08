# Code: PlannerSection Migration

## Files Changed

### Phase 1: Foundation
| File | Action | Description |
|------|--------|-------------|
| `frontend/src/components/common/PlannerSection.tsx` | Created | Unified section wrapper with h2 + container |

### Phase 2: Low-Risk Migrations
| File | Action | Description |
|------|--------|-------------|
| `frontend/src/components/egoGift/EGOGiftObservationSection.tsx` | Modified | Replaced SectionContainer, moved cost to children |
| `frontend/src/components/egoGift/EGOGiftComprehensiveListSection.tsx` | Modified | Replaced SectionContainer |

### Phase 3: Medium-Risk Migrations
| File | Action | Description |
|------|--------|-------------|
| `frontend/src/components/startBuff/StartBuffSection.tsx` | Modified | Wrapped in PlannerSection |
| `frontend/src/components/startGift/StartGiftSection.tsx` | Modified | Wrapped in PlannerSection, moved counter |
| `frontend/src/components/skillReplacement/SkillReplacementSection.tsx` | Modified | Wrapped in PlannerSection |
| `frontend/src/components/deckBuilder/DeckBuilder.tsx` | Modified | Wrapped in PlannerSection, added i18n |

### Phase 4: Page Integration
| File | Action | Description |
|------|--------|-------------|
| `frontend/src/components/floorTheme/FloorThemeGiftSection.tsx` | Modified | Added border-border class |
| `frontend/src/routes/PlannerMDNewPage.tsx` | Modified | Wrapped Floor Themes in PlannerSection |

### Phase 5: Cleanup
| File | Action | Description |
|------|--------|-------------|
| `frontend/src/components/common/SectionContainer.tsx` | Modified | Added @deprecated JSDoc |

### Localization
| File | Action | Description |
|------|--------|-------------|
| `static/i18n/EN/common.json` | Modified | Added `pages.plannerMD.deckBuilder` key |
| `static/i18n/CN/common.json` | Modified | Added `pages.plannerMD.deckBuilder` key |
| `static/i18n/JP/common.json` | Modified | Added `pages.plannerMD.deckBuilder` key |
| `static/i18n/KR/common.json` | Modified | Added `pages.plannerMD.deckBuilder` key |

---

## Verification Results

### Grep Verification
```bash
# No SectionContainer imports remain
$ grep -r "import.*SectionContainer" frontend/src/
# Result: No matches ✓

# All sections migrated to PlannerSection
$ grep -r "import.*PlannerSection" frontend/src/
# Result: 7 components found ✓

# i18n key used correctly
$ grep -r 't.*pages.plannerMD.deckBuilder' frontend/src/
# Result: DeckBuilder.tsx:349 ✓
```

### Lint Verification
```bash
$ yarn eslint src/components/common/PlannerSection.tsx src/components/common/SectionContainer.tsx
# Result: No errors ✓
```

---

## Issues & Resolutions

### Issue #1: Missing React Import
- **Problem**: PlannerSection used `React.ReactNode` without importing React
- **Resolution**: Added `import type { ReactNode } from 'react'`

### Issue #2: Hardcoded DeckBuilder Title
- **Problem**: `<PlannerSection title="Deck Builder">` broke i18n
- **Resolution**:
  1. Added `pages.plannerMD.deckBuilder` to all 4 language files
  2. Changed to `title={t('pages.plannerMD.deckBuilder')}`

### Issue #3: Nested Containers in DeckBuilder
- **Analysis**: DeckBuilder has internal subsections (Formation, Entity List) with their own containers
- **Resolution**: Kept as-is per plan.md Step 7: "Note: Internal containers unchanged (sub-sections)"
- **Rationale**: Intentional design for visual grouping of complex subsections

---

## Architecture Notes

### PlannerSection API
```tsx
interface PlannerSectionProps {
  title: string       // Section heading
  children: ReactNode // Content inside container
}
```

### Styling Tokens Used
- `SECTION_STYLES.SPACING.content` → `space-y-4`
- `SECTION_STYLES.TEXT.header` → `text-xl font-semibold`
- `SECTION_STYLES.container` → `bg-muted border border-border rounded-md p-6`

### Migration Pattern
```tsx
// Before (SectionContainer)
<SectionContainer title="..." caption={...}>
  {content}
</SectionContainer>

// After (PlannerSection)
<PlannerSection title="...">
  <div className="flex justify-end mb-4">{caption}</div>
  {content}
</PlannerSection>
```
