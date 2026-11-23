# 555 an-entity-fields-nullness-mirrors-its-column
epic: none · pr: none

## Decisions
- @nullness @jpa @schema — An entity field is typed `@Nullable` exactly when its column is NULL-able, and every other persistent field is declared non-null to JPA (an id, `nullable = false`, `optional = false`, or a collection mapping). JPA fills entity fields by reflection, so NullAway treats entities as externally initialized and trusts whatever the field declares; a spike of the compiler check compiled `publication.getFirstPublishedAt().getNano()` without a warning although `first_published_at` is a NULL column.
  INVARIANT: every persistent field of an `@Entity` or `@Embeddable` declares its nullness one way and not both — `EntityNullabilityRuleTest` (fixture `org.danteplanner.fixture.entitynullability`).
  INVARIANT: the declared nullness equals `information_schema.columns.IS_NULLABLE` of the migrated schema for every mapped column — `EntityNullabilitySchemaIT`.
  NON-GOAL: `@Transient` fields and primitives — they map no nullable column.

## Takeaway
- takeaway: when a checker trusts a declaration it cannot verify, the declaration needs its own check against the thing it describes.
