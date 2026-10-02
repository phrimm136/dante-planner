# 159 asset-misses-are-real-404s
epic: none · pr: none

## Decisions
- @frontend @deploy — The build emits `a/404.html`, so Pages answers a missing `/a/*` file with
  a 404 (`cache-control: no-store`) while app routes keep the SPA fallback. Without it Pages
  answered a missing chunk with `200 text/html`, and the `/a/*` header rule stamped that HTML
  with the one-year immutable header, so edges and browsers cached HTML under a chunk URL: a tab
  open across a deploy, or a request in the deploy window, broke the route until the URL changed.
  Measured on a throwaway Pages project: with only `a/404.html`, `/a/missing.js` → 404 no-store,
  `/planner/md` → 200 index; with a top-level `404.html` added, every unknown route → 404.
  REJECTED: a top-level `404.html` with a `/* /index.html 200` rewrite — Pages follows redirects
  even when an asset matches, so every chunk would be rewritten to index.html.
  REJECTED: a top-level `404.html` that copies index.html — every deep link answers 404, which
  the sitemap and link previews read.
  REJECTED: an edge Cache Rule bypassing non-200 responses under `/a/*` — the fallback is a 200,
  and an edge rule cannot change what the browser caches.
  REJECTED: shortening the `/a/*` max-age — taxes every correct chunk to shorten, not prevent,
  the poisoning.
  The plugin derives the directory from the build's `chunkFileNames`, so the page follows the
  chunks if their prefix moves.
  INVARIANT: no response under `/a/` is HTML carrying the immutable header — the config test
  that the plugin is registered and that chunk, entry and asset file names share the directory
  it writes `404.html` into.
  NON-GOAL: the one-year immutable header on `/a/*` stays.
- @frontend — A tab whose lazy chunk fails to load reloads itself from a `vite:preloadError`
  listener, unless the current document began within 10 seconds of the listener's last reload
  (it is that reload's result) or sessionStorage cannot hold the timestamp; those fall through to
  the route error screen. Measuring from the document's start rather than the failure keeps a
  page that needs longer than the window to reach the import from reloading forever; the cost is
  that a document produced by a reload does not reload itself again for a later failure, which
  TanStack's reload and the error screen's reload button still cover. Every `import()` in a Vite 8 build passes
  through the preload helper, which dispatches the event with the import's own error for both a
  404 and an HTML body (Chromium 151, Firefox 153; WebKit unmeasured).
  TanStack Router's `lazyRouteComponent` already reloads once per error message per session, but
  only for a route component's own import and only when the message matches its three browser
  strings; a failed CSS or shared-dependency preload, or a module a loader imports, reaches the
  route as a different error and gets no reload. The two mechanisms coexist and together still
  reload once.
  REJECTED: relying on TanStack's reload alone — misses preload and non-route imports, and gives
  the error screen no way to recognise a chunk failure.
  REJECTED: once per session — a tab open across two deploys would not recover from the second.
  INVARIANT: the listener never calls `preventDefault()` — a prevented event makes the import
  resolve to `undefined`, replacing the chunk error with an unrelated `TypeError`; the
  stale-chunk e2e reaches the error screen with `CHUNK_LOAD`.
- @frontend @errors — The error screens show a short code under the message in production:
  `CHUNK_LOAD`, or `UNKNOWN · <error.name>`. A chunk failure is recognised by a symbol the
  `vite:preloadError` listener sets on the event's payload, which is the same object the route
  receives; browsers word the rejection differently (`Failed to fetch dynamically imported
  module`, `error loading dynamically imported module`), so its text cannot classify it. On
  `CHUNK_LOAD` the retry button reloads the page instead of resetting the router, which would
  re-run the same failed import.
  REJECTED: matching the rejection message — differs per browser and WebKit's is unmeasured.
  NON-GOAL: no client error reporting service; the code exists so a user's screenshot names the
  failure.

## Takeaway
- takeaway: a path-scoped cache header applies to whatever the server answers on that path,
  including its fallback; make the fallback impossible there rather than tune the header.
