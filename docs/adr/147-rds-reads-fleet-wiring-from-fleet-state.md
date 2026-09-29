# 147 rds-reads-fleet-wiring-from-fleet-state
epic: none · pr: none

## Decisions
- @terraform @state @rds @fleet — The rds stack derives its fleet wiring (the return routes over
  each peering and the 3306 ingress) from the oregon and seoul states, reading each state only
  when an exact-key listing of the state bucket shows its object exists, with every consumed
  output defaulting to "". The fleets peer to the database network, so rds applies before them
  and again after each; a value handed across by an operator is one that a later apply can omit,
  and an omitted value reads as "no wiring" and plans the deletion of live routes.
  `terraform_remote_state` fails outright on a state object that was never written, on the s3
  backend too, and `defaults` covers only outputs missing from a state that exists, so an
  ungated read breaks the first apply in any fresh account.
  REJECTED: required variables with an explicit "" opt-out — the rehearsal's first apply has no
  values to pass, and every prod apply still depends on a hand copy.
  REJECTED: seeding empty state objects before the first apply — a bootstrap step each new account
  and each rehearsal must remember, failing hard when forgotten.
  REJECTED: a wiring flag per fleet — two more inputs threaded through the stored tfvars and the
  rehearsal secret, reintroducing the operator-supplied state this removes.
  REJECTED: a prefix match on the listing — `oregon/terraform.tfstate.backup` shares the prefix,
  opens the gate while the state is absent, and restores the hard failure.
  INVARIANT: rds wiring equals what the fleet states hold at plan time, and an absent fleet state
  plans no wiring without error — unenforced; the stack's ephemeral master-password read blocks
  mock-provider plan tests, so a wrong gate surfaces only as route changes in a plan.
  INVARIANT: a fleet state that holds a peering id but no vpc_cidr fails the rds plan rather than
  planning an empty-CIDR route or deleting the live one — preconditions on the CIDR-consuming
  route and ingress resources.
  NON-GOAL: no override of the derived values — a wrong wiring is corrected in the fleet stack
  that owns it.

## Takeaway
- takeaway: a claim about how a missing input behaves has to be traced to the code that consumes
  it; the s3 client returning nothing for a missing key was true, and the data source turning
  nothing into an error was the half that decided the design.
