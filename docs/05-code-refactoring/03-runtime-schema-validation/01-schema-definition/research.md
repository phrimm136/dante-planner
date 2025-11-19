# Research: Schema Definition

## Overview of Codebase

- Zod library not installed as direct dependency only appearing in yarn.lock from transitive dependencies requiring explicit installation
- No existing runtime validation anywhere in codebase with widespread use of type casting via as operator
- IdentityData has complex nested structure with SkillsData containing four skill arrays each with SkillData objects and nested upties
- EGOData includes EGORank enum-like union type and optional corrosion skill requiring conditional validation logic
- EGOGiftData simplest structure with flat properties and basic arrays making validation straightforward
- All entity types have parallel i18n structures mirroring data types requiring separate schema definitions
- Upties use hardcoded string keys three and four as Record keys needing enum validation for type safety
- Optional fields exist in PassiveData atkType in SkillData and corrosion in EGOData requiring careful schema definitions
- Tuple type for resist field with exactly three numbers for slash pierce blunt damage requiring tuple validation
- Dynamic imports load JSON data via import statements in hooks with no validation at load time
- useEntityDetailData and useEntityListData merge data and i18n using type casts assuming structure matches interfaces
- Constants file uses as const pattern with derived types suggesting similar pattern possible for validation schemas
- Type guards completely absent from codebase with no runtime checks for data structure correctness
- Error handling exists for network failures but not for data structure mismatches or invalid JSON shape
- Record types with any used extensively in data loading hooks indicating flexible but unvalidated structure assumptions
- String key records for upties threadspins and other nested structures need validation preventing invalid key usage
- Array fields like stagger resistances and keywords have no min or max length constraints enforced at runtime

## Codebase Structure

- Type definitions centralized in frontend/src/types/ directory with separate files per entity type organization
- Schemas should mirror this organization creating frontend/src/schemas/ directory parallel to types directory
- IdentityTypes.ts exports Identity IdentityData IdentityI18n SkillsData SkillData and nested interfaces
- EGOTypes.ts exports EGO EGOData EGOI18n EGORank and threadspins interfaces
- EGOGiftTypes.ts exports EGOGift EGOGiftData EGOGiftI18n and spec list Record types
- Hooks located at frontend/src/hooks/ including useEntityDetailData and useEntityListData for data fetching
- Lib directory at frontend/src/lib/ contains utilities queryClient router and constants appropriate for validation helpers
- Static data files loaded from static/data/ and static/i18n/ directories via dynamic imports in query functions
- Each entity type has spec list JSON i18n name list JSON and individual entity detail JSON files
- Routes at frontend/src/routes/ consume hook data expecting validated types but no checks currently implemented
- Query key factories in hooks use hierarchical structure for caching providing validation insertion points

## Gotchas and Pitfalls

- Installing zod as new dependency requires yarn add zod and ensuring version compatibility with React and TypeScript versions
- Upties use string literal keys three and four not numeric keys requiring z.object with literal key validation not z.record
- Optional fields must use z.optional() not TypeScript question mark syntax requiring careful conversion from interfaces
- Tuple resist field needs z.tuple([z.number(), z.number(), z.number()]) not z.array() to enforce exact length
- Skills structure has four separate named properties not array requiring z.object with four array fields not z.record
- EGORank union must use z.enum() with all five values explicitly listed matching TypeScript union type exactly
- Record types with string keys need z.record(z.string(), schema) but upties need specific literal keys instead
- i18n schemas must match data schemas structurally but all fields become strings requiring duplicate schema definitions
- Optional corrosion skill in EGO means awakening required corrosion optional needing careful schema structure
- Dynamic imports return unknown requiring schema.parse() wrapping to validate and return typed data safely
- Type casting currently used extensively means adding validation requires updating all import sites and hooks
- Merging data and i18n happens after both loaded meaning both need validation before merge operation
- Array length validation missing for resistances costs and stagger arrays needing min/max constraints added
- Nested array structures like SkillData arrays inside SkillsData require nested z.array(z.object()) schemas
- Schema changes must stay synchronized with interface changes requiring documentation and possibly shared source
