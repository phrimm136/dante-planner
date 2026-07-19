# Phase 05 (local-tdd)

## Rows
- abevent-card-lazy-async: frontend/src/pages/abEvent/components/__tests__/AbEventCard.test.tsx#renders-lazy-async-image — it renders
- themepack-card-lazy-async: frontend/src/pages/themePack/components/__tests__/ThemePackCard.test.tsx#renders-lazy-async-image — it renders

## Touches
- frontend/src/pages/abEvent/components/AbEventCard.tsx
- frontend/src/pages/themePack/components/ThemePackCard.tsx

## Validation
- `yarn --cwd frontend vitest run src/pages/abEvent src/pages/themePack`

## Seams (drives)
- AbEventCard render -> img[loading=lazy][decoding=async]
- ThemePackCard render -> img[loading=lazy][decoding=async]

## DECISION SLOTS
SLOT ordering-rationale: Two independent, identical-shaped card edits (AbEventCard, then ThemePackCard). Order is irrelevant — no shared state; each is a two-attribute add mirroring the existing IdentityCard/EGOCard lazy pattern.
SLOT batch-boundary: Both rows land in ONE red batch — the same assertion on two components, sharing the vitest run (`src/pages/abEvent src/pages/themePack`), no interdependency. One red → one green.
SLOT fallback-scope: No stub surface. If stopped after one card, the other is an independent trivial follow-up; each card edit is atomic, so there is no partial-state risk.
