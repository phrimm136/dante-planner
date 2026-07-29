# 030 keyword-pipeline-boundaries
epic: none · pr: none

## Decisions
- @projection @derivation — The denormalized keyword column is derived server-side from the content tree, and the redundant top-level copy is deleted from the request payload. REJECTED: accepting a client-supplied projection alongside its source — a denormalized read model must be derived from its single source, never sent as a second copy the server trusts to stay in sync with the first.
- @keywords @strictness — Renames always remap, while a genuinely unknown keyword is rejected only when publishing and tolerated on a draft sync. REJECTED: rejecting unknowns on every write — routine draft saves would newly start failing for documents that had been saving fine, which is the two-tier strictness the client already applies.
- @keywords @filtering — The derived column filters to valid members while the content blob retains unknowns. This is forced rather than chosen: the column type physically rejects non-members, and the draft tolerance above guarantees unknowns can reach the write path.
- @transaction @toctou — The version-conflict check and the per-user limit count stay inside the write transaction. REJECTED: extracting them into an orchestrating service — it reopens a lost-write time-of-check-to-time-of-use gap, makes count-then-insert non-atomic, and trades a read that is currently free inside the existing upsert transaction for a paid one, at single-row-upsert scale, for no gain.
- @keywords @client-defense — The client-side migrate-on-read and publish rejection stay in place. REJECTED: removing them as redundant with the server-side work — guest documents live only in browser storage and never reach the server, so for that population the client is the sole defense rather than a duplicate of one.

## Takeaway
- takeaway: "the server now validates this, so the client check is redundant" holds only when every document actually reaches the server. A local-first application has a population that never does, and for it the client-side rule is not defense in depth but the only defense.
