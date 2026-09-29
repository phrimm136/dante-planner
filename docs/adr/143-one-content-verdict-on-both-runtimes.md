# 143 one-content-verdict-on-both-runtimes
epic: none · pr: none · supersedes: 030 @keywords @strictness

## Decisions
- @validation @parity — For every case of the shared content corpus, both runtimes return the
  same ordered list of (code, path), and the corpus holds that expected list; the client mirrors
  the document boundary the server applies to parsed content (unknown and required top-level
  fields, section note size, content version) and returns a boundary refusal alone, as the server
  does; the category is metadata the content check receives already typed. Guest documents and imports are judged by the client alone, so a boundary only
  the server applies is a second definition of acceptable content.
  REJECTED: scoping the comparison to codes both runtimes emit — the corpus then records its own
  exclusions, and a document the client accepts can still be refused on upload.
  INVARIANT: both runtimes return the expected list for every corpus case at every stage it runs
    — the backend and frontend content corpus replays.
  NON-GOAL: the client gains no string-level boundary (empty, malformed, root kind) and no
    whole-content size check — it receives parsed content, and those refusals stay on the server.
- @validation @keywords — Renames still remap; the unknown-keyword rule runs inside content validation at the publish
  stage, one violation per unknown element at its index, so it applies to publishing and to every
  save of a published planner. No stored planner holds an unknown keyword, and the client already
  rejects one on every publish-stage save, so the tolerance protected content that does not exist
  and let the two runtimes disagree on a republished save.
  REJECTED: keeping the check on first publish only — a published planner edited to an unknown
  keyword is refused by the client and accepted by the server.
  NON-GOAL: a draft save does not check unknown keywords — drafts of older content keep saving.
- @validation @numbers — A JSON number with an exactly integral value is an integer on the
  server, as it already was for floor difficulty. After parsing, the client cannot tell `60.0`
  from `60`.
- @validation @shapes — A container that must be an object and holds a string, a number or an
  array is an invalid field type, including a non-ZAYIN E.G.O slot; null in a required container
  (identity, E.G.O equipment, the ZAYIN slot) is that container missing, while null in an optional
  non-ZAYIN slot is an invalid field type, since the editor deletes an emptied slot; a blank
  string is "absent" only for a string-kinded field (the start gift keyword, a non-ZAYIN E.G.O id,
  which then leaves the slot empty); and no rule derives a violation from a value its boundary
  rejected (no skill total over a malformed slot, no missing-keyword report beside a wrong-kind
  keyword).
  REJECTED: treating a blank string as absent for every field — a blank identity or E.G.O
  container is a wrong kind, not an unequipped sinner.
  REJECTED: looking up a blank E.G.O id — reports an unknown E.G.O for a slot the editor leaves
  empty.
- @validation @references — An unknown entity is reported once, as unknown: an unknown start
  gift skips its keyword-pool check, and an unknown start buff skips its base-id range and
  duplicate checks, as an unknown floor gift already skips its affordability check.
  REJECTED: running shape checks on unknown ids — one bad id yields two or three violations whose
  fix is the same.
- @validation @gifts — An enhanced gift is its base gift plus an enhancement level: within one
  floor, a base and an enhanced form of one gift are duplicates, and start-gift pool membership
  compares the base id; across floors and in the observation and comprehensive lists, the forms
  are distinct, since enhancement happens across floors.
  REJECTED: comparing raw ids everywhere — a floor could list one gift twice, and an enhanced
  start gift would fall outside its own pool.
- @validation @sinners — The skill plan is a required top-level field. Two keys naming one sinner
  (`"01"` and `"1"`) are reported at every such key, and neither entry is checked further, because
  the runtimes enumerate object keys in different orders and so would pick different entries.
  Deployment order rejects a repeated sinner and more than twelve entries. A sinner key is ASCII
  digits with an optional sign; the server's integer parse also read other scripts' digits,
  which the client never did and no stored planner uses.
  REJECTED: silently keeping one entry — the server keeps the later key in document order and the
  client the padded key, so the two can validate different sinners.
  NON-GOAL: no data migration — no stored planner holds any shape this file rejects.

## Takeaway
- takeaway: two runtimes agree only on what one corpus pins with expected output; recorded output
  compared after the fact finds disagreements, but nothing prevents new ones.
