# Learning Reflection: Settings Page - Username Keyword Change

## What Was Easy

- Pattern reuse from AssociationDropdown enabled copy-then-adapt approach
- Backend structure clarity - UsernameConfig already had keyword definitions and validator
- Spec-to-code mapping in research.md was 100% accurate, no backtracking needed
- Authentication gate aligned with existing conditional render patterns
- Test writing flowed naturally from established RTL/Jest patterns

## What Was Challenging

- OAuth duplication only discovered during component build, not in spec phase
- i18n key uncertainty - unsure if association.* keys existed in all locales
- Public endpoint security gap required manual SecurityConfig verification
- Cache invalidation timing between mutation success, toast, and Header refresh

## Key Learnings

- Backend-first execution order prevented frontend-driven rewrites
- Pattern detection (studying existing components) saves design time
- Validation at boundaries - backend validation cheaper than duplicating in frontend
- Gated content vs redirect - public page with conditional auth is better UX
- Live preview with local state decouples UI selection from server commits

## Spec-Driven Process Feedback

- Research.md mapping was fully accurate - every requirement matched implementation
- Plan execution order was optimal - 18 steps never required reordering
- Spec ambiguities resolved proactively in research phase, not during coding
- OAuth duplication and i18n gaps only emerged at code review - could catch earlier
- Test requirements were implied but not explicit in spec

## Pattern Recommendations

- Add useGoogleLogin hook pattern to fe-data skill (OAuth state machine)
- Document public-with-gated-content pattern in fe-routing (conditional auth check)
- Add single-select dropdown pattern with live preview variant to fe-component
- Flag OAuth duplication anti-pattern - suggest hook extraction during spec review

## Next Time

- Add OAuth deduplication check during research phase
- Verify i18n keys exist in all locales before DTO design
- Include security review step for public API endpoints in research.md
- Specify expected test count/coverage targets upfront in spec
- Scan existing components for similar patterns before implementation
