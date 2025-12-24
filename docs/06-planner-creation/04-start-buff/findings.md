# Findings and Reflections: Start Buff Section

## Key Takeaways

- **ID encoding scheme worked well**: Encoding baseId and enhancement level into a single number simplified state management and made selection logic straightforward
- **CSS border-image is powerful but brittle**: 9-slice scaling for button backgrounds required pixel-perfect measurements; any image changes will require manual recalibration
- **useLayoutEffect essential for measurement**: Standard useEffect caused visible flickering during text scaling; switching to useLayoutEffect eliminated the issue
- **Separating enhancement from selection state was crucial**: Initial design conflated the two, leading to confusing UX; separating them made each interaction predictable
- **Zod validation adds safety with minimal overhead**: Runtime validation for battleKeywords.json caught structure assumptions early and provided clear error messages
- **TanStack Query dynamic imports work smoothly**: Language-based JSON loading with query key factories enabled clean caching per locale
- **Translation pipeline complexity**: Multiple layers (i18n strings, color tags, placeholder replacement, keyword translation) required careful ordering

## Things to Watch

- **State synchronization risk**: Enhancement state exists in both StartBuffSection and StartBuffCard; external updates to selection may cause drift
- **Performance with larger datasets**: Array find operations run on every render; may need optimization if buff count increases significantly
- **Missing loading states for battleKeywords**: Hook returns data but isPending/isError not checked; could render with undefined data
- **Hardcoded dimensions**: Pixel values throughout components will break if card size or image assets change
- **Mixed language comments**: Korean comments in some files reduce accessibility for non-Korean speakers

## Next Steps

- Consider lifting enhancement state entirely to parent component using controlled pattern
- Create buff lookup Map at section level to avoid repeated array searches
- Add Suspense boundary or explicit loading state for battleKeywords fetch
- Extract magic numbers (slice values, dimensions) to named constants or config
- Standardize on English comments throughout codebase for consistency
