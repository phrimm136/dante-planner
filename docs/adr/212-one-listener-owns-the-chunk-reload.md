# 212 one-listener-owns-the-chunk-reload
epic: none · pr: none · supersedes: 159 @frontend

## Decisions
- @frontend — A tab whose lazy chunk fails to load reloads itself only from the `vite:preloadError`
  listener, unless the current document began within 10 seconds of the listener's last reload (it
  is that reload's result) or sessionStorage cannot hold the timestamp; those fall through to the
  route error screen. Measuring from the document's start rather than the failure keeps a page
  that needs longer than the window to reach the import from reloading forever; the cost is that a
  document produced by a reload does not reload itself again for a later failure, which the error
  screen's reload button covers. Every `import()` in a Vite 8 build passes through the preload
  helper, which dispatches the event with the import's own error for both a 404 and an HTML body
  (Chromium 151, Firefox 153; WebKit unmeasured). Route components are wrapped by the app's own
  `lazyRoute`, which preloads, rethrows a failed import to the error screen and retries on the next
  load, and never reloads. TanStack Router's `lazyRouteComponent` also reloads, once per error
  message per session, and from 1.170 it records that only when the failed component renders: in
  the document the listener's reload produced, it reloaded a second time (the stale-chunk e2e
  counted three loads where it expects two).
  REJECTED: keeping both reloads and seeding TanStack's `tanstack_router_reload:<message>` key from
  the listener — couples the app to a private storage key whose timing already changed once
  without notice.
  REJECTED: relying on TanStack's reload alone — misses preload and non-route imports, and gives
  the error screen no way to recognise a chunk failure.
  REJECTED: once per session — a tab open across two deploys would not recover from the second.
  INVARIANT: no source imports `lazyRouteComponent` from `@tanstack/react-router` — oxlint
  `no-restricted-imports`.
  INVARIANT: a chunk that keeps failing produces exactly one reload, then the error screen with
  `CHUNK_LOAD` — the stale-chunk e2e.
  INVARIANT: the listener never calls `preventDefault()` — a prevented event makes the import
  resolve to `undefined`, replacing the chunk error with an unrelated `TypeError`; the
  stale-chunk e2e reaches the error screen with `CHUNK_LOAD`.
  NON-GOAL: an automatic reload for a second, different chunk failure within 10 seconds of a
  reload — the error screen's reload button covers it.

## Takeaway
- takeaway: two recovery mechanisms that happen to compose are one library release away from
  compounding; give each failure exactly one owner.
