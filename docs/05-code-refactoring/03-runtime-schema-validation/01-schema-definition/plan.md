# Implementation Plan: Schema Definition

## Clarifications Resolved

- Schemas use strict validation rejecting additional fields ensuring data matches expected structure exactly
- Array length constraints added for type/sin resistances exactly seven elements and sin costs exactly seven elements but not stagger allowing variable length
- Validation errors collected using safeParse returning comprehensive ZodError object with all validation failures for debugging
- TypeScript interfaces remain source of truth with schemas maintained separately avoiding circular type inference issues
- Schema and interface synchronization enforced via shared source generation approach using tools to derive schemas from interfaces automatically

## Task Overview

Define comprehensive zod schemas for all entity types matching existing TypeScript interfaces providing runtime validation with strict mode enabled. Setup automated schema generation from TypeScript interfaces using shared source generation approach. Create schemas directory structure parallel to types directory with separate schema files for Identity EGO and EGOGift covering both data and i18n structures. Schemas must validate all required fields optional fields nested structures enums arrays with length constraints tuples and record types collecting all validation errors for comprehensive debugging.

## Steps to Implementation

1. **Install zod package**: Add zod as direct dependency via yarn ensuring version compatibility with project TypeScript and React versions.

2. **Create schemas directory structure**: Create frontend/src/schemas/ directory with index.ts for exports mirroring types directory organization.

3. **Define Identity schemas**: Create IdentitySchemas.ts with schemas for Identity IdentityData IdentityI18n covering nested skills upties passives with proper optional field handling.

4. **Define EGO schemas**: Create EGOSchemas.ts with schemas for EGO EGOData EGOI18n including EGORank enum validation optional corrosion skill and threadspins structure.

5. **Define EGOGift schemas**: Create EGOGiftSchemas.ts with schemas for EGOGift EGOGiftData EGOGiftI18n covering simple flat structures with keyword and themePack arrays.

6. **Handle nested and special structures**: Implement tuple validation for resist field literal key validation for upties Record types for spec lists proper enum validation for Sin and Rank types and array length constraints for resistances seven elements and costs seven elements.

7. **Create schema utilities**: Add utility functions for common validation patterns error formatting and schema composition patterns in schemas/utils.ts if needed.

8. **Setup shared source generation tooling**: Configure typescript-to-zod or similar tool to automatically generate zod schemas from TypeScript interfaces establishing single source of truth and automated synchronization.

9. **Document schema usage and maintenance**: Add comments explaining schema structure maintenance requirements automated generation workflow and relationship to TypeScript interfaces ensuring future developers follow correct update process.

## Success Criteria

- Zod package installed in package.json as direct dependency with appropriate version specified
- Schemas directory created at frontend/src/schemas/ with index.ts exporting all schemas
- All entity data interfaces have corresponding zod schemas including Identity IdentityData EGOData EGOGift with complete field coverage
- All i18n interfaces have corresponding schemas including IdentityI18n EGOI18n EGOGiftI18n mirroring data structure
- Nested structures validated including SkillsData SkillData UptieData PassiveData EGOThreadspinData with proper nesting
- Enum and union types validated including EGORank using z.enum() with all literal values specified
- Optional fields properly handled using z.optional() for atkType passiveSin passiveEA passiveType corrosion
- Tuple validation implemented for resist field enforcing exactly three number elements
- Array length constraints enforced for resistances exactly seven elements and costs exactly seven elements but not stagger
- Strict validation enabled using z.object().strict() rejecting additional fields not defined in schema
- Record types with literal keys validated for upties three and four using z.object() with specific keys not z.record()
- Shared source generation tooling configured to automatically generate schemas from TypeScript interfaces
- Schema files organized and exported from schemas/index.ts for clean imports throughout codebase
- Documentation includes automated generation workflow and maintenance process for keeping schemas synchronized with interfaces

## Assumptions Made

- Schemas use strict validation with z.object().strict() rejecting any additional fields ensuring JSON data precisely matches interface definitions
- Array length constraints enforced for resistances and costs with exactly seven elements matching game mechanics but stagger remains variable length
- Validation uses safeParse pattern collecting all errors in ZodError object enabling comprehensive error reporting rather than failing on first error
- TypeScript interfaces remain authoritative source with schemas derived via shared source generation tools automating synchronization
- Schemas will be defined but not yet integrated into hooks deferring actual validation implementation and error handling to separate integration task
- Shared source generation approach requires tooling setup to automatically generate schemas from TypeScript interfaces maintaining single source of truth
