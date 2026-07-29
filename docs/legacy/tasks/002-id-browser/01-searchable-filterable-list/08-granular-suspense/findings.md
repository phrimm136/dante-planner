# Learning Reflection: Granular Suspense Implementation

## What Was Easy

- Reference implementation existed - EGO page patterns provided clear template
- Type patterns were straightforward - Spec-only types consistent with existing patterns
- Deferred hook pattern was reusable - Copy-paste-adapt workflow effective
- Suspense architecture was stable - Card-level boundaries isolated from page-level

## What Was Challenging

- Multi-line name handling - Korean names required domain-specific line splitting
- DRY violation detection - Indirect hook calls created unnecessary indirection
- Undefined field access - Alt attribute referenced removed field on spec-only type
- Search UX during load - Empty results while i18n loads is correct but non-obvious

## Key Learnings

- Spec-only types unlock Suspense control - Separating data flow gives granular control
- Reference implementations need domain extension - 80% copy-paste, 20% domain adaptation
- Deferred hooks return empty, not undefined - Use EMPTY constant with ?? operator
- Grid-level suspense is the bottleneck - Keeping grid mounted controls visibility
- Type consistency matters - Naming inconsistencies create cognitive friction
- Query key factories essential - Distinct keys prevent cache collisions

## Spec-Driven Process Feedback

- Research.md was accurate - All 7 required files identified correctly
- Pattern enforcement table was critical - MUST-READ order prevented mistakes
- Testing guidelines were comprehensive - Manual tests caught multi-line issue early
- Gap analysis was overspecified - More granular breakdown would help estimates

## Pattern Recommendations

- Document deferred hook pattern in frontend/CLAUDE.md with constraints
- Add spec-only type naming convention to skill documentation
- Create multi-line name utility in lib/textUtils.ts
- Document card Suspense boundaries in fe-component skill

## Next Time

- Prioritize domain research before pattern adoption
- Validate type field assumptions during planning phase
- Add intermediate TypeScript verification after transcription
- Plan DRY review time when creating multiple similar hooks
