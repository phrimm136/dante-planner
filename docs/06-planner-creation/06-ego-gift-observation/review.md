# EGO Gift Observation - Code Quality Review

## Spec-Driven Compliance

- Spec-to-Code Mapping followed: All indicator components extracted as separate units matching spec
- Spec-to-Pattern violation: StarlightCostDisplay hardcodes #f8c200 color instead of using constants
- Technical Constraints respected: 7-day staleTime, useSuspenseQuery, Zod validation, 3-gift max
- Execution Order followed: Steps completed in correct sequence from plan.md
- Plan deviation: EGOGiftCardBackground uses w-18 h-18 which is invalid Tailwind class
- Missing i18n: EGOGiftSelectionList hardcodes empty state message

## What Went Well

- Component extraction properly applied with SRP for Background/Tier/Enhancement/Keyword
- Zod validation pipeline complete with strict() mode and safeParse
- Universal selection pattern: EGOGiftSelectionList designed for reuse
- State immutability: All Set operations create new instances for re-renders
- Lazy loading preserved for hover-triggered description loading

## Code Quality Issues

- [HIGH] Invalid Tailwind class w-18 h-18 in EGOGiftCardBackground - will render incorrectly
- [HIGH] Hardcoded #f8c200 color in StarlightCostDisplay violates constants rule
- [MEDIUM] Duplicate spec+i18n merge logic in ObservationSection and ObservationSelection
- [MEDIUM] Missing i18n for empty state message in EGOGiftSelectionList
- [MEDIUM] Hardcoded /images/UI/egoGift/onSelect.webp path should use assetPaths helper
- [LOW] Inefficient Set iteration converts to array just to slice first 3 items
- [LOW] Missing null check for tier tag with non-null assertion operator

## Technical Debt Introduced

- Data transformation duplication: Spec+i18n merge in 2 files needs extraction
- Hardcoded grid ratio: grid-cols-10 layout not responsive for mobile/tablet
- Enhancement fixed at 0: Future enhancement in observation requires refactoring

## Backlog Items

- Extract mergeSpecWithI18n utility or useEGOGiftListMerge hook
- Add missing i18n keys: noMatchingGifts, fix hardcoded strings
- Create getEGOGiftSelectionOverlayPath in assetPaths.ts
- Add responsive grid breakpoints for sm/md/lg viewports
- Fix w-18 h-18 to valid Tailwind class before production
