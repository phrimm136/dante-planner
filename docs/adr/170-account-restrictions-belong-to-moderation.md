# 170 account-restrictions-belong-to-moderation
epic: none · pr: none

## Decisions
- @moderation @restrictions — Moderation owns an account's restriction state. The timeout end, the
  ban time and the banning actor live in a table that moderation owns. The restriction check and
  its errors are part of the moderation API. Moderation alone writes these values, and it
  wrote them through setters on the user entity, which the published-API rule forbids. The policy,
  the audit and the action records already live in moderation.
  REJECTED: the user module keeps the columns and exposes commands for them — the user API then
  carries four commands that only moderation calls. The state also lives apart from the policy
  that decides it.
  INVARIANT: no module outside moderation writes restriction state — the published-API rule, once
  the columns leave the user entity.
- @moderation @migration — The columns move in a coexistence window. Moderation first writes the
  new table and the old columns together, then reads move to the new table, and the old columns are
  dropped last. The drop is the irreversible step, so it waits until reads come only from the new
  table.
  REJECTED: one migration that moves the data and drops the columns — a failed deploy after the
  drop has no path back to the old reads.

## Takeaway
- takeaway: data belongs with the module that decides it, not with the row it describes.
