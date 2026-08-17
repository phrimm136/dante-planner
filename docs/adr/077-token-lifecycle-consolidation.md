# 077 token-lifecycle-consolidation
epic: none · pr: none

## Decisions

- @auth @rotation — The legacy non-lineage rotation path (flag bean, legacy admission, family-id
  synthesis) is removed: the lineage rollout is complete and the seven-day refresh TTL bounds
  surviving legacy tokens to zero. REJECTED: continued admission — a coexistence window whose
  end condition has passed is untracked work wearing a scope's clothes.
- @auth @refresh — Refresh orchestration moves from the authentication filter into a
  SessionRefresher collaborator; the name sits beside SessionRevokedException and
  UserSessionService, which already treat a refresh-token family as one login session.
  REJECTED: a second servlet filter — the refresh path shares the authentication verdict,
  response cookies, abandon protocol, and outage short-circuit. REJECTED: token-first naming —
  it would make the new class the odd one out among its neighbors.
- @auth @token — The token blacklist and refresh-rotation services consolidate into one
  TokenLifecycleService: their Lua scripts already cross both key namespaces atomically, so the
  key schema is one subject with one owner. The fail-open posture is scoped to revocation reads
  and the fail-closed posture to all writes, per method group. REJECTED: two services with a
  one-way key-schema dependency — leaves the atomic logout script writing a namespace it does
  not own. REJECTED: the name TokenRevocationService — rotation mints tokens.
- @auth @logout — Logout drops the refresh-token hash entry; family revocation covers it and is
  stronger under outage, because the hash check fails open while the family check fails closed.
  REJECTED: keeping both — a fail-open backup behind a fail-closed primary adds storage, not
  guarantee.
- @auth @logout (taste) — The logout script keeps its array-based segmented-KEYS form although
  the population shrinks to at most one token and one family: revocations are built
  conditionally, so the legal shapes are {0,1} tokens by {0,1} families. REJECTED: fixed
  positional keys — sentinel keys or four call variants, moving shape-handling into the caller.
- @auth @rotation @naming — The persisted rotation states rename to LIVE (was UNUSED_LATEST) and
  IN_GRACE (was PENDING), and the legacy USED spelling is dropped after conversion to RETIRED;
  the theft arm's spellings are untouched. REJECTED: keeping the old spellings — the state
  machine is the security argument, and its names misstated it.
- @auth @rotation @migration — Pre-rename values are batch-converted by an idempotent boot-time
  converter running before readiness, valid only under big-bang deployment (no old writer
  survives into the new keyspace); the converter is deleted in the first release after it has
  run in production. REJECTED: a synonym window inside the script — its removal edits the
  security-critical Lua again, where deleting the converter is inert.

## Takeaway

- takeaway: identifiers persisted in a datastore are wire format, not names — renaming one is a
  migration with a coexistence window, and the only cheap rename is at birth.
