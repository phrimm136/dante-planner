# Code Review: Create Planner Page

## Feedback on Code

- Constants and types well-organized with proper TypeScript type exports
- Route registration follows existing patterns in router.tsx correctly
- i18n keys properly structured and added to all language files
- UTF-8 byte length validation implemented correctly for title input
- KeywordSelector component inline in page file rather than extracted to components directory

## Areas for Improvement

1. **Component extraction**: KeywordSelector is 100+ lines defined inline in page file, reducing readability and preventing reuse elsewhere
2. **Missing accessibility**: KeywordSelector lacks keyboard navigation, aria labels, and focus management for the panel toggle
3. **Hardcoded "Done" text**: The Done button in KeywordSelector uses hardcoded string instead of i18n translation key
4. **No click-outside handler**: Selector panel stays open until user clicks Done, may cause confusion
5. **Missing translations**: JP/KR/CN translations left empty, creating inconsistent user experience

## Suggestions

1. Extract KeywordSelector to a reusable component in components/common or components/planner directory
2. Add click-outside detection to auto-close the selector panel using a ref-based approach
3. Consider grouping keywords visually (status effects vs sins) in the selector panel for clarity
4. Add aria-expanded, role="listbox", and keyboard event handlers for accessibility compliance
5. Create a shared Input component wrapping the HTML input with consistent styling instead of inline Tailwind classes
