# Implementation Plan: Multi-Purpose Search Bar

## Task Overview

Implement a functional search bar that filters identities by name, keywords, and traits using multi-language natural language input. The search must support partial matching where users type natural language text that gets mapped to bracketed keywords in the data. Search results combine with existing sinner and keyword filters using AND logic, with debouncing for live search behavior.

## Clarifications Needed

- None at this time

## Assumptions Made

- Search uses OR logic across categories: matches name OR keyword OR trait
- Search results AND with existing filters: must pass search AND sinner filter AND keyword filter
- Empty search shows all items matching current filters
- Case-insensitive partial matching for all search types
- 500ms debounce delay for live search (configurable via constant)
- Language-wise mapping files loaded based on current language setting (Korean mappings for Korean language, etc.)
- Input trimmed and whitespace normalized before matching
- Missing mapping files for a language means search is unavailable for that language

## Steps to Implementation

1. **Add search state to IdentityPage**: Create searchQuery state using useState and pass to IdentitySearchBar and IdentityList components via props

2. **Load language-specific mapping data**: Import identityNameList, keywordMatch, and traitMatch JSON files based on current i18n language setting

3. **Create reverse mapping logic**: Build inverse maps from natural language to bracketed notation for keywords and traits to enable user input lookup

4. **Implement search input with debouncing**: Create controlled input component with onChange handler that debounces user input (500ms configurable constant) before updating search state

5. **Add search matching logic to IdentityList**: Extend filter function to check if identity matches search query by name, keywords, or traits using partial string matching

6. **Combine search with existing filters**: Ensure search results respect active sinner and keyword filters using AND logic between all filter types

7. **Add placeholder text**: Use i18n translation for search input placeholder indicating searchable fields (name, keyword, trait)

8. **Handle input normalization**: Implement case-insensitive matching with toLowerCase and whitespace trimming on user input before matching

9. **Type definitions**: Add proper TypeScript interfaces for search props, mapping data structures, and component interfaces

10. **Test compilation**: Run TypeScript compiler to verify all types are correct and no errors exist

## Success Criteria

- Search input accepts text and filters identity list in real-time with 500ms debounce
- Partial matching works: typing "rup" shows identities with rupture keyword
- Name search works via identityNameList direct lookup
- Keyword search works via keywordMatch reverse mapping to bracketed values
- Trait search works via traitMatch reverse mapping to bracketed values
- Search combines with sinner and keyword filters using AND logic
- Empty search shows all identities matching current filters
- Case-insensitive matching for all search types with whitespace handling
- Language-specific mappings load based on i18n language setting
- TypeScript compilation passes with no errors
- Debounce delay configurable via constant (default 500ms)
