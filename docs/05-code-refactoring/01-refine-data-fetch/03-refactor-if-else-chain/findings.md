# Findings and Reflections: Refactor if-else Chain

## Key Takeaways

- Configuration object pattern effectively eliminates if-else chains reducing new entity type implementation from four code locations to single configuration entry
- Externalizing staleTime to queryConfig.json enables operational adjustment without code deployment reducing friction for cache tuning
- Breaking changes like renaming egogift to egoGift require systematic verification of all usage sites but TypeScript type system catches most issues at compile time
- Type safety with 'as const satisfies' pattern provides both compile-time verification and literal type preservation enabling accurate path construction
- Selective file system changes based on actual structure prevents unnecessary modifications - only EN directory needed gift to egoGift rename since other languages lack entity-specific subdirectories
- Template literal dynamic imports with configuration-driven paths work correctly but lack validation that paths match actual file system until runtime
- Configuration split between external JSON and internal TypeScript constant creates coordination burden requiring developers to maintain consistency across two files

## Things to Watch

- Type assertions throughout queryFn implementations provide no runtime guarantees creating risk that JSON structure divergence from interfaces surfaces as cryptic render-time errors instead of clear load-time validation failures
- i18n staleTime remains hardcoded at 7 days in createI18nQueryOptions while data staleTime externalized creating configuration inconsistency and requiring code changes for i18n cache adjustments
- Incomplete language directory migration means CN, KR, JP directories await future entity-specific content without documented naming convention guidance risking divergent patterns when translations added
- ENTITY_CONFIG couples three separate concerns - file system paths, i18n paths, and caching strategy - increasing risk that changes to one concern unintentionally affect others
- Dynamic import path validation missing means configuration typos or mismatches with actual file system structure fail silently until query execution

## Next Steps

- Implement runtime schema validation using Zod or similar library to verify loaded JSON matches TypeScript interfaces at query time converting runtime type errors into explicit validation failures with actionable error messages
- Unify configuration strategy by extending queryConfig.json to include i18n staleTime and potentially path mappings making ENTITY_CONFIG derive from single external source instead of mixing external and internal configuration
- Document configuration pattern with inline comments explaining how to add new entity types including checklist of required steps covering type definitions, file system structure, and configuration entries
- Plan language directory migration strategy documenting expected naming convention for CN, KR, JP when entity-specific translations added potentially with automated validation preventing divergent patterns
- Consider configuration schema validation during build or startup catching configuration errors like typos or missing entries before runtime query failures occur
