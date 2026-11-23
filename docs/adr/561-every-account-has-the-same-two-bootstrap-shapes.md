# 561 every-account-has-the-same-two-bootstrap-shapes
epic: none · pr: none · supersedes: 033 @terraform @state @migration

## Decisions
- @terraform @state @bootstrap @management — The state bucket of the first account moves into `state-backend`, and that account's `iam-bootstrap` stack is retired. Every account then has one shape: `state-backend` owns the bucket, and `iam-bootstrap` exists only where a fleet runs. The first account became the management account and lost its fleet, but its bundled stack kept a role that can build one, in the account that holds the organization. The earlier decision left the bundle alone because the inconsistency was expected to end when that account was rebuilt. It was not rebuilt. It changed its job, and the role stayed.
  REJECTED: a switch that turns the identity off in the bundled stack — the management account stays a special case of a stack whose reason to exist there is gone, and the bucket switch and its code stay for one account.
  REJECTED: leaving the bucket where it is and deleting the role by hand — nothing re-asserts a manual deletion, and the next full plan of that stack offers the role again.
  REJECTED: keeping the earlier refusal to move a protected resource between states — the move is now declared in code with an import on one side and a release without deletion on the other, so each side shows as a plan before it happens, and nothing in the account changes but one tag.
  INVARIANT: the bucket is never without protection against deletion while it is managed — the release from the old stack happens in a plan limited to the bucket's five addresses, and the plan that removes the old stack is made only after that release; the plan guard refuses the removal plan while it still lists the bucket.
  INVARIANT: `iam-bootstrap` declares no bucket — the move script reads each stack's tracked files and refuses a state with an address the stack does not declare, and its tests pin both directions against the real stacks.
  NON-GOAL: keeping the import and release blocks — they are for this one move, a new account's first plan fails while the import block exists, and they are deleted once every account's states are past them; the ledger holds that condition.

## Takeaway
- takeaway: an inconsistency that is left to expire needs the event that ends it written down; when the account changed its job in place of being rebuilt, the exception had no owner and outlived its reason.
