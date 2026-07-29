# 025 fe-schema-type-direction
epic: none · pr: none

## Decisions
- @schema @ssot — Types at a parse boundary derive from their schema by inference, including API response types. REJECTED: maintaining type and schema by hand as separate artifacts — double bookkeeping already existed, with an interface and a schema describing the same pipeline JSON and nothing forcing them to agree, while the validator silently strips fields the schema does not declare. Deriving converts that silent drift into a compile error.
- @schema @saved-documents @exempt — The saved-document tree is deliberately exempt: its gate is looser than its type, and a reader schema must accept documents older than the writer type describes. REJECTED: deriving the document schema from its type — that collapses schema evolution to the current version and silently destroys older saves on load. REJECTED: type-to-schema code generation — a generator can only express equality between the two, which is the single relationship this tree forbids. Consistency is pinned instead by type-level assertions: equality for leaf types, and a one-directional assignability check for composites.
- @schema @strip @round-trip — Completing a persisted schema must preserve strip behavior across the save and load round trip, so the fixture test asserts round-trip identity rather than merely that parsing succeeds. Adding a field makes the validator retain a key it previously discarded, which changes the bytes written back.
- @schema @refine @blast-radius — Completing a load-path schema is structural only and never introduces a rejecting refinement, because a strict refinement anywhere in the saved-document tree discards the *entire* document on load rather than the offending field.

## Takeaway
- takeaway: a schema on a write path and a schema on a read path want opposite strictness, so "keep the type and the schema in sync" is only correct where the two directions coincide. Where a reader must tolerate what today's writer would never produce, equality is precisely the wrong relationship to enforce.
