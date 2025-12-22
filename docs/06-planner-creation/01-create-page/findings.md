# Findings and Reflections

## Key Takeaways

- Route registration with TanStack Router is straightforward following existing patterns
- Creating combined constants from existing arrays requires careful type handling
- Custom multi-select components needed when existing IconFilter pattern doesn't fit UX needs
- UTF-8 byte length validation requires TextEncoder API for accurate counting
- Updating shared constants can cause cascading type errors in dependent files
- i18n structure is well-organized but JP/KR/CN translations need separate effort
- DropdownMenu from shadcn/ui works well for simple selection needs

## Things to Watch

- Icons for new keywords (Burst, Breath, Penetration, Hit) need verification in static assets
- KeywordSelector lacks click-outside handling which may confuse users
- KEYWORD_ORDER constant change from Keywordless to None affects multiple files
- Inline component definitions reduce reusability across future planner pages
- Missing accessibility features could cause compliance issues

## Next Steps

- Extract KeywordSelector to components/planner or components/common for reuse
- Verify all keyword icons exist and add missing ones to static/images
- Add click-outside hook to auto-close selector panel
- Complete translations for JP/KR/CN language files
- Add accessibility attributes before expanding planner functionality
