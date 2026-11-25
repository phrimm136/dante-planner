# 565 straight-line-checks-return-a-result
epic: none · pr: none

## Decisions
- @result @types (taste) — Checks that run one after another with no read between them return `Result<T, E>` and are joined with `flatMap`; the caller unwraps inside its transaction with `orElseThrow` or an exhaustive `switch`. A `@Parsed` factory of 563 may therefore return a Result as well as return the value or throw. Nested throwing factories read inside out, and a test of a refusal asserts on an exception.
  REJECTED: throwing factories everywhere — they work and stay for a check that stands alone.
  REJECTED: a Result for every decision — a decision with three or more cases, or with reads between its steps, is a sealed type and a `switch`.
  REJECTED: `Optional` — it drops the reason for the refusal.
  REJECTED: Vavr `Either` — it joins different error types no better, and 047 rejects the library.
  REJECTED: a lint that requires `orElseThrow` — a pure function returns the Result to be chained, and a `switch` is a valid use.
  INVARIANT: a method that returns a Result carries `@CheckReturnValue` on itself or a class around it — `ResultRuleTest`; with it a discarded Result, and a Result held in a local that is never read, fail `compileJava` (Error Prone `CheckReturnValue` and `UnusedVariable` at ERROR). The annotation on `Result` alone does not cover a factory in another class.
  INVARIANT: no `@Transactional` method returns a Result — `FailureUnionBoundaryTest`, since a Result is a `FailureUnion`.
  INVARIANT: a method that returns a Result performs no effect, in its lambdas included — `DecisionPurityTest` (fixture `ChainedPortReader`).
  NON-GOAL: an `Err` arm that does nothing — no check sees intent.
  NON-GOAL: a local named `unused…` or under `@SuppressWarnings("unused")` — Error Prone takes the name as a deliberate discard; the diff is where it is reviewed.
  NON-GOAL: test sources — `compileTestJava` does not raise the two checks.

## Takeaway
- takeaway: a refusal returned as a value needs a compiler that refuses to drop it, because a thrown one could not be dropped.
