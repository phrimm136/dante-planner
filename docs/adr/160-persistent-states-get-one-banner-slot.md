# 160 persistent-states-get-one-banner-slot
epic: none · pr: none

## Decisions
- @frontend @errors — A condition that holds over time (the server unreachable, local
  storage that cannot open, a newer deploy than the open tab) is shown in one app-wide banner
  slot above the header; a one-off event (saved, copied, a request that failed) stays a toast.
  The slot renders the single highest-priority active signal — server, then storage, then
  update — chosen by a pure `selectBanner`. Unavailability was a warning toast per failing query
  with no id, so an outage stacked toasts that vanished while the outage lasted, and a condition
  like unopenable storage had no surface at all.
  REJECTED: a toast with an id and infinite duration — on mobile the top-right toaster covers the
  header and content, and it is a banner without a slot.
  REJECTED: stacking several banners — on a phone two or three rows push the page below the fold.
  INVARIANT: at most one banner renders — `AppBanner` renders `selectBanner`'s single result.
  NON-GOAL: restricted-account and offline banners, and editor-scoped states (a save-failure
  streak, a plan changed or removed elsewhere), are separate units; `BanStatusBanner` stays
  unrendered here.
- @frontend @errors — Server reachability is recorded where every response passes,
  `ApiClient.fetch`: a 503 that maps to `BackendUnavailableError` or to
  `WriteTemporarilyUnavailableError`, or a fetch rejected without the caller aborting it, sets
  `down`; any 2xx sets `ok`. `WRITE_TEMPORARILY_UNAVAILABLE` is the backend's database-down code
  for every request, reads included (`DegradationErrorConstants.DB_UNAVAILABLE`), so it means the
  server cannot serve, not that writes alone are paused. The auth, rate-limit and deadlock 503s
  are partial or retryable and leave the status alone; a caller's abort (navigation, unmount,
  query cancellation) says nothing about the server. Queries no longer toast unavailability;
  mutations keep their toast because it answers the user's own action. Recording at the boundary
  also covers `/api/auth/me`, which swallows unavailability and returns the cached user or null.
  The status has no "updating" state: no production response carries `SERVICE_UPDATING` since
  the nginx maintenance flag was retired.
  REJECTED: setting the flag from `QueryCache.onError` and clearing it on query success — the
  auth query resolves successfully while the server is down, so it would clear the banner the
  other queries set.
  REJECTED: classifying by the response's code string in a second table — the error class
  `ApiClient` already picks for the 503 is the one classification; a second table drifted from
  the first on inherited keys.
  INVARIANT: a query never toasts an unavailable error.
  NON-GOAL: the auth-unavailable scope keeps its toast.
- @frontend @storage — When IndexedDB cannot open, the planner list queries resolve empty and
  the banner says local storage is unavailable, instead of the list query rejecting into the
  route error screen. The degradation sits in the two display queries' `queryFn`, not in the
  storage adapter: sync and import read the same lists and then write, so an adapter that
  answered `[]` would let a briefly failing open pull every server plan over unsynced local
  drafts and import over existing plans without the conflict dialog; they keep rejecting and
  abort. Storage is probed only on `/planner` routes, so other pages never open the database.
  REJECTED: `collectRows` returning `[]` on an open failure — every caller then reads "no local
  plans", including the ones that write next.
  REJECTED: returning `{ rows, available }` from `listLocal` — changes every caller's interface
  for a signal only the banner reads.
  INVARIANT: an IndexedDB open failure never deletes a server plan and never overwrites a local
  one — server deletes come only from tombstones, and sync and import reject before writing.
  NON-GOAL: local write failures keep their current path.
- @frontend @deploy — Each build mints a random build id, compiles it in as `__BUILD_ID__` and
  emits it as `version.json`; open tabs fetch that file without cache on focus and every ten
  minutes, and a different id shows the update banner with a reload button. A static file needs
  no server, and Pages serves it fresh after each deploy.
  REJECTED: the entry chunk's hash as the id — `define` is applied before chunk names exist.
  REJECTED: the git SHA — the Pages build environment's access to it is unverified, and a random
  id costs only one spurious banner when the same commit is redeployed.
  REJECTED: parsing the entry script out of `/` — couples the check to index.html's markup.
  NON-GOAL: no version endpoint on the backend.

## Takeaway
- takeaway: decide toast versus banner by whether the condition outlives the event that
  revealed it; and record shared status where every response passes, not where one consumer
  happens to see it.
