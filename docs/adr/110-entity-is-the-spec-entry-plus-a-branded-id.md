# 110 entity-is-the-spec-entry-plus-a-branded-id

## Decisions
- @entity @spec — Every database section's entity is its spec entry copied key for key, plus a branded `id` and an optional `name`, produced by one shared builder that spreads the entry and never names fields. A hand-written per-section mapping silently dropped `fusioned` when five copies were folded into one, and the facet reading it returned an empty grid with every test green.
  REJECTED: one constructor per section with a declared spec-to-item field map — a second artifact to keep in step with the schema, which is the same drift one level up.
- @entity @rename — Entities keep the spec's key names and carry every spec key, so `attributeType` stays singular on identity and EGO entities and EGO entities carry `requirements`. A rename or an omission is exactly a hand-written mapping.
  REJECTED: keeping the plural entity key names because they read better on cards — readability at the card is not worth a mapping the conformance test cannot see through.
- @entity-section @discovery — Sections are discovered by a file glob over each section's `entitySection` module, and a guard asserts every section that ships a facet table also ships that module. A registry array is one more edit per section, and the forgotten edit is its failure mode.
  REJECTED: a hand-kept registry — silent omission is its failure mode, the same one the glob exists to close.
- @entity-section @fixture — The conformance test builds entities from the real static spec JSON and, per schema key, picks the first entry that sets it; a key no entry sets fails the test. A hand-written full fixture is a per-field edit that goes stale exactly when a field is added.
  REJECTED: a per-section fully populated fixture — the edit it demands is the drift under test.
- @facet @fallback — Facet getters carry no `?? []` on a field the spec schema marks required. The fallback turns a missing field into a defined empty value, so the conformance check that every getter returns a defined value would pass on the exact bug it exists to catch.
  REJECTED: keeping the fallbacks as defense in depth — they and the test cancel each other.
- @keyword @id — Battle keyword ids get an entity-id brand like every other section, so the shared entity type can require a branded id without a keyword exception.
- @entity @naming — Per section the input type is `<Noun>Spec` (one entry of the spec record, schema `<Noun>SpecSchema`), the output type is `<Noun>Entity`, and the builder is `to<Noun>Entity`; the shared factory is `createEntityBuilder`. Six sections had five suffixes for the input role, two of them ending in `ListItem`, the word for the output.
  REJECTED: keeping `ListItem` for the output — the grid is one consumer; the planner and the encoders consume the same object and are not lists.
- @entity @singular-builder — Builders are singular in name and operation: each section's entity module exports only `to<Noun>Entity` values produced by `createEntityBuilder` (plus a hand-written `toUnknown<Noun>Entity` placeholder), no identifier `to<Noun>Entities` exists anywhere, and plural work is a `map` at the call site. A lint rule scoped to `pages/*/lib/*Entity.ts` enforces it, because "builder" is a role the linter can only recognize by location.
  REJECTED: a per-section plural helper taking the record and the name catalogue — it is a second signature to keep uniform, and the six copies had already diverged on the i18n parameter's type.

## Takeaway
- takeaway: a consolidation that leaves the shape different per section only moves the drift; the shape itself has to be one thing, and the test has to read that one thing from the schema and the data, never from a hand-kept list.
