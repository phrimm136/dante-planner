---
status: Accepted
tracking: none
---

# 0005 Server-side no-op conflict arbitration and digest retirement

## Summary

A stale-version planner write is no longer an automatic conflict: before throwing 409, the server
checks whether the write is an effective no-op — every client-mutable field equal to the stored
row, content compared as parsed JSON trees — and if so acknowledges it with the stored version,
writing nothing. Clients identify saves by `syncVersion` alone; the content digest never crosses
the wire and its column is retired for want of a reader.

## The rule

> A stale write that would change no persisted field — every carried scalar equal, the
> content tree-equal — is acknowledged with the stored `syncVersion`, writing nothing;
> every other path of the version check is unchanged.

## Motivation

The `(syncVersion, contentDigest)` client identity (ADR 071, RFC 0003/0004 stream 1) cannot work as
designed: the server's digest is computed over its sanitizer's normalized rendering, which a client
can never reproduce from its own bytes, so digest-gated ack adoption wedges a planner into
permanent phantom 409s and digest-based sync classification was dead code in production. Plain
version-only optimistic locking is sound but over-triggers: a retried save that already committed,
and a re-sent copy of content the server already holds, both surface a conflict dialog for a
divergence no user experienced. After this lands, a conflict is shown exactly when human intent
actually diverged.

## Current behavior

- `SyncVersionValidator.requireSyncVersionMatch(force, requestedVersion, actualVersion)`: `force`
  bypasses; a null or mismatched version throws `PlannerConflictException` unconditionally. Called
  from the two upsert paths in `PlannerCommandService` (`:352`, `:402`).
- `planner_content.content_digest BINARY(32) NOT NULL` (migration V058), computed in
  `PlannerContent.onCreate`/`recordSave` over the sanitized request string, exposed through
  `PlannerResponse.contentDigest`, `PlannerSummaryResponse.contentDigest`, and the
  `findOwnerSummaries` projection. Nothing else reads it.
- RFC 0004 stream 1 (in flight) holds frontend commits that consume the exposed digest; they are
  being stripped to version-only as job `frontend-versiononly` of this proposal.

## Prior art

HTTP conditional requests are this exact shape: `If-Match` failing yields 412, and mature servers
short-circuit "the representation you sent is what I already hold" to success — the
idempotent-receiver pattern. CouchDB is the counterexample: `_rev` mismatches 409 even when the
submitted document is identical, and its ecosystem carries client-side retry-dedup logic forever.
In-repo prior art is RFC 0003/0004 stream 1 itself: the digest-token design whose failure analysis
(three rendering domains, spiked below) produced this proposal.

## Proposal

The stale branch of the version check gains one arbitration step; nothing else moves:

```
force?                  → write (unchanged)
version matches?        → write (unchanged; no comparison runs on this path)
stale:
  effective no-op?      → 200, ack carries the stored syncVersion, zero writes
  otherwise             → 409 with the stored version (unchanged)
```

An *effective no-op* holds when applying the write would change no persisted field: every
client-mutable scalar the request carries equals the stored value, and the content string is
**tree-equal** to the stored content — both sides parsed and compared as JSON trees, so equality is
insensitive to key order, whitespace, and number rendering. Tree-equality, not byte-equality, is
load-bearing: a pulled document never returns byte-identical (the storage and serialization layers
each re-render it), so byte comparison would re-import the phantom conflicts this exists to remove.
A parse failure on either side falls through to 409 — arbitration never converts an error into a
success.

The no-op acknowledgement performs no write of any kind: no version bump, no timestamp touch. Both
comparands are already in hand on this branch, so no digest, hash, or token participates; with the
wire exposure withdrawn (RFC 0003's amended stream 1), the digest column has zero readers and is
dropped.

## Plan

Two phases, one job each.

- **Phase 1**
  - `backend-noop` — stale writes arbitrated by effective no-op equality; contentDigest
    leaves the response DTOs; content_digest column and its entity code retired.
    Files: the two upsert paths in `PlannerCommandService`, `SyncVersionValidator`,
    the response DTOs and `findOwnerSummaries` projection, the drop migration.
    Verification: the eight `backend-noop` scenarios and the four invariant gates.
- **Phase 2**
  - `frontend-versiononly` — RFC 0004 stream 1 lands version-only: client digest code
    stripped, batch pull and version discipline kept; RFC 0004's stream 1 text
    corrected. Verification: `frontend-schemas-parse-digestless-responses`; the digest
    modules are deleted, so any surviving importer fails `tsc -b`.

## Scenarios

```gherkin
Scenario: stale-identical-save-acks-current-version        # backend-noop
  Given a planner stored at syncVersion 6
  When an upsert presents syncVersion 5 with tree-equal content and every carried scalar equal
  Then the response is 200 with syncVersion 6, and the stored row is unchanged including lastModifiedAt

Scenario: retry-after-committed-save-is-idempotent          # backend-noop
  Given a save that committed and moved the planner from syncVersion 5 to 6
  When the byte-identical request is retried still presenting syncVersion 5
  Then the response is 200 with syncVersion 6 and the stored row is unchanged

Scenario: pulled-rendering-counts-as-identical              # backend-noop
  Given a planner whose version reached 6 via a force-push of state identical to version 5
  When a stale save presents syncVersion 5 with the content in the storage layer's rendering
  Then the response is 200 with syncVersion 6

Scenario: stale-title-change-still-conflicts                # backend-noop
  Given a planner stored at syncVersion 6
  When an upsert presents syncVersion 5 with tree-equal content but a different title
  Then the response is 409 carrying serverVersion 6

Scenario: stale-different-content-conflicts                 # backend-noop
  Given a planner stored at syncVersion 6
  When an upsert presents syncVersion 5 with content that is not tree-equal
  Then the response is 409 carrying serverVersion 6

Scenario: unparseable-content-falls-through-to-conflict     # backend-noop
  Given a stored content string that fails JSON parsing
  When a stale upsert arrives
  Then the response is 409, not 500 and not 200

Scenario: no-version-still-conflicts                        # backend-noop
  Given a stored planner
  When an upsert names no syncVersion and does not force
  Then the response is 409

Scenario: force-still-bypasses                              # backend-noop
  Given a planner stored at syncVersion 6
  When an upsert forces with any content at any version
  Then the write is applied and the response carries syncVersion 7

Scenario: frontend-schemas-parse-digestless-responses       # frontend-versiononly
  Given the backend of backend-noop
  When the frontend parses planner read, write, summary, and batch responses
  Then every strict schema parse succeeds with no contentDigest field defined or received
```

## Invariants

- The no-op predicate is biconditional with the write: it answers true exactly when applying the
  request would change no persisted field. Gate: a test applying the request to a copy of the row
  and asserting predicate ⇔ no field changed.
- The arbitration branch performs zero writes. Gate: integration test asserting version, content
  bytes, and `lastModifiedAt` unchanged after a no-op acknowledgement.
- Version-matching saves run no comparison. Gate: the pre-existing upsert integration tests pass
  unmodified.
- No frontend code references a content digest. Gate: the digest modules are deleted, so any
  surviving importer fails `tsc -b`.

## Verified facts

1. JSON byte stability — dead. Input `{"zebra":1,…,"big":1e21,…}` returned from a
   MySQL 8 JSON column key-resorted and re-spaced; Jackson renders `1e21` as `1.0E21`;
   `JSON.stringify(JSON.parse(x))` renders it `1e+21`.
2. Comparison cost — double parse plus tree-compare of a 170 KB document measured at
   1.4 ms, paid only on the stale branch.

Fact 1 licenses tree-equality over byte- or digest-equality (082 @sync @conflict);
fact 2 prices the arbitration branch. Decisions live in `docs/adr/` (082); this
document keeps no second copy.

## Drawbacks

- The list-sync flow still classifies rows by version alone, so a stale local draft over content
  identical to the server's can still prompt the user — no client-side identity check survives to
  suppress it.
- Any future feature needing cheap cross-device content identity (deduplication, ETag-style
  caching) must introduce a new token from scratch; nothing digest-shaped remains to extend.

## Non-goals

- No server-side size cap on `content` is added here (today the title is capped and content is
  bounded only by the HTTP request limit); recorded in `docs/debt.md`.
- The pull-side sync classification (`categorizeSync`) is not redesigned; it stays version-only.
- Force semantics are untouched, pinned by `force-still-bypasses`.

## Risks and rollback

- An over-matching no-op predicate silently discards stale edits. Detected by the biconditional
  gate before merge and, in production, by the existing 409-rate observability — a conflict rate
  falling to zero is the smoke signal. Undone by deleting the arbitration branch, which restores
  today's unconditional 409.
- The column drop is a one-way door only in ceremony: re-adding it is one migration, and the
  backfilled values were never comparable to wire digests, so no irreplaceable data is lost.

## Open questions

(none)
