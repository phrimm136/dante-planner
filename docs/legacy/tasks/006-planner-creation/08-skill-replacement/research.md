# Skill Replacement Feature - Research

## Clarifications Resolved
- **EA State**: Per-Sinner in DeckState, resets on identity swap
- **Exchange Constraints**: EA as resource (costs 1 per exchange, multiple allowed until depleted)
- **Pane Type**: Modal Dialog (ESC/backdrop closes)
- **Grid Layout**: Responsive (6→4→3→2 columns)
- **Uptie Level**: Use deck's configured uptie for skill display
- **Card Display**: Image composition only (layers 1-4, no text/name)

## Spec-to-Code Mapping
- Section placement → `PlannerMDNewPage.tsx` between Observation and Comprehensive sections
- 12 sinner grid → Reference `SinnerGrid.tsx` responsive pattern
- Sinner iteration → Use `SINNERS` constant from `constants.ts`
- Skill image without power → Extract layers 1-4 from `SkillImageComposite.tsx`
- Modal dialog → Use shadcn `Dialog` (pattern in `DeckBuilder.tsx`)
- DeckState persistence → Extend `DeckTypes.ts` with skill EA per sinner
- Exchange validation → Check EA > 0 before enabling exchange
- i18n keys → Add to `/static/i18n/{LANG}/common.json`

## Spec-to-Pattern Mapping
- Section component → `EGOGiftObservationSection.tsx` suspended component pattern
- Grid container → `SinnerGrid.tsx` Tailwind responsive breakpoints
- Modal pattern → `DeckBuilder.tsx` Dialog structure
- Sinner card item → `SinnerDeckCard.tsx` onClick + state styling
- Image layers → `SkillImageComposite.tsx` absolute positioned divs
- Data hook → `useIdentityDetailData.ts` useSuspenseQuery + Zod
- State management → PlannerMDNewPage useState pattern
- Constants → Check/add to `constants.ts` before hardcoding

## Gap Analysis
**Missing (needs creation):**
- SkillReplacementSection component (main container)
- SkillExchangeModal component (dialog with exchange logic)
- Skill EA state in DeckTypes.ts (extend SinnerEquipment)
- Skill EA display component
- Exchange operation types/constants
- i18n keys for all 4 languages
- Layer extraction utility (render only layers 1-4)

**Needs modification:**
- `DeckTypes.ts` - add skillEA field to SinnerEquipment
- `PlannerMDNewPage.tsx` - add state + section JSX
- `constants.ts` - add skill slot/EA constants

**Can reuse:**
- Dialog from shadcn/ui, SinnerGrid layout, SkillImageComposite base
- State patterns from EGOGiftObservationSection
- useSuspenseQuery pattern, i18n pattern

## Technical Constraints
- EA per-sinner stored in DeckState, resets on identity change
- No hardcoded skill slot numbers - use constants
- Absolute positioning CSS for image layers (w-32 h-32)
- shadcn Dialog required for accessibility
- Responsive grid: 6→4→3→2 columns at breakpoints
- Zod validation mandatory for all JSON data
- Suspense wrapping required for useSuspenseQuery
- useSuspenseQuery only (no plain useQuery)
- i18n keys follow `pages.plannerMD.{featureName}` pattern
- No prop drilling beyond 2 levels
- React Compiler handles optimization (no manual memo/useCallback)

## Integration Points
- Entry: `PlannerMDNewPage.tsx` line ~271
- State: useState in main page, pass to section
- DeckState: Extend SinnerEquipment interface
- Images: Use `getSkillImagePath()` from assetPaths.ts
- Dialog: Reuse shadcn pattern from DeckBuilder
- Translations: Add keys before writing components
