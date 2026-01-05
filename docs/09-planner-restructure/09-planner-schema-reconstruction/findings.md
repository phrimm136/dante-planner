# Learning Reflection: Planner Schema Reconstruction

## What Was Easy

- Discriminated union pattern was straightforward once two-step validation approach was established
- TypeScript type narrowing worked seamlessly with switch/if guards on config.type
- Database migration ENUM to VARCHAR was mechanical and low-risk
- Existing builder patterns in backend DTOs made adding plannerType field straightforward
- Test coverage achievable through schema validation tests

## What Was Challenging

- Zod limitation: discriminated unions cannot validate nested content based on discriminator in single schema
- Cascading type changes: moving category to config rippled through 12+ files
- Code review caught unsafe as casts without config.type guards
- Test fixture drift: plan specified updating non-existent test file
- Union narrowing sometimes required explicit casts even with discriminated unions

## Key Learnings

- Two-step validation pattern: separate base validation from type-dependent content validation
- Discriminated unions as extensibility: config layer creates clean extension point for new planner types
- Backend service as validation orchestrator: centralizing in isValidCategory() prevents duplication
- Type guards are essential: explicit config.type checks before casts prevent runtime errors
- Migration first not last: early database migration prevents late-stage surprises
- Content type discriminator: adding type field to content interfaces enables better validation errors

## Spec-Driven Process Feedback

- Research mapping was accurate: correctly identified all critical gaps
- Plan execution order worked well: foundation-first prevented downstream breakage
- One ambiguity: plan said "update test file" that didn't exist - should say "create or verify"
- Verification checkpoints crucial: tsc and mvnw compile after each phase caught errors early

## Pattern Recommendations

- Document two-step Zod validation pattern in fe-data skill for discriminated unions
- Add type guard anti-pattern documentation showing unsafe vs safe narrowing
- Config/metadata/content layering strategy is reusable beyond planners
- Consider Jackson @JsonTypeInfo for backend polymorphic DTO responses

## Next Time

- Require explicit "test exists" vs "create tests" language in plans
- Add cascading reference check as verification step after field restructuring
- Document Zod discriminated union limitation upfront in fe-data skill
- Create migration rollback SQL explicitly in migration comments
