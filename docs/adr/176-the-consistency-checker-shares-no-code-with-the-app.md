# 176 the-consistency-checker-shares-no-code-with-the-app
epic: none · pr: none @consistency-checker @placement

## Decisions
- @consistency-checker @placement — The consistency checker is its own `consistency` source set in the
  backend Gradle project, under the package root `org.danteplanner.consistency`, and its only input is
  the history format. Its compile classpath holds Jackson and nothing else. Its tests, the operation
  generator, the fault scheduler and the recorder live in the backend test tree beside the existing
  causal test setup. An independent checker shares no code with the system it judges. Then an
  application bug cannot also live in the judge. A checker with no application imports also moves to
  another host by a directory move. The mutation gate mutates the main and `consistency` source sets
  only, so the checker is under it without test helpers becoming mutation targets.
  REJECTED: a Gradle subproject — the backend becomes a multi-project build. The checkstyle, pitest
  and `pitestRatchet` wiring must then repeat for the subproject. A source set gives the same
  compile boundary inside one project.
  REJECTED: a package in the test source set — pitest then mutates the whole test source set. Test
  helpers in the existing target packages became mutants. Survivors in
  `org.danteplanner.backend.auth.token` rose from 76 to 119. An exclusion list by name breaks at the
  next helper.
  REJECTED: a separate repository — the rules are this application's ADR 008 contract. The history
  format changes with the generator in the first phases, so each format change spans two
  repositories and a version pin.
  REJECTED: a denylist rule that forbids `org.danteplanner.backend` imports — dependencies it does not
  name, such as Spring utilities, enter silently and break a later extraction.
  INVARIANT: classes under `org.danteplanner.consistency` import only `java.*` and Jackson — the
  `consistency` source set's compile classpath, and `ConsistencyCheckerIsolationTest`.
  NON-GOAL: building the checker and the application from different commits — only the agent merge
  gate needs it, and that tool waits.

## Takeaway
- takeaway: the independence of a judge is a property of its imports, not of its build or its
  repository. A compile classpath enforces it and also covers dependencies that nobody named.
