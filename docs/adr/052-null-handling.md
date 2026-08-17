# 052 null-handling
epic: none · pr: none

## Decisions
- @nulls @boundary — Nulls die once at the web boundary through bean validation; internal defensive re-checks of what the boundary guarantees are removed rather than standardized. A re-check re-asserts a proven fact and normalizes the silent-skip variant beside it. REJECTED: `requireNonNull` guards on every public service method — re-proves the boundary per method and trains readers to expect null-tolerant internals.
- @nulls @meaning — Null carries meaning only where the wire delivers it (a partial-update DTO's absent field); internal APIs express absence structurally — an absent element, an overload, a typed item — never a null argument. Documentation of a meaningful null lives away from the call site, which is where the bug happens. REJECTED: documented null-to-skip parameters — invisible at the call site, so intent and accident read identically.
- @nulls @revocation — Logout revocation accepts only the credentials actually present, as typed non-null items in a single atomic call; absence is exclusion from the call, not a null slot. A five-slot signature where any slot may be null to skip makes every call site a place where a real credential can silently go unrevoked. REJECTED: nullable positional parameters with null-to-skip — a misrouted null under-revokes with no observable trace. REJECTED: a builder with nullable setters — moves the null one hop without removing it.
- @nulls @effects — A null reaching a mutation is a bug and throws; only queries may answer their negative identity for a null input, and only where documented. A guard that silently no-ops on null converts a programming error into an unrevoked-credential gap, the failure direction guards exist to prevent. REJECTED: silent-skip guards on mutations — fail-open in the unaffordable direction.

## Takeaway
- takeaway: a nullable parameter is an invisible boolean every caller can set by accident; absence expressed structurally — excluded from the call, absent from the set — is absence the compiler and the reader can both see.
