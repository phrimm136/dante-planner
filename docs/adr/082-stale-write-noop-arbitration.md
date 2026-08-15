# 082 stale-write-noop-arbitration
epic: none · pr: none

## Decisions
- @sync @conflict @noop — Stale writes are arbitrated by direct equality — scalars compared directly, content as parsed JSON trees — because both operands are local to the check and equality is the question being asked. REJECTED: digest-equality comparison — spiked dead: MySQL re-sorts keys and re-spaces, Jackson and JavaScript each renormalize numeric literals, so a recomputed digest diverges from the stored one for semantically identical content. REJECTED: a concatenated multi-field digest — equality via hashing, plus a field-recipe that must track every future mutable column. REJECTED: client-visible digest tokens (the 071 design) — normalization makes an ack unmatchable by its author; supersedes 071's @sync @digest bullets (identity, causality, recompute, backfill) once the arbitration is implemented — until then they describe the running system.
- @sync @digest @retirement — The `content_digest` column is dropped, because after wire withdrawal and direct comparison it has zero readers, and an unread column is speculative retention. REJECTED: keeping it for observability — nothing observes it.
- @sync @rollout @ordering — The backend's arbitration and digest wire-withdrawal merge before the frontend drops its digest declarations, because the frontend's `.strict()` response schemas reject unknown fields, so the backend must stop emitting the field before the frontend stops declaring it. REJECTED: a tolerated optional schema field — a compatibility shim that merge ordering makes unnecessary.

## Takeaway
- takeaway: when both operands of an identity question are already in hand, compare them directly — any digest, hash, or token interposed between two local values imports every normalization domain it crossed as a new way to be wrong.
