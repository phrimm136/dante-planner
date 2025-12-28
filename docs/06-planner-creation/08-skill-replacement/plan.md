# Skill Replacement - Execution Plan

## Execution Overview

This feature adds a skill exchange section between EGO Gift Observation and Comprehensive sections:
1. Extend DeckTypes to store skill EA per sinner
2. Create SkillCardSimple component (layers 1-4 only, no power text)
3. Build SkillReplacementSection with 12-sinner responsive grid
4. Build SkillExchangeModal with current EA display and exchange panes
5. Add constants and i18n keys
6. Integrate into PlannerMDNewPage with state management

## Execution Order

### Phase 1: Foundation (Constants & Types)

1. **constants.ts**: Add skill slot and EA default constants
   - Depends on: none
   - Enables: F1, F2, F3

2. **DeckTypes.ts**: Extend SinnerEquipment with skillEA field
   - Depends on: step 1
   - Enables: F1, F2, F3, F4

3. **i18n/EN/common.json**: Add i18n keys (skillReplacement, reset, exchange keys)
   - Depends on: none
   - Enables: F1

4. **i18n/{CN,JP,KR}/common.json**: Add same i18n keys
   - Depends on: step 3
   - Enables: F1

### Phase 2: Presentation Components

5. **SkillCardSimple.tsx**: Skill card with layers 1-4 only (no power text)
   - Depends on: none
   - Enables: F2

6. **SkillEADisplay.tsx**: Skill card with EA count badge
   - Depends on: step 5
   - Enables: F2

7. **SkillExchangePane.tsx**: Clickable exchange option (skill→skill or reset)
   - Depends on: step 5
   - Enables: F3

### Phase 3: Modal Component

8. **SkillExchangeModal.tsx**: Dialog with current EA (left) and exchange options (right)
   - Depends on: steps 5, 6, 7
   - Enables: F2, F3, F4

### Phase 4: Section Component

9. **SinnerSkillCard.tsx**: Clickable sinner card with identity + skills + EA
   - Depends on: steps 5, 6
   - Enables: F1, F2

10. **SkillReplacementSection.tsx**: Section with 12-sinner responsive grid
    - Depends on: step 9
    - Enables: F1, F2, E1

### Phase 5: Integration

11. **PlannerMDNewPage.tsx**: Add skillEA state, integrate section
    - Depends on: steps 2, 10
    - Enables: F1, F2, F3, F4, E1, E2, I1

12. **PlannerMDNewPage.tsx**: Add EA reset logic on identity change
    - Depends on: step 11
    - Enables: E2

### Phase 6: Refinement

13. **SkillExchangeModal.tsx**: Wire exchange validation (disable when EA <= 0)
    - Depends on: step 8
    - Enables: F3, E3

14. **SkillReplacementSection.tsx**: Apply responsive grid breakpoints
    - Depends on: step 10
    - Enables: E1

## Verification Checkpoints

- After step 4: i18n keys load correctly
- After step 8: Modal opens, skill cards display without power, exchange panes clickable
- After step 11: Section between Observation/Comprehensive, 12 sinners in grid
- After step 12: Identity change resets EA to 3,2,1
- After step 14: Grid responds to resize (6→4→3→2 columns)
- Final: Reset returns EA to 3,2,1; exchange updates EA correctly

## Rollback Strategy

- Steps 1-4: Non-breaking additions, revert individually
- Steps 5-10: New files, delete to rollback
- Step 11: Safe stopping point before this if issues arise
- Step 12: Can comment out useEffect while debugging
- Safe stopping points: After step 4, After step 10, After step 11
