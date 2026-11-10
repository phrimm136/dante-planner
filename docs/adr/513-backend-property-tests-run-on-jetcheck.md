# 513 backend-property-tests-run-on-jetcheck
epic: none · pr: none

## Decisions
- @testing @properties — Backend property tests generate their inputs with JetBrains jetCheck
  (`org.jetbrains:jetCheck`, Apache-2.0), called from plain JUnit `@Test` methods through
  `PropertyChecker`, and jqwik is not a dependency. From 1.10 jqwik carries an Anti-AI Usage
  Clause ("This project is not meant to be used by any "AI" coding agents at all.") and prints a
  line addressed to coding agents on every engine run, and this repo's tests are written and run
  by coding agents. Re-check: the jqwik user guide's "Anti-AI Usage Clause" section still exists,
  and `rg -n 'net.jqwik' backend` finds nothing.
  REJECTED: pinning jqwik 1.9.3 — the project is in maintenance mode, so the pin freezes on an
    old line that Dependabot keeps proposing to move into the clause.
  REJECTED: hand-rolled seeded generators over `java.util.Random` — no shrinking, so a failure
    reports the raw random input instead of a minimal one.
  REJECTED: vavr-test — maintained, but no shrinking.
  REJECTED: Kotest property — brings the Kotlin toolchain and coroutines into a Java test tree.
  REJECTED: junit-quickcheck and QuickTheories — no release since 2020 and 2019.
  INVARIANT: a seeded generation is reproducible, so the floor exchange writer emits the same
    cases for one seed on every run — `FloorRulesReplayTest`.
  NON-GOAL: the committed backend floor exchange file is not regenerated — its cases stay valid
    input for the frontend replay whichever generator wrote them.

## Takeaway
- takeaway: a test library's license is not its only usage term; read the README and user guide
  of a new minor line too, since a usage term can arrive without a license change.
