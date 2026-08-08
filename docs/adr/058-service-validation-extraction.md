# 058 service-validation-extraction

## Decisions

- @validation @services (taste) — Every inline business-rule if-throw in a service
  moves to a validator component, single-use checks included; service bodies read as
  orchestration, and a rule's home is decided by its kind, not by how many copies
  exist. The duplicated checks (author-ownership ×3, category validity ×3,
  sync-version conflict ×2, planner limit ×2, owner ×2, deleted-top-level-reply ×2)
  converge to one copy each as a consequence rather than as the goal.
  REJECTED: extracting only the duplicated checks — leaves two legitimate homes for
  the same kind of rule, so the next single-use check lands inline by default and the
  divergence regrows.

- @validation @services — Pure-input rules stay validator components too; Jakarta
  constraint annotations on request DTOs are not used for business validation.
  REJECTED: custom `@Constraint` annotations for the pure-input subset — the exception
  handler collapses every constraint violation to one generic wire code (erasing live
  codes), class-level violations produce object errors the handler drops, and Bean
  Validation runs before the restriction checks, inverting the response-ordering
  contract for restricted actors.

## Takeaway

- takeaway: extraction driven by duplication count keeps the inline form as the
  default and guarantees re-divergence; extraction driven by kind removes the default.
