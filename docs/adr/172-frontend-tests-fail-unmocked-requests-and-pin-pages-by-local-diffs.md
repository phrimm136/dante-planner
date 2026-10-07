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

## Takeaway
- takeaway: pin a matrix of renders as one baseline and local differences, so a change inside the
  shared page fails only the baseline and a cell's change fails only that cell; a node inserted
  before an ancestor of a cell's changed lines, or a changed ancestor tag or first class, rewrites
  their paths and re-records that cell too.
