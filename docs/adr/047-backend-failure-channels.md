# 047 backend-failure-channels
epic: none · pr: none

## Decisions
- @errors @transactions — Sealed failure unions never cross the transaction proxy; a failure that must undo writes travels as an unchecked throw, and unions exist only at pure-decision seams, seams whose state is not JPA-managed, or non-transactional facades serving callers that branch. The proxy keys rollback off unchecked exceptions, so a returned failure value commits whatever the method wrote before deciding to fail. REJECTED: returning the value plus `setRollbackOnly` — reintroduces forget-the-call corruption and poisons enclosing transactions into `UnexpectedRollbackException` far from the cause. REJECTED: ordering all writes after all decisions as the sole guarantee — nothing detects the future seam that writes first.
- @errors @boundary — The web boundary keeps exceptions routed to the global advice handler as its failure channel, one adapter per control-flow jurisdiction. Filters throw outside the advice layer's reach, so the exception channel cannot be eliminated even in principle. REJECTED: unions returned through controllers — re-implements the advice layer's mapping at every call site.
- @errors @library — Failure unions are Java 21 sealed interfaces with records and exhaustive switch; no FP library. The compiler's exhaustiveness check on sealed types already fails the build when a variant is added unhandled. REJECTED: Vavr — a foreign `Option`/`Try`/collection hierarchy fights Jackson, JPA, and Spring signatures for a guarantee the language ships.

## Takeaway
- takeaway: a framework that owns control flow defines the failure currency inside its jurisdiction — the transaction proxy's is the unchecked throw exactly as a query cache's is rejection — so choosing a failure channel inside a transaction is choosing rollback semantics.
