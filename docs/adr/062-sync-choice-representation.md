# 062 sync-choice-representation

## Decisions

- @schema @nullability @settings — The sync preference is stored as a non-null
  `sync_enabled` beside a non-null `sync_choice_made`, with a check constraint
  forbidding sync-on before the choice is made, and both fields ride the settings
  response. Every consumer but the first-login prompt collapses "not chosen" into
  "off", so the third state is a fact about the prompt rather than a third mode of
  sync.
  REJECTED: a `SyncChoice` enum column (UNSET/ENABLED/DISABLED) — one column, but it
  welds the prompt fact to the preference and makes every sync consumer spell
  `== ENABLED` for a distinction none of them draws.
  REJECTED: the nullable Boolean it replaces — null as data across the API boundary,
  which each consumer re-derives with its own `?? false`.

## Takeaway

- takeaway: when only one consumer reads a tri-state's third value, that value is a
  second fact about the same subject; split it off and the remainder is a boolean.
