# Phase 04 (infra)

## Rows
- thumbnails-emitted-and-sized: static/scripts/pipeline.py:validation#thumbnails — the pipeline validation step runs
- shared-assets-resolves-thumb: frontend/src/shared/assets/__tests__/thumb.test.ts#resolves-thumb-variant-for-grid-art — the thumb accessor is called
- grid-cards-consume-thumb: frontend/src/pages/ego/components/__tests__/EGOCard.test.tsx#grid-card-uses-thumb-accessor — it renders its art

## Touches
- static/scripts/**
- static/images/**
- frontend/src/shared/assets/**
- frontend/src/pages/ego/components/EGOCard.tsx
- frontend/src/pages/identity/components/IdentityCard.tsx
- frontend/src/pages/abEvent/components/AbEventCard.tsx
- frontend/src/pages/themePack/components/ThemePackCard.tsx

## Validation
- `yarn --cwd frontend vitest run src/shared/assets src/pages/ego`

## Seams (drives)
- grid card render -> src from the thumb accessor, not the original-art helper
- shared/assets thumb accessor -> resolves the thumb variant path for grid art, original for detail
- thumbnail step -> a webp thumb per grid-consumed art id, each >= 2x its consuming grid's largest slot

## DECISION SLOTS
SLOT ordering-rationale: D8 ordering is load-bearing — pipeline before rendering. (1) `thumbnails-emitted-and-sized` (static resize step + validation assertion) FIRST, gated by spike `probe-thumbnail-resize`; (2) `shared-assets-resolves-thumb` (FE accessor mapping art id → emitted thumb path); (3) `grid-cards-consume-thumb` (grid cards switch to the accessor). The FE rows depend on the thumbs existing and the accessor resolving, so they follow the pipeline row; rows 2–3 share the `@/shared/assets` fixture.
SLOT batch-boundary: The static row is a separate cycle (different runner — the pipeline validation step, gated by the resize spike) and must land green before the FE rows, per D8. The two FE rows (accessor, then consumers) form one vitest red batch (`src/shared/assets src/pages/ego`) since the card test depends on the accessor. Sequence: pipeline cycle → FE red batch.
SLOT fallback-scope: No declared stub surface (the accessor is an addition to an existing module, not a new-file stub). If `probe-thumbnail-resize` is dead, stop before the FE rows: ship the accessor returning the original path as a documented no-thumb fallback (grids stay correct but heavy) and raise the pipeline row as an amendment. testExceptions permits `static/**/fixtures/*` for a fixture thumb.
