# Findings and Reflections: Schema Definition

## Key Takeaways

- Zod schemas mirror TypeScript interfaces effectively with excellent type inference and runtime validation
- Strict mode with z.object().strict() successfully catches unexpected additional fields preventing data structure divergence
- Array length constraints straightforward using .length(7) for resistances and costs matching game mechanics
- Enum validation with z.enum() provides strong typing and prevents invalid string values like typos in sin types
- Shared schema extraction eliminated PassiveI18n duplication across Identity and EGO schemas reducing maintenance burden
- Comprehensive documentation in README.md crucial for team understanding of validation patterns and maintenance procedures
- Build verification after schema changes ensures no TypeScript compilation errors or module resolution issues

## Things to Watch

- Manual synchronization between TypeScript interfaces and schemas remains error-prone without automated ts-to-zod tooling
- Upties and threadspins using literal string keys require z.object() with explicit properties not z.record()
- Performance impact of strict validation on large datasets not yet measured may need optimization for production
- Schemas defined but not yet integrated into data loading hooks requiring separate integration task
- Record key validation missing allowing arbitrary ID strings rather than enforcing expected format patterns

## Next Steps

- Integrate schemas into useEntityDetailData and useEntityListData hooks with proper error handling
- Measure validation performance on large datasets and implement caching if needed
- Create validation utility functions for common error formatting and result transformation patterns
- Consider installing ts-to-zod for automated schema generation maintaining synchronization with interfaces
- Implement record key validation for ID fields enforcing expected alphanumeric or numeric string patterns
