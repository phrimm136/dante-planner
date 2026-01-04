# Findings: Gesellschaft Username Generation

## What Was Easy

- Time-decay weighting logic with LocalDate and ChronoUnit.DAYS was straightforward
- SecureRandom integration required no custom cryptography; 31-char safe set was explicit in spec
- i18n structure reuse from existing common.json made association.json files mechanical
- Collision handling via DataIntegrityViolationException with retry matched existing Spring patterns
- DTO updates using existing builder pattern were simple extensions

## What Was Challenging

- Test fixture churn: New NOT NULL fields broke 6+ test classes simultaneously
- Migration safety gap: V011 adds NOT NULL without DEFAULT, fails if users exist
- PublicPlannerResponse breaking change requires atomic backend+frontend deploy
- ISP violation in UsernameConfig: Interface doesn't cover all public methods

## Key Learnings

- Weighted pool should be cached at init, not rebuilt on every call
- 31^5 = 28.6M namespace makes infinite retry genuinely safe (add logging after N retries)
- i18n defaultValue pattern gracefully degrades missing translations without crashes
- Test fixture factory/builder would prevent cascading fixture failures
- Adding mandatory DTO fields creates backend-frontend coupling; consider wrapper pattern

## Spec-Driven Process Feedback

- Research mapping was accurate; no hidden dependencies discovered
- Plan execution order (DB → Services → API → Frontend → Tests) prevented circular deps
- Spec omission: Migration safety for existing users not addressed
- Association curation delay handled gracefully but needed explicit decision point

## Pattern Recommendations

- **be-service**: Add collision-retry pattern with DataIntegrityViolationException
- **fe-data**: Add i18n fallback with defaultValue in t() function
- **Anti-pattern**: Don't rebuild expensive pools per-call; cache at initialization
- **be-testing**: Add test fixture factory pattern for shared entities
- **Migration**: NOT NULL columns need DEFAULT or zero-user precondition

## Next Time

- Ask explicit migration questions: "Do users exist? What's the strategy?"
- Consider API versioning early to avoid breaking changes
- Cache weighted pools during @PostConstruct or constructor
- Create shared test fixture factories before adding required fields
- Incorporate review.md feedback into skill documentation
