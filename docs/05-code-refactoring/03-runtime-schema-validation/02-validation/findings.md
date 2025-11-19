# Findings and Reflections: Runtime Validation Integration

## Key Takeaways

- Integrating runtime validation at React Query queryFn level proved effective pattern validating before cache preventing bad data propagation through application
- Zod safeParse API straightforward to use but API differs from expectations using issues property not errors requiring documentation consultation during implementation
- Environment-aware error formatting using import.meta.env.DEV provides good balance between developer debugging needs and user-facing error messages
- Generic type parameters in hooks became problematic after validation integration requiring removal and reintroduction of type assertions at component level defeating some type safety benefits
- Schema mapping pattern works well for routing entity types to correct schemas but resulted in duplication across four separate functions suggesting need for centralized configuration
- Removing unsafe type assertions from hooks achieved but component-level assertions reintroduced maintaining runtime safety while losing some compile-time type checking
- Build and type compilation succeeded demonstrating zod type inference can replace type assertions when properly configured

## Things to Watch

- Component type assertions reintroduced after hook calls compromise compile-time type safety allowing potential entity type mismatches that compiler cannot catch
- Validation utilities lack unit tests leaving critical error path including schema mapping and error formatting untested increasing risk of production errors
- Merged list data structure in useEntityListData receives no validation only input Records validated allowing malformed output if spec contains conflicting properties
- Schema mapping duplication across four functions requires updating multiple locations when adding new entity types creating maintenance burden and inconsistency risk
- Error message formatting uses any type for zod issue objects bypassing TypeScript checking when implementation could properly type issue structure

## Next Steps

- Restore generic type parameters to useEntityDetailData allowing proper type inference from entity type parameter maintaining type safety from validation through component usage
- Create comprehensive unit test suite for lib/validation.ts covering all schema mappings error formatting variations and validation success and failure paths
- Centralize schema mappings into single configuration object reducing duplication and making entity type additions require single mapping entry
- Implement list validation with element index context enabling error messages to report specific failing entity ID in large spec lists
- Import proper ZodIssue type or define interface replacing any type in error formatting ensuring type safety when accessing issue properties
