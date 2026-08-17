# 020 index-discipline
epic: none · pr: none

## Decisions
- @index @access-path (taste) — Indexes serve access paths, not individual queries, and every index is write-path debt on a write-hot table. Prefer few multi-purpose composites over bespoke covering indexes; selectivity decides which column earns a place, so a near-universal predicate earns nothing. EXPLAIN under realistic data volume is the gate — static analysis only generates candidates, it never justifies one. Rare-path queries such as moderation dashboards are allowed to scan.
- @index @epoch @replacement — Indexes evolve by replacement per catalog epoch rather than by accretion: the wide replacement is created before the narrow predecessors are dropped, and a drop proceeds only once EXPLAIN plus usage statistics under a realistic profile confirm the redundancy. REJECTED: adding an index per new query shape — it is how a write-hot table accumulates an inventory nobody can account for.
- @index @inventory — The inventory stays small enough to narrate, with each index nameable by the access path that owns it. An index nobody can name a purpose for is a candidate for removal, not a mystery to preserve.

## Takeaway
- takeaway: an index is a permanent tax on every write in exchange for a discount on some reads, so the question is never "would this query be faster" but "which access path is buying this, and is that path worth taxing the autosave for".
