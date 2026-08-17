# 042 sse-exclusion-by-account
epic: none · pr: none

## Decisions
- @sse @identity — Comment fan-out skips the raising account rather than the raising device, so the value the exclusion turns on comes from the authenticated principal instead of a cookie the client chooses. Nothing on this channel can be published by a guest — comment creation is authenticated — so an account is always available to name. REJECTED: excluding by device — the finer grain is real, since it would let the author's other tabs receive their own comment, but the client patches its cache by comment id and discards a duplicate, so the finer grain buys nothing observable. The earlier argument for keeping it assumed an echo would render twice, which reading the client disproved.
- @sse @identity — The device identifier stays, scoped to de-duplicating a client's own reconnects. Forging it costs only the forger a redundant connection slot, which is the test that separates a safe use of a client-supplied value from an unsafe one. REJECTED: removing it in favour of the account — a guest has no account, and two guests would then collapse into one registry entry.
- @sse @identity — The excluded account travels in the published envelope rather than being resolved at delivery, because the pod that publishes is not the pod that holds the emitter. REJECTED: resolving it from the payload at delivery — the payload is a client-facing projection carrying display names, not identifiers, and widening it to carry one would put an internal id on a guest-readable channel.

## Takeaway
- takeaway: an identifier is safe to accept from the client exactly when a lie about it costs only the liar, so the same field can be sound as a de-duplication key and unsound as an exclusion or quota key.
