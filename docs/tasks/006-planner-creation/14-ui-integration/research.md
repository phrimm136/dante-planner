# Research: PlannerSection Unified Component

## Spec Ambiguities
**NONE** - Spec is clear and unambiguous.

---

## Spec-to-Code Mapping

| Requirement | Target File | Modification |
|-------------|-------------|--------------|
| Create PlannerSection | `components/common/PlannerSection.tsx` | NEW |
| Migrate StartBuffSection | `components/startBuff/StartBuffSection.tsx` | Remove label + wrapper |
| Migrate StartGiftSection | `components/startGift/StartGiftSection.tsx` | Remove h2, move counter |
| Migrate EGOGiftObservation | `components/egoGift/EGOGiftObservationSection.tsx` | Replace SectionContainer, move caption |
| Migrate EGOGiftComprehensive | `components/egoGift/EGOGiftComprehensiveListSection.tsx` | Replace SectionContainer |
| Migrate SkillReplacement | `components/skillReplacement/SkillReplacementSection.tsx` | Remove inline container + h2 |
| Migrate DeckBuilder | `components/deckBuilder/DeckBuilder.tsx` | Wrap in PlannerSection |
| Update Floor Themes | `routes/PlannerMDNewPage.tsx` | Wrap h2 + loop in PlannerSection |
| Deprecate SectionContainer | `components/common/SectionContainer.tsx` | Add @deprecated JSDoc |

---

## Pattern Enforcement

| New/Modified File | MUST Read First | Pattern to Copy |
|-------------------|-----------------|-----------------|
| PlannerSection.tsx | SectionContainer.tsx | Props interface, JSDoc |
| PlannerSection.tsx | lib/constants.ts:260-301 | SECTION_STYLES tokens |
| All migrations | Current implementation of each | State logic preserved |

---

## Existing Utilities (USE THESE)

| Category | Location | Use |
|----------|----------|-----|
| Section container style | SECTION_STYLES.container | `bg-muted border border-border rounded-md p-6` |
| Header typography | SECTION_STYLES.TEXT.header | `text-xl font-semibold` |
| Section spacing | SECTION_STYLES.SPACING.content | `space-y-4` |

---

## Gap Analysis

- **Missing**: PlannerSection component
- **Modify**: 7 components + 1 page
- **Reuse**: All SECTION_STYLES constants, all state logic, all i18n keys

---

## Testing

### Manual UI Tests
- All sections render with identical h2 + bordered container
- StartGift counter inside container (not header)
- EGOGiftObservation cost inside container (not header)
- Floor themes: 15 rows inside single section
- Dark mode consistency
- Mobile responsive

### Automated Tests
- PlannerSection renders section > h2 + container structure
- All components use PlannerSection as wrapper
- SectionContainer has @deprecated tag
- No caption props remain

---

## Technical Constraints
- PlannerSection: Zero dependencies (React only)
- Zero state changes in migrations
- All i18n keys unchanged
- Use SECTION_STYLES constants only
- SSR compatible (no browser APIs)
