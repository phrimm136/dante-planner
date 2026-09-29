# 144 planner-content-is-written-only-as-a-parsed-document
epic: none · pr: none

## Decisions
- @validation @boundary — Content validation is a parse that returns a document type, and the
  planner entity's write path accepts only that type; the stored column stays a JSON string that
  a loaded row exposes unparsed. Validation that returned the same raw string left every writer
  free to store unchecked content, and the only guard was an architecture test that each writer
  depends on the validator.
  REJECTED: typing the entity column through a converter — a loaded row cannot claim to be
  valid, since a data release can leave stored content failing current rules, and loading would
  need the game data.
  REJECTED: keeping the string and the architecture test — it proves a dependency, not that the
  stored value came out of the parse.
  INVARIANT: no production path stores planner content except as a parsed document — enforced by
    the type: the content entity has no string writer; on the frontend, no code outside the parse
    asserts a `ValidatedPlanner` — `no-validated-planner-cast`.
  NON-GOAL: the editor's local write-through stays unvalidated — a draft mid-edit must save.
  NON-GOAL: a loaded row's content stays a string — see the rejected converter.
- @validation @metadata — Title (at publish), category and content version are parsed before the
  content, in that order, each refusing alone, and reach the content parse as separate typed arguments (a
  Mirror Dungeon category, a season that has game data, a stage); an unknown version is a
  refusal rather than one violation among the content's. The content parse checked a category
  and a version that the service had already checked as strings, so each was checked twice.
  REJECTED: one context object bundling the three — the category-only path needs a category and
  a stage but no season, so the bundle would carry a value that path must fake.
- @validation @stage — The stage is the publication state the write leaves the planner in: a
  save takes it from the planner, and only the publish action names publish itself, since it
  validates before the planner becomes published. Four client sites and two server sites each
  chose between the draft and publish entry points themselves.
- @sync @change — One comparison of the request with the already loaded row decides both an
  acknowledged no-op and a category-only change, and the server infers it rather than the client
  declaring it. A category-only change skips the game-data checks, so a client that could declare
  it could store unchecked ids; the row is already loaded inside the write, so the comparison
  costs no read.
  REJECTED: a client-declared change — the server would have to verify it against the row anyway.
  REJECTED: the two separate comparisons (the sync no-op predicate and the upsert's
  category-only test) — each parsed the stored and the requested content again.
  INVARIANT: a category-only change never runs the game-data checks over unchanged content, and a
    write equal to the row in every carried field is acknowledged without a write — `PlannerWriteParseTest` and `CategoryChangeFloorRulesTest`.

## Takeaway
- takeaway: a validator that returns its input unchanged proves nothing to the next caller; return
  a type only the validator can build, and make the write path demand it.
