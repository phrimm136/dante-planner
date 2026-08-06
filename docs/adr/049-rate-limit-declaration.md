# 049 rate-limit-declaration
epic: none · pr: none

## Decisions
- @ratelimit @identity — The declarative rate-limit annotation carries the policy enum; attachment (class-level default, method-level override) stays decoupled from bucket identity. Bucket keys, limit configuration, and subject prefixes bind to policy names and form a pinned production contract that must survive code moves. REJECTED: controller-class-as-identity — a rename silently resets every live bucket, subject prefixes are inexpressible, and the class-to-policy mapping is many-to-many in both directions. REJECTED: method-level-only declaration — recreates the per-site repetition being removed.
- @ratelimit @enforcement — Endpoint coverage fails the build through an architecture test (every handler resolves a policy or an explicit exemption), backstopped by a deny-and-log branch on the structurally unreachable no-annotation path. A gate is worth what its last run proved; the backstop covers the deploy where the suite did not run. REJECTED: startup fail-fast — the same guarantee one stage later, in a second enforcement home. REJECTED: a runtime default policy — silently applies the wrong subject model to pre-authentication endpoints.
- @ratelimit @auth-callback — A rate-limited OAuth callback redirects with a distinct rate-limit error code. A browser navigation target cannot render a 429 usefully, and distinct wire codes per degradation cause are the established pattern. REJECTED: a plain 429 response — unrenderable at that boundary, leaving rate-limited users indistinguishable from declined consent.

## Takeaway
- takeaway: identities that outlive code layout — bucket keys, config bindings, dashboards — must never derive from code layout; attach behavior by position, identify it by name.
