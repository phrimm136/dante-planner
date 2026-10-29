# 172 frontend-tests-fail-unmocked-requests-and-pin-pages-by-local-diffs
epic: none · pr: none @tests @frontend @network @parity @naming

## Decisions
- @tests @frontend @network — Every frontend unit test runs under one shared MSW server that fails any
  awaited request no handler matches; a test that needs `fetch` itself as the double spies on it. A
  suite-wide `fetch = vi.fn()` stub answered every request with `undefined`, so a test whose code path
  issued a request it never mocked still passed, and four files had to tear the stub down to use MSW.
  The cost is measured over alternating full runs: summed setup rising from about 60 s to 110 s
  across 303 files and the suite's reported duration from 55 s to 60 s; peak memory stayed within the
  run-to-run spread on this box (2.7 to 2.9 GB before, 2.8 to 3.2 GB after).
  REJECTED: a global fetch stub that throws on any call a test did not mock — it fails unmocked calls
  too, but every network test then mocks `fetch` call shapes instead of HTTP, which is what the
  request client sends.
  INVARIANT: an awaited request no handler matches fails the test — `onUnhandledRequest: 'error'` in
  `vitest.setup.ts`. A request whose rejection the code swallows only logs; that gap is unenforced.
- @tests @frontend @parity — A detail page's state matrix is pinned as one full normalized DOM per
  layout plus, for every other cell, the difference from that layout's baseline, each changed line
  prefixed with its ancestor path and each node's index among its siblings, and no context lines.
  Whole-page snapshots per cell stored the same chrome 21 times and failed every cell on one shared
  change; the differences between cells were 5 to 31 of about 430 tag lines. A cell is captured only
  after every progressively revealed section and lazily loaded control has mounted, with the page's
  static data already in the query cache: a capture that raced the reveal missed sections, and a
  click landing while descriptions were still suspended re-rendered without end until the worker ran
  out of memory.
  REJECTED: hand-written assertions for each cell's differences — any difference the author does not
  name goes unchecked.
  REJECTED: snapshotting only the skill panel per cell — a difference outside the chosen region goes
  unchecked.
  REJECTED: a hash of each cell's DOM — keeps every byte but a failure shows no diff, so a legitimate
  change is re-recorded blind.
  REJECTED: diff hunks with context lines — a shared change near a cell's difference failed that
  cell's diff as well as the baseline.
  INVARIANT: every cell's rendered DOM equals its layout baseline plus its stored diff, so a change
  anywhere fails the baseline or that cell — the parity tests in `IdentityDetailPage.parity` and
  `EGODetailPage.parity`.
- @tests @frontend @naming — A test title states the behaviour in the present tense; "should" is not
  allowed. Sixty-five titles carried it, and the rest of the suite already stated behaviour directly.
  INVARIANT: no test title contains "should" — oxlint `vitest/valid-title` with
  `disallowedWords: ["should"]`.
- @tests @frontend @i18n — A test that mounts a page over the live static data asserts only what holds
  for every data version, such as every label being non-empty; a rule for missing translation text is
  a pure function tested with made-up inputs. Two page tests pinned that CN lacked text for Pwie's
  Buffet forms, and static c5ce83f6 added the text, so both failed while the code
  was correct; CN lags new content and catches up on later updates, so every pinned gap fails the
  same way.
  REJECTED: page tests that warn instead of fail on a gap — a warning goes unread in CI, and the
  static pipeline already reports gaps (static ADR 490: CN warns under a ceiling, KR, EN and JP fail).
  REJECTED: updating the expected text to each new data version — the test then breaks on every
  catch-up and stops covering the missing-text branch.
  INVARIANT: the missing-text rules are pinned with made-up inputs — `shownStageEffects` and
  `formName` tests in `pages/battle/lib/__tests__/battleDetail.test.ts`; that no page test pins a live
  gap is unenforced.
  NON-GOAL: a frontend check that reports translation gaps — the static pipeline owns that report.

## Takeaway
- takeaway: pin a matrix of renders as one baseline and local differences, so a change inside the
  shared page fails only the baseline and a cell's change fails only that cell; a node inserted
  before an ancestor of a cell's changed lines, or a changed ancestor tag or first class, rewrites
  their paths and re-records that cell too.
