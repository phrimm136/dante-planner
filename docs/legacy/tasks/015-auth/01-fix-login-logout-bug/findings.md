# Learning Reflection: Fix Authentication Session Bugs

## What Was Easy

- 204 response handling - additive status check with no ripple effects
- Error distinction - backend already returned needed context, just required parsing
- Testing framework - TanStack Query mocking patterns already established (33 tests smoothly)
- Toast integration - Sonner already global, just import + call
- TypeScript caught type mismatches on error responses early

## What Was Challenging

- Infinite refresh loop from hasty refactor - changed pattern without understanding WHY
- Error status extraction - using 0 as default caught redirects/network, causing toast spam
- Conceptual gap between 401 contexts - "guest" vs "token expired" not initially distinguished
- Test file extension - JSX in .ts caused parser errors

## Key Learnings

- HTTP semantics matter more than pattern shape - `/auth/*` grouped by intent, not URL
- Error responses carry structured data - backend already distinguished, frontend discarded it
- 204 has no body by design (RFC 7231) - client must respect HTTP semantics
- Different errors need different UI states - 401/5xx/network shouldn't all show login
- Use null for "unknown," not 0 - avoids collision with valid values

## Spec-Driven Process Feedback

- Research mapping 80% accurate - missed that exclusion was intentionally semantic
- Plan revision happened mid-execution - P1-2 caused loops, required pivot
- Instructions lacked "why" context - led to confidently breaking correct code
- Manual test checklist would have caught infinite loop immediately

## Pattern Recommendations

- Document semantic groupings in API clients with comments explaining WHY
- Use structured error classes instead of regex parsing
- Add HTTP status code reference to style guide (204 body, 401 contexts)
- Test existing behavior before refactoring error retry logic

## Next Time

- Ask "why" before changing foundational patterns
- Map backend error codes before choosing extraction method
- Separate bug discovery from bug fixing - fix root cause, not suspicious code
- Create HTTP protocol reference during project kickoff
- In code reviews, ask what error types are being swallowed by catch-alls
