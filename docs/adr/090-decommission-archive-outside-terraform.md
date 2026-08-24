# 090 decommission-archive-outside-terraform
epic: none · pr: none

## Decisions
- @terraform @archive @decommission — the dump taken from a decommissioned environment's database is held in a bucket created by hand in the surviving account, deliberately unmanaged, hardened to the shape the managed buckets already use: public access blocked on all four settings, versioning enabled, default encryption, and no lifecycle rule. An archive exists to outlive the infrastructure, so it cannot be owned by the tool destroying that infrastructure, and a lifecycle rule that expires noncurrent versions would let the archive delete its own history. REJECTED: a stack in the surviving account — the same `destroy` verb that empties the old environment reaches the archive too, and a stack whose every other resource is gone invites exactly that cleanup. REJECTED: a bucket in the account being emptied — an archive stored inside the environment being destroyed is not an archive. REJECTED: the final RDS snapshot alone — a snapshot restores only into RDS, which makes it a rollback artifact rather than a record anything else can read.

## Takeaway
- takeaway: infrastructure-as-code should own what ought to be rebuilt, not what ought merely to persist. When a resource's entire value is surviving the teardown, being unmanaged is the property rather than the defect, and the reason it is unmanaged has to be recorded somewhere the teardown cannot reach.
