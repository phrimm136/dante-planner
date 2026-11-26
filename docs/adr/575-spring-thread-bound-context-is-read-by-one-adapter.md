# 575 spring-thread-bound-context-is-read-by-one-adapter
epic: none · pr: none

## Decisions
- @adapter @archunit — Spring's thread-bound security and request context is read and written only by `SpringContextAdapter`, which turns their nulls into the codebase's types: the signed-in user is a `Viewer` (a missing authentication, an anonymous token and a principal that is not a user id are all a guest), and a request value is read with the value to use when it is absent. Six classes read the context and each handled its null in its own way: one switched on `case null`, one wrapped the authentication in a type on the switch line, one logged an unexpected principal type, and the access-token check kept a session for any non-null authentication.
  REJECTED: a type per site for the same cases — the rate limiter's `SignIn` and `Payer` and the restriction hook's `Principal` named the guest and member cases `Viewer` already names.
  REJECTED: an injected bean — the restriction hook's decision record is called statically from the check service, and every caller reads the same static holders, so a bean adds wiring and changes no test seam.
  INVARIANT: no main class but the adapter names `SecurityContextHolder` or `RequestContextHolder` — `SpringContextAdapterRuleTest`.
  NON-GOAL: test sources — a test sets the security context directly to arrange a login.
  NON-GOAL: entity columns and DTO fields — they stay the boundary for their own nulls.
- @restriction — What restriction owes a request is one `RestrictionDuty` born once in a handler interceptor's `preHandle`: a check due for a signed-in user of a `@Restricted` handler, a decision due for a `@RestrictedByState` handler, or nothing; the transaction callbacks read it, decide in a pure method and move it to discharged. The hook created its state lazily on the first transaction callback and held the declaration beside two flags, each meaningful for one declaration only, so a restricted request could hold a decided flag.
  REJECTED: creating the state on the first transaction callback — a read-only transaction then has to read the request before it can skip, and the state's birth depends on which callback comes first.
  REJECTED: flags beside the declaration — they allow combinations no request can be in.
  INVARIANT: a restricted request is checked once, at its first write transaction, and a by-state request that commits a write without a decision fails or is counted — `RestrictionTransactionHookTest`.
  NON-GOAL: transactions outside a handled request (schedulers, filters before handler matching) — their duty is discharged, as before.

## Takeaway
- takeaway: a framework null handled at every call site comes back as a different type at each one; read it in one place and hand out the type.
