# Execution Plan: PlannerSection Unified Component

## Execution Overview

Create unified `PlannerSection` component and migrate all planner sections.
- Foundation first (new component)
- Low-risk migrations (SectionContainer users)
- Medium-risk migrations (inline styling)
- Page integration (Floor Themes)
- Cleanup (deprecate old component)

Each step independently verifiable. No build breaks between steps.

---

## Execution Order

### Phase 1: Foundation

1. **PlannerSection.tsx (NEW)**: Create unified section wrapper
   - Depends on: none
   - Enables: F1, all migrations
   - Pattern: SectionContainer.tsx
   - Uses: SECTION_STYLES.container, TEXT.header, SPACING.content

### Phase 2: Low-Risk Migrations

2. **EGOGiftObservationSection.tsx**: Replace SectionContainer, move caption to children
   - Depends on: Step 1
   - Enables: F2
   - Wrap StarlightCostDisplay in `flex justify-end mb-4` as first child

3. **EGOGiftComprehensiveListSection.tsx**: Replace SectionContainer
   - Depends on: Step 1
   - Enables: F1
   - Simple import swap

### Phase 3: Medium-Risk Migrations

4. **StartBuffSection.tsx**: Wrap in PlannerSection
   - Depends on: Step 1
   - Enables: F1
   - Remove: space-y-2 wrapper, label element
   - Keep: Grid div

5. **StartGiftSection.tsx**: Wrap in PlannerSection, move counter
   - Depends on: Step 1
   - Enables: F2, F3
   - Remove: space-y-4, flex header, h2, span
   - Move: Counter to children first element

6. **SkillReplacementSection.tsx**: Wrap in PlannerSection
   - Depends on: Step 1
   - Enables: F1, F4
   - Remove: bg-muted container, h2 with mb-4
   - Keep: Grid + modal

7. **DeckBuilder.tsx**: Wrap in PlannerSection
   - Depends on: Step 1
   - Enables: F1
   - Add: PlannerSection with title
   - Note: Internal containers unchanged (sub-sections)

### Phase 4: Page Integration

8. **FloorThemeGiftSection.tsx**: Add border class
   - Depends on: none (parallel with Phase 2-3)
   - Enables: F5
   - Add: `border border-border` to bg-muted div

9. **PlannerMDNewPage.tsx**: Wrap Floor Themes in PlannerSection
   - Depends on: Step 1, Step 8
   - Enables: F1
   - Replace: div + h2 structure with PlannerSection

### Phase 5: Cleanup

10. **SectionContainer.tsx**: Add @deprecated JSDoc
    - Depends on: Steps 2, 3
    - Enables: F6

---

## Verification Checkpoints

| After Step | Check | Method |
|------------|-------|--------|
| 1 | PlannerSection structure | DOM inspect |
| 3 | No SectionContainer in EGO Gift sections | Grep |
| 6 | All sections use PlannerSection | Grep |
| 9 | Floor Themes wrapped | Visual |
| 10 | Build passes | `yarn build` |

**Manual UI (after Step 9):**
- All sections: identical h2 + bordered container
- Start Gifts counter: inside container
- EGO Gift cost: inside container
- Floor Themes: wraps 15 rows
- Each floor row: has border
- Dark mode: consistent
- Mobile: no breaks

---

## Rollback Strategy

**Safe stopping points:**
- After Step 1: Component exists, nothing uses it
- After Step 3: EGO Gift sections migrated
- After Step 6: All section components migrated
- After Step 9: Full migration complete

**If step fails:**
- Steps 2-6: Revert individual file
- Step 7: DeckBuilder complex - revert and investigate
- Step 9: Revert, Floor Themes still renders
- Step 10: Skip if needed

**Critical**: Step 1 must succeed first
