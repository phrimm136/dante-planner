# Research: FilterSidebar Implementation

## Spec Ambiguities
**NONE FOUND** - Specification is clear and well-defined.

---

## Spec-to-Code Mapping

| Requirement | Current State | Action Needed |
|-------------|---------------|---------------|
| Desktop/Mobile Layout | Horizontal inline | Refactor to responsive sidebar + Sheet |
| Skill Attributes Filter | Not implemented | Create `SkillAttributeFilter.tsx` (8 icons) |
| Attack Types Filter | Not implemented | Create `AttackTypeFilter.tsx` (3 icons) |
| Rank Filter | Not implemented | Create `RankFilter.tsx` (3 toggles) |
| Season Filter | Data exists in spec | Create `SeasonDropdown.tsx` + i18n |
| Association Filter | Data exists in spec | Create `AssociationDropdown.tsx` + i18n |
| Collapsible Sections | Not implemented | Create `FilterSection.tsx` with indicators |
| Identity Type Extension | Missing fields | Add season, attributeTypes, atkTypes, associations |
| Filter Logic | Partial | Extend `IdentityList.tsx` for new types |

---

## Pattern Enforcement (MANDATORY)

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `FilterSidebar.tsx` | `IdentityPage.tsx` | Layout structure, state management |
| `FilterSection.tsx` | `IconFilter.tsx` | Collapsible wrapper, styling |
| `SkillAttributeFilter.tsx` | `SinnerFilter.tsx` | Wrapper → IconFilter composition |
| `AttackTypeFilter.tsx` | `SinnerFilter.tsx` | Wrapper → IconFilter composition |
| `RankFilter.tsx` | `IconFilter.tsx` | Button-based selection |
| `SeasonDropdown.tsx` | `dropdown-menu.tsx` | Multi-select with checkboxes |
| `AssociationDropdown.tsx` | `dropdown-menu.tsx` | Multi-select with checkboxes |

---

## Existing Utilities

| Category | Location | Found |
|----------|----------|-------|
| Asset Paths | `lib/assetPaths.ts` | `getSinnerIconPath()`, `getKeywordIconPath()` |
| Constants | `lib/constants.ts` | `SINNERS`, `STATUS_EFFECTS`, `AFFINITIES`, `SKILL_ATTRIBUTE_TYPES`, `ATK_TYPES` |
| Formatters | `lib/utils.ts` | `getSinnerFromId()`, `getKeywordDisplayName()` |
| Validators | `lib/validation.ts` | Validation patterns exist |
| i18n | `static/i18n/` | Season/association keys missing |

---

## Gap Analysis

**Currently Missing:**
- `FilterSidebar.tsx`, `FilterSection.tsx`
- `SkillAttributeFilter.tsx`, `AttackTypeFilter.tsx`, `RankFilter.tsx`
- `SeasonDropdown.tsx`, `AssociationDropdown.tsx`
- `components/ui/sheet.tsx` (requires `yarn run shadcn add sheet`)
- Season & Association i18n translations

**Needs Modification:**
- `types/IdentityTypes.ts` - Add new fields to Identity
- `hooks/useIdentityListData.ts` - Include new fields
- `routes/IdentityPage.tsx` - Refactor layout
- `components/identity/IdentityList.tsx` - Extend filter logic
- `lib/assetPaths.ts` - Verify affinity/attack icon helpers
- `static/i18n/{EN,JP,KR,CN}/common.json` - Add translations

**Can Reuse:**
- `IconFilter.tsx` for Skill Attributes, Attack Types
- `SinnerFilter.tsx` pattern for all wrappers
- Filter state management from `IdentityPage.tsx`
- Zod validation in `IdentitySchemas.ts`

---

## Testing Requirements

### Manual UI Tests
- Desktop: Sidebar sticky LEFT, sections expand/collapse, count indicators
- Mobile: Sheet drawer from bottom, toggle button with badge
- Filters: Sinner, Keyword, Attribute, AttackType, Rank, Season, Association
- Reset All clears everything

### Automated Tests
- Filter functions (OR/AND logic)
- Component render at breakpoints
- Badge count accuracy
- Sheet open/close state
- i18n translations

### Edge Cases
- No results after filtering
- Rapid filter toggling
- Filters reset on navigation
- Missing attributeType/atkType data

---

## Technical Constraints

**Blocking Task:**
- Run `yarn run shadcn add sheet` before implementation

**Pattern Compliance:**
- State as `Set<string>` (following existing filters)
- Filtering logic in `IdentityList.tsx`
- No manual `memo`/`useCallback` (React Compiler)
- All strings → i18n keys
- All asset paths → `lib/assetPaths.ts`
- All constants → `lib/constants.ts`

**Data Structure:**
- Season is numeric (0+)
- Association/attributeType/atkType are string arrays
- Backward compatible for identities missing new fields

---

## Implementation Order

1. Add Sheet component: `yarn run shadcn add sheet`
2. Update types: `IdentityTypes.ts`
3. Update data hook: `useIdentityListData.ts`
4. Create filter components (isolated, testable)
5. Create FilterSidebar + FilterSection wrappers
6. Refactor IdentityPage layout
7. Extend IdentityList filtering logic
8. Add i18n translations
9. Test desktop and mobile
