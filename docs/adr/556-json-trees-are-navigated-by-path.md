# 556 json-trees-are-navigated-by-path
epic: none · pr: none

## Decisions
- @nullness @json @jackson — Main code reads a Jackson tree with `JsonNode.path(String)` and `path(int)`, never `get`. Jackson 3 publishes no nullness annotations, so NullAway treats `get`'s return as non-null and `node.get("k").asInt()` compiled without a warning on a missing key; `path` returns a `MissingNode` instead of null, and a `MissingNode` answers false to `isObject`, `isArray`, `isString`, `isValueNode` and `canConvertToLong`, so the type checks that already guarded each read keep their behaviour.
  INVARIANT: no main class calls `JsonNode.get(String)` or `get(int)` on any `JsonNode` subtype — `JsonTreeNavigationRuleTest` (fixture `org.danteplanner.fixture.jsontree`).

## Takeaway
- takeaway: an unannotated library is non-null to the checker, so its nullable returns need a rule of their own or a call that cannot return null.
