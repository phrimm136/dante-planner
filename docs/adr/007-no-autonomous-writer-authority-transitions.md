# 007 no-autonomous-writer-authority-transitions
epic: none · pr: none

## Decisions
- @rds @redis @failover @quorum (taste) — No component promotes a new write authority on its own. Synchronous same-site failover (RDS Multi-AZ) covers the mundane case; a human-executed promotion runbook covers the geographic case. REJECTED: automatic cross-region promotion — correct automatic failover of a stateful primary requires quorum machinery, and below the scale that justifies that machinery an automatic promotion is a coin flip on split-brain.
- @redis @outage @degradation — A Redis outage is a typed 503, then wait for auto-recovery and AOF replay. REJECTED: Sentinel plus a third-region witness — auto-promotion of auth state costs more machinery than the outage costs, and is retained only as a documented later exercise.
- @rds @multi-az @sequencing — Multi-AZ is enabled after the migration cutover, never during seeding. REJECTED: enabling during seed — a synchronous standby slows the catch-up it is meant to protect and doubles the bill while serving no one.
- @rds @replica @parameter-group — The cross-region read replica runs a region-local parameter group at parity with the primary: `gtid_mode=ON`, `enforce_gtid_consistency=ON`, `require_secure_transport=1`. REJECTED: default replica parameters — the replica is the promotion target, so it must already carry the primary's posture at the moment it is promoted, not acquire it afterwards.

## Takeaway
- takeaway: automatic failover is a quorum feature wearing a convenience feature's clothes. Without a quorum layer underneath, "automatic" means "unsupervised", and every cross-region authority change should be a deliberate act with a rehearsed runbook.
