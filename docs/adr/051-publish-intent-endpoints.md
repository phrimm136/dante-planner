# 051 publish-intent-endpoints
epic: none · pr: none

## Decisions
- @wire @unpublish — Publish and unpublish are two intent endpoints; the published boolean is eliminated at every layer, and the legacy boolean route delegates for exactly one release, its deletion tracked as work in the release that follows. Deployed and running frontends are different populations, no auto-reload mechanism exists, and an open-ended window is a compatibility layer rather than a coexistence window. REJECTED: removing the legacy route in the same release — stale local-first tabs keep a dead publish control until a manual reload. REJECTED: retaining the boolean endpoint — preserves the branch-on-boolean seam the intent methods exist to remove.

## Takeaway
- takeaway: a coexistence window is legitimate only with a mechanical removal condition — "one release, then deleted, tracked" is a scope; "until clients catch up" is untracked work wearing one.
