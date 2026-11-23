# 554 main-code-nullness-is-checked-by-the-compiler
epic: none · pr: none

## Decisions
- @nullness @build @compiler — NullAway runs on the backend's main sources as an Error Prone check in JSpecify mode over `org.danteplanner.backend`: an unannotated reference is non-null, and `org.jspecify.annotations.Nullable` marks where an absent value enters or leaves: an entity field on a NULL column and the projections and SQL parameters that carry it, a wire DTO component the JSON contract allows to be null, a library return, a framework callback, or the parameter of the pure factory that parses one of these into a narrower type. Review under RFC 0002's null-handling rules had let 62 null-literal slots through. Re-check: `rg -c NullAway backend/build.gradle.kts`.
  REJECTED: review only — it missed those 62 slots.
  REJECTED: `java.util.Optional` for every absent value — rejected as a project preference (taste).
  INVARIANT: a nullable reaching a non-null fails `compileJava` — NullAway at ERROR.
  INVARIANT: a controller parameter that is optional on the wire and has no default is `@Nullable` — `OptionalParameterRuleTest`; guest principals, header reads and composed annotations are outside it.
  INVARIANT: the cookie-gate classes are outside the check until the cookie gate is removed — the exclusion list in the build file, unenforced.
  INVARIANT: `@Nullable` appears only at those places — unenforced.
  NON-GOAL: unannotated libraries and nullable callback parameters without an annotation — the checker trusts both as non-null.
  NON-GOAL: test sources — `compileTestJava` does not run NullAway, so a test may pass null into main code.
  NON-GOAL: a comparison with the Checker Framework — it was not spiked.

## Takeaway
- takeaway: which values may be absent is a type rule, and review alone does not hold one.
