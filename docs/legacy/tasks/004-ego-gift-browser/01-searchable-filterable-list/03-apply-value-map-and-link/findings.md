# Findings and Reflections: Apply Value Map and Link

## Key Takeaways

- Creating shared getKeywordDisplayName utility function straightforward improving code reuse across components eliminating duplication
- Link component integration followed established IdentityCard and EGOCard patterns enabling rapid implementation with minimal debugging
- IconFilter extension with optional getLabel prop maintained backward compatibility preventing breaking changes to existing consumers
- Keyword icon absolute positioning required careful CSS consideration ensuring proper placement without affecting card layout or responsiveness
- Text fallback using onError DOM manipulation pragmatic solution but violates React declarative paradigm creating technical debt
- Removing bracketed keywords from keywordMatch.json simplified data structure without breaking search functionality confirming safe refactoring
- Build completed successfully without TypeScript errors demonstrating strong type safety and proper interface definitions throughout changes

## Things to Watch

- DOM manipulation in onError handler bypasses React rendering cycle potentially causing hydration mismatches or concurrent rendering issues in future
- Hardcoded EN keywordMatch.json import in utils.ts prevents runtime language switching requiring component reload for internationalization breaking user experience
- Layout shift when icons fail loading due to inconsistent dimensions between img and span fallback disrupting visual stability especially noticeable with multiple cards
- Absolute positioned keyword icons could overlap with long card names lacking minimum height constraint or content padding considerations
- Missing loading states for icons causing brief flicker of broken image before onError triggers particularly problematic on slow network connections

## Next Steps

- Refactor onError handler to React state pattern tracking failed icons declaratively preventing DOM manipulation and enabling proper lifecycle management
- Extract keyword mapping to useKeywordDisplayName hook consuming i18n context supporting dynamic language switching without component reload
- Add icon loading skeleton or placeholder maintaining consistent dimensions across loading error and success states eliminating layout shift
- Implement unit tests for getKeywordDisplayName covering edge cases like missing mappings and undefined input ensuring reliability as keyword list grows
- Define card minimum height or reserved keyword space preventing content overlap ensuring consistent layout across varying title lengths
