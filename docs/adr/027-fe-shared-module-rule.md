# 027 fe-shared-module-rule
epic: none · pr: none

## Decisions
- @shared @dual-consumer — A module consumed by both a slice and another domain stays shared and is never absorbed into the slice. REJECTED: pulling a dual-consumer module in with the domain that uses it most — the remaining consumer then imports it back out of the slice, forging exactly the inbound edge the slice boundary exists to prevent, and in the barrel case dragging the slice's dependencies into an eagerly-loaded module.
- @slice @public-api @router — External code reaches a slice only through its public entry module, internal imports within a slice are relative, and route components sit at the slice root where the router deep-imports them under a lint exemption. A slice gains a public entry only when something outside actually consumes it. REJECTED: a public entry per slice on principle — the barrel exists as the lint-permitted import surface for external consumers, so one with no external consumer is a dead boundary that only invites bad edges.

## Takeaway
- takeaway: before moving a module, trace who imports it rather than what it appears to be about. A second consumer in another domain converts an obvious relocation into a cycle, and forward tracing from the module you are moving is precisely the direction that cannot see them.
