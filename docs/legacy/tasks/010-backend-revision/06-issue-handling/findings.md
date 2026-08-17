# Security Issue Handling - Learning Reflection

## What Was Easy

- Configuration via properties - Spring's ConfigurationProperties mechanism is straightforward
- Leveraging existing frameworks - ObjectMapper and Spring Security header DSL already available
- Static utility pattern - ClientIpResolver followed existing ViewerHashUtil precedent
- Test coverage - Security tests built on familiar mocking patterns
- Review-driven fixes - Self-correcting via code review was efficient

## What Was Challenging

- IPv6 regex completeness - RFC 4291 has edge cases; InetAddress.getByName() would be more robust
- Multi-proxy X-Forwarded-For handling - Comma-separated IPs added parsing complexity
- SameSite cookie risk perception - Changing Strict to Lax felt unsafe until GET-only analysis
- CORS header scope - Required tracing frontend requests, not just theoretical security
- Spec ambiguity on token refresh IP validation - Not obvious this breaks VPN/mobile UX

## Key Learnings

- Trusted proxy pattern requires defensive programming - validate at startup, reject untrusted sources
- ObjectMapper handles JSON escaping automatically - never concatenate JSON strings manually
- SameSite=Lax is safe for read-only GET endpoints - significant UX win
- IP resolution should fall back gracefully - ignore untrusted headers, don't fail
- HSTS with includeSubDomains and 1-year max-age is industry standard

## Spec-Driven Process Feedback

- Research mapping was precise - all 6 issues (F1-F6) mapped exactly to implementation
- Plan execution order worked well - config → utility → critical → hardening
- Instructions missed one edge case - legitimate proxy requests needed verification
- Code review added value beyond spec - IPv6 support, logging levels, validation gaps

## Pattern Recommendations

- Add to be-security: Trusted proxy validation pattern for client IP extraction
- Add to be-security: ObjectMapper for error response serialization anti-pattern
- Document: SameSite=Lax safety condition (state-changing ops use POST/DELETE only)
- Document anti-pattern: Don't validate tokens on IP change for consumer apps

## Next Time

- Validate configuration at startup with @PostConstruct, not lazily
- Use InetAddress.getByName() for production-grade IPv6 validation
- Test through actual proxy setup, not just header spoofing
- Ask deployment questions upfront - "Behind nginx?" should be in spec
- Clarify UX-security tradeoffs early in research phase
