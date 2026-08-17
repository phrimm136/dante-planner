# Execution Plan: FilterSidebar Implementation

## Planning Gaps
**NONE** - Research is complete and all information available.

## Clarifications Resolved
- i18n for seasons: `static/i18n/{lang}/seasons.json`
- i18n for associations: `static/i18n/{lang}/unitKeywords.json`

## Execution Overview

Bottom-up approach across 5 phases:
1. Install dependencies, add constants/i18n (blocking)
2. Build atomic filter components
3. Build layout wrapper components
4. Integrate into page and update filtering logic
5. Polish and verify

**Total: 16 steps**

---

## Execution Order

### Phase 1: Dependencies & Data Layer (Steps 1-4)

1. **[shadcn CLI]**: Run `yarn run shadcn add sheet`
   - Depends on: none
   - Enables: F2

2. **[constants.ts]**: Add SEASONS and ASSOCIATIONS constants
   - Depends on: none
   - Enables: F7, F8, F9

3. **[i18n files]**: Add/verify season and association translations
   - `static/i18n/{lang}/seasons.json` for season names
   - `static/i18n/{lang}/unitKeywords.json` for association names
   - Depends on: Step 2
   - Enables: F8, F9

4. **[IdentityTypes.ts]**: Extend Identity interface with new fields
   - Depends on: none
   - Enables: F3-F9

### Phase 2: Filter Components (Steps 5-9)

5. **[FilterSection.tsx]**: Create collapsible section wrapper
   - Depends on: none
   - Enables: F10
   - Pattern: Custom with Tailwind transitions

6. **[SkillAttributeFilter.tsx]**: Create skill attribute icon filter
   - Depends on: Step 4, Step 5
   - Enables: F5
   - Pattern: SinnerFilter.tsx

7. **[AttackTypeFilter.tsx]**: Create attack type icon filter
   - Depends on: Step 4, Step 5
   - Enables: F6
   - Pattern: SinnerFilter.tsx

8. **[RankFilter.tsx]**: Create rank toggle button filter
   - Depends on: Step 4, Step 5
   - Enables: F7
   - Pattern: Custom toggle buttons

9. **[SeasonDropdown.tsx + AssociationDropdown.tsx]**: Create dropdown multi-selects
   - Depends on: Step 2, Step 3, Step 4, Step 5
   - Enables: F8, F9
   - Pattern: DropdownMenuCheckboxItem

### Phase 3: Layout Components (Steps 10-11)

10. **[FilterSidebar.tsx]**: Create responsive sidebar wrapper
    - Depends on: Step 1, Step 5
    - Enables: F1, F2, F11, F12

11. **[SearchBar]**: No change needed - remains at top of content

### Phase 4: Page Integration (Steps 12-13)

12. **[IdentityPage.tsx]**: Refactor layout to use FilterSidebar
    - Depends on: Steps 5-10
    - Enables: F1, F2
    - Add state for new filters, update Identity mapping

13. **[IdentityList.tsx]**: Extend filtering logic
    - Depends on: Step 12
    - Enables: F3-F9
    - Add: attributeType (OR), atkType (OR), rank (exact), season (exact), associationList (OR)

### Phase 5: Polish & Edge Cases (Steps 14-16)

14. **[Empty state]**: Verify IdentityList empty state works
    - Depends on: Step 13
    - Enables: E1

15. **[Filter reset]**: Verify state resets on page leave
    - Depends on: Step 12
    - Enables: E2

16. **[Manual testing]**: Run through all test scenarios
    - Depends on: Steps 1-15
    - Enables: All features verified

---

## Verification Checkpoints

| After Step | Verify | Method |
|------------|--------|--------|
| 1 | Sheet installed | `ls frontend/src/components/ui/sheet.tsx` |
| 5 | FilterSection expands/collapses | Visual test |
| 6-8 | Icon filters render and toggle | Visual test |
| 9 | Dropdowns show localized options | Check i18n renders |
| 10 | Sidebar on desktop, Sheet on mobile | Resize browser |
| 12 | Page renders with sidebar layout | Navigate to /identity |
| 13 | Filters actually filter list | Select sinner, verify list updates |
| 16 | All F1-F12, E1-E2 work | Follow test checklist |

---

## Rollback Strategy

**Safe stopping points:**
- After Step 4: Types extended, no UI changes
- After Step 9: All components built, page unchanged
- After Step 11: Layout ready, can integrate incrementally

**If step fails:**
- Steps 1-4: No rollback needed, isolated
- Steps 5-9: Delete new component file
- Step 10: Delete FilterSidebar.tsx
- Steps 12-13: `git checkout -- IdentityPage.tsx IdentityList.tsx`
