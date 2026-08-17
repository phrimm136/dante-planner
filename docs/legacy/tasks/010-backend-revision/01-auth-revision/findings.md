# Auth Revision Learning Reflection

## What Was Easy

- Interface Segregation: TokenGenerator/TokenValidator split had clear responsibility boundaries
- Strategy Pattern: OAuthProvider + registry aligned with Spring bean auto-discovery
- Bottom-Up Execution: Utilities first created stable foundations for services
- Configuration Properties: @ConfigurationProperties + @Validated enabled fail-fast startup validation
- Test Coverage: Pattern consistency made 80/80 tests predictable

## What Was Challenging

- TokenBlacklistService exposed test-only methods (size, clear), violating ISP
- JwtAuthenticationFilter writes JSON directly instead of delegating to GlobalExceptionHandler
- IP extraction duplicated across 3 AuthController endpoints
- Blacklist eviction logic became overly defensive and complex
- OAuth null checks felt repetitive but were necessary for external API responses

## Key Learnings

- Value objects (TokenClaims, OAuthTokens) prevent token data leaking across boundaries
- @PostConstruct config validation catches issues at startup, not runtime
- Registry pattern enables adding providers without controller changes
- Facade pattern justified when flow orchestration spans 3+ services
- Security audit logging (IP, URI, User-Agent) essential for debugging auth failures
- Interface segregation keeps filter focused on validation, not generation

## Spec-Driven Process Feedback

- Research mapping was accurate: all 11 requirements implemented as specified
- 5-phase bottom-up order worked; no blocking dependencies discovered
- Gap analysis correctly identified 19 new + 6 modified + 2 deleted files
- Manual checkpoints caught 4 issues before full test suite
- Missing: Spec assumed single-server but didn't define Redis abstraction path

## Pattern Recommendations

- Add security audit logging pattern to be-security skill (IP + URI + User-Agent)
- Document immutable value objects in be-service skill (TokenClaims, OAuthUserInfo)
- Anti-pattern: Test-only methods exposed in production API—use interface segregation
- Anti-pattern: Manual JSON in filters—delegate to GlobalExceptionHandler or shared builder

## Next Time

- Extract utilities at first duplication, not after 3+ repetitions
- Define cache abstraction before implementation to avoid scaling debt
- Add concurrent race condition tests to plan checkpoints
- Standardize null-handling policy for external APIs at project start
- Extend config validation to OAuthProperties (client secrets, scopes)
