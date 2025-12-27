# EGO Gift Observation - Learning Findings

## What Was Easy

- Extraction patterns clear: StartBuffCard cost display and EgoGiftMiniCard selection patterns accelerated scaffolding
- TanStack Query consistency: Observation hook followed proven patterns with 7-day staleTime
- Tailwind component extraction: Separating indicators into discrete components isolated concerns well
- Selection state management: Parent-level Set immutability pattern transferred directly from StartGiftSection

## What Was Challenging

- Image asset scaling complexity: Python pixel analysis for bgEnhanced created invalid Tailwind class w-18
- Hardcoded values spread: Color, image paths, empty state messages scattered instead of centralized
- Duplicate data transformation: Spec+i18n merge logic repeated in two components
- Responsive design deferred: Grid layout hardcoded without mobile/tablet breakpoints
- Enhancement assumptions: Fixed level 0 may require architectural changes for future features

## Key Learnings

- Component extraction must include concern extraction: colors, paths, constants in single locations
- Data transformation is reusable logic: spec+i18n merge should become documented utility
- Zod validation doesn't catch invalid Tailwind: Need separate design system validation step
- Lazy loading reduces initial cost: Hover-triggered fetch lightweight for 291-gift pool
- Set operations need immutability discipline: All mutations must create new Sets
- i18n keys grow organically: Specs should explicitly call out all user-facing text

## Spec-Driven Process Feedback

- Research.md mapping accurate: File organization and pattern identification matched implementation
- Plan.md execution order worked well: Sequential scaffolding → hook → integration flow correct
- Spec gaps discovered post-implementation: Grid responsiveness, constants, duplication only in review
- High-level requirements clear but implementation details need tighter definition

## Pattern Recommendations

- Document Data Transformation Utilities: spec+i18n merge into reusable hooks
- Add Constants Checklist: Establish constants.ts entries before component extraction
- Formalize Responsive Component Guidelines: Grid breakpoints, Tailwind class validation
- Create i18n Coverage Matrix: Pre-assign keys in specs before implementation

## Next Time

- Pre-review constants sweep: Audit hex colors, paths, limits before formal review
- Extract transformations early: Create util files immediately when patterns emerge
- Validate design system assumptions: Invalid Tailwind classes should fail in lint/build
- Spec template improvements: Add Responsive Design and i18n Strings sections
- Two-phase review: First for architecture, second for hardcoding violations
