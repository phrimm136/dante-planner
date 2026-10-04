# 169 backend-modules-meet-through-published-apis
epic: none · pr: none · supersedes: 018 @cross-feature @archunit @allowlist

## Decisions
- @modules @api — Each backend module publishes an API, and other modules can import only that
  API. Entities, repositories and services stay internal. The module graph has no cycles. The
  feature boundary test named each forbidden edge by hand. Every new module needed new rules.
  Shared JPA entities also let one module change another module's data.
  REJECTED: layers by kind (feature, concept, platform) — a class that several modules use must
  split before it can move down. The split follows the importers, not the subject.
  REJECTED: layers computed from the current import graph — such a layer follows today's edges,
  not the kind of code. A new edge then moves modules between layers.
  REJECTED: Spring Modulith as the enforcer — ArchUnit is already in the build and enforces the
  same two rules.
  INVARIANT: an import of another module's internals, or a cycle between modules, fails the build
  — ArchUnit rules over the module list.
  INVARIANT: an api package imports no internal type of its own module, so a caller never needs an
  internal to use an API member — an ArchUnit rule over each api package.
- @modules @relations — The kind of need decides the means. A module reads another module's data
  through that module's API and stores references as ids. It changes another module's data only
  through an API command. It reacts to what happens in another module through an event. A lower
  module that needs a higher module to act declares an interface that the higher module
  implements.
  REJECTED: calling across modules for reactions — the caller then imports every module that must
  react, which formed the user to planner, comment and moderation edges.
  INVARIANT: an allowlist freezes the edges that break the rules on the day they land. An entry is
  deleted with its edge — the allowlist staleness check.
- @modules @naming — A provided API is named for what it offers, plural for a collection
  (`Users`, `PublishedPlanners`). An interface one module declares and another implements ends in
  one of four role words: Provider (supplies values the declarer reads), Participant (takes part in
  a process the declarer runs), Target (receives actions the declarer takes), Handler (receives what
  the declarer dispatches). Mixed names did not say whether a caller calls the type or implements
  it.
  REJECTED: free agent nouns ending in -er or -or — each new interface reopens the naming choice.
  REJECTED: exempting data-type interfaces from the role-word rule by a list — a list needs a
  person to judge each entry; a `@TypeLabel` annotation whose implementers must be data types is
  checked by the build. A type label marks what kind of data a type is (ValuedEnum, FailureUnion);
  it performs nothing, so no role word fits it.
  INVARIANT: an interface implemented outside its module either ends in a role word or carries
  `@TypeLabel` — an ArchUnit rule.
  INVARIANT: only data types (enums, records, interfaces) implement a `@TypeLabel` interface — an
  ArchUnit rule.
  NON-GOAL: abstract classes that another module extends — a framework type such as a servlet
  filter keeps its framework suffix.
- @modules @reads — A module that other modules join in queries publishes a read-only entity over
  its own table, with only the columns it agrees to share. A query in another module joins that
  entity by id in the same statement. Hiding the entity alone turned each such join into a second
  query.
  REJECTED: a second query through the API per page — it adds a round trip to every list page.
  REJECTED: copying the shared columns into the reading module — each copy needs an event to stay
  current and is stale until the event lands.
  NON-GOAL: separate schemas or grants per module — one schema keeps the foreign keys and cascades.
- @modules @platform — The shared package holds only code that knows no domain. Its parts that read
  module entities or serve endpoints move into a module.
  REJECTED: one shared package for everything that several modules use — usage count is noise for
  infrastructure and evidence for domain code, so the bucket mixes the two.
  NON-GOAL: moving controllers to their own layer — no code imports a controller.

## Takeaway
- takeaway: sharing an entity is not the coupling; sharing a mutable one is. Publish what other
  modules can read, and keep what they can change behind a command.
