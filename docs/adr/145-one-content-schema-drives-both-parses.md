# 145 one-content-schema-drives-both-parses
epic: none · pr: none · supersedes: 139 @planner @floors @boundary, 143 @validation @shapes, 143 @validation @sinners

## Decisions
- @validation @schema — The planner content shape is written once, as the client's Zod schema,
  and exported to a committed JSON Schema that the server validates with a JSON Schema library
  before binding the content to a typed tree; a test fails when the committed file differs from a
  fresh export. Adding a planner type meant writing its shape twice, once per runtime, and the
  corpus found the disagreements only afterwards.
  REJECTED: a JSON Schema file as the source, with the client validating it through Ajv — a second
  validation library beside Zod, which the client already uses at every other data boundary.
  REJECTED: generating Zod from a JSON Schema source — the converters silently drop a
  `propertyNames` pattern and report a missing field as a wrong type.
  REJECTED: hand-written decoders on both runtimes, as before — two shape definitions that only a
  corpus could keep in step.
  INVARIANT: the committed JSON Schema equals the export of the client schema — the schema export
  test.
  INVARIANT: every schema pattern means the same on both runtimes — the server compiles a
  trailing `$` as end of input, as ECMA-262 reads it (Java's `^` already means start of
  input), and a pattern subset test rejects any construct whose meaning differs between the
  two engines.
- @validation @shape — The schema is strict and total: every top-level field, every sinner "1" to
  "12" (as named keys, so a padded, signed or out-of-range key is an unknown field), an identity's
  id, level and uptie, the ZAYIN slot, and the three skill slots are required; objects admit no
  other keys; every id matches its entity's id format (identity, E.G.O, theme pack, encoded gift)
  and the start gift keyword is an ASCII keyword name, or is null where the field allows it;
  numbers declared integer accept an exactly integral value, as JSON Schema defines integer.
  Every stored planner already has this shape, and the editor writes nothing else.
  REJECTED: keeping the blank-string and padded-key leniencies — they existed for shapes no stored
  planner holds and made the shape impossible to state in a standard schema.
  NON-GOAL: no data migration — no stored planner holds a shape this schema rejects.
- @validation @sinners — Deployment order rejects a repeated sinner and more than twelve entries.
  The shape rules that 143 stated beside it (a required skill plan, one key per sinner, ASCII
  digits) are now the schema's.
- @validation @codes @shape — A shape failure maps to the generic codes by the kind of failure
  alone (missing required field, unknown field, invalid field type, which also covers a malformed
  id and an integer outside the safe range), at the path of the offending field; an unknown-id code
  is left for a well-formed id the game data lacks; the per-subject codes that restated a shape rule (a missing sinner, identity or ZAYIN
  slot, an out-of-range or duplicate sinner key, an unknown E.G.O type or skill slot) leave the
  vocabulary. The client reports a missing field as a wrong type, so the mapper tells them apart by
  whether the input holds a value at that path.
  REJECTED: a path-keyed table mapping schema failures back to the subject codes — a second copy
  of the schema's structure that must be edited with it.
- @validation @tree — The server binds admitted content to a typed tree that rules read; the
  stored JSON stays what the entity writes, so the tree is a read model of it. Rules then see only
  well-shaped values, and no rule reads JSON.
  REJECTED: serializing the tree as the stored form — the serializer must preserve raw keys, null
  apart from absent, floors past the category's count and opaque notes, or unchanged resends read
  as changes and hidden floors are lost.
  NON-GOAL: floors past the category's count and section notes stay opaque JSON inside the tree.
- @planner @floors @boundary — At the draft and publish stages a shape failure on the content is
  returned alone and no rule runs; at the index stage the floors that parsed are admitted beside
  the violations, and a rejected object floor contributes the fields that parsed on their own (a
  numeric id as its text) so that the server's index matches, field by field, what the stored
  procedure's `JSON_TABLE` extracts. Floors at or past the category's floor count reach no rule
  and are stored untouched (136), but the schema checks their shape like any other floor, since
  every stored floor already has it.
  REJECTED: running the rules over the surviving fields at draft and publish — piles derived
  violations on top of the shape error.

## Takeaway
- takeaway: a leniency kept for data nobody stores costs a hand-written decoder on every runtime;
  count the stored shapes before keeping one.
