# Code shape

Questions a reviewer asks of every method and type a change touches. Each came from rewriting real
code with the owner. A question is failed only by lines that can be quoted; "it reads badly" fails
none. Line count is reported, never a reason.

A guard below is an `if` without `else` whose body is one bare `return;`, one `throw` or one
`continue`; the `&&`/`||` terms of its condition are part of it. An early `return` of a value is a
decision, not a guard: a run of them is a decision table inside an effect method.

## Values and effects
- **V1** Does a method that reads, writes, logs or calls a port also work out a judgement beyond
  guards? Evidence: the effect line and the judgement line in one method.
- **V2** When several facts decide the case, are they read first, decided by one pure method, and
  acted on after? Failed by a decision spread over several effect methods, or a pure decision that
  performs a read. When each step needs the previous result and stops for one reason, a chain of
  `Result` steps ended by `orElseThrow` or `fold` passes.
- **V3** Is an expected answer thrown and caught where the callee could return it as a value?
  Evidence: the catch, and the callee that throws.

## Types
- **T1** Is a boolean, a null or an empty check turned into a type on the line that branches on it?
- **T2** Is a fact held in a weaker form than where it is born: a null where a case would say why,
  the same null checked twice in one class, a flag beside the field it qualifies? Evidence: the
  repeated checks, or the field and its flag.
- **T3** Do two types name the same cases, or does a method only translate one into the other?
- **T4** Does a type exist that makes no wrong combination impossible, whose cases carry no
  different data, that crosses no module and is switched on in one place only?
- **T5** Does a wrapper, a case or a recursion exist only so that a rule or a count passes? The
  finding is then against the rule too, and its fix names the ADR to change. Forms met so far: a
  boolean wrapped in an enum to switch on; `switch (x) { case null -> ... }` for `if (x == null)`;
  a lookup named `...Outcome`; a run of early value returns presented as guards.
- **T6** Does a null or an `Optional` live inside the code instead of at a boundary? A boundary
  is a framework or library return (`RequestContextHolder.getRequestAttributes()`,
  `getAuthentication()`, a repository `findBy...`), an entity column, a DTO field. The line that
  receives it turns it into a named type (`Unguarded`, `Anonymous`) or ends it with an invariant;
  methods, parameters, returns and fields past that line carry no `@Nullable` and no `Optional`.
  Evidence: a `@Nullable` or `Optional` on a member that is not the receiving line.

## Calls and names
- **C1** Does an argument hold more than a name or one short call?
- **C2** Do two reads answer one question without one method that asks it?
- **C3** Does a name say less than the method does (a lookup that creates or stores; a check that
  writes), or promise what it no longer does?
- **C4** Do names break the conventions: a pure judgement is `...Decision` or `of`; `parse` may
  refuse and `of` cannot; `...Outcome` reports what a command did; `Result` is only
  `Result<T, E>`; an effect is a verb that says what it does?
- **C5** Is the method a reader came for placed after what it calls, or after the types?

## Tests
- **X1** Did a test's expected value change although the problem did not ask for it?

## A rewrite
A rewrite offered as proof fails none of these questions. Next to it the reviewer lists every
question with the rewrite's line that answers it, or "n/a". The main loop compiles and tests it; a
rewrite that fails either is rejected.

## Example: `AccessTokenAuthenticator`, 175 lines to 124
Before: `verify` built `TokenCheck.NeedBlacklist`, `NeedInvalidation`, `Authenticate` and a nested
`Terminal` with five cases (T3, T5), recursed through `proceed` and read Redis inside the
arguments of that call (C1, V1), and translated `Terminal` into `AccessTokenOutcome` case by case
(T3). After:
```java
public AccessTokenOutcome authenticate(String token, HttpServletRequest request) {
    AccessClaims claims;
    try {
        claims = tokenValidator.validateAccessToken(token);
    } catch (InvalidTokenException invalid) {
        return refuseInvalid(invalid.getReason(), request);              // V3: stays until the validator returns a value
    }

    AccessDecision decision = AccessDecision.of(claims, revocationOf(token, claims), currentLogin()); // V2, C1, C2
    perform(decision, claims, request);
    return decision.outcome;                                              // T3: the decision carries the answer
}

enum AccessDecision {
    SIGN_IN(AccessTokenOutcome.AUTHENTICATED),
    KEEP_SIGNED_IN(AccessTokenOutcome.AUTHENTICATED),
    REFUSE_REVOKED(AccessTokenOutcome.REVOKED),
    REFUSE_SENTINEL(AccessTokenOutcome.SENTINEL_BLOCKED);
    ...
    static AccessDecision of(AccessClaims claims, RevocationOutcome revocation, @Nullable Authentication current) {
        return revocation == RevocationOutcome.REVOKED ? REFUSE_REVOKED
                : current != null ? KEEP_SIGNED_IN
                : claims.isSentinel() ? REFUSE_SENTINEL
                : SIGN_IN;
    }
}
```
