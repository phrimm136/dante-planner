# 063 guarantee-ratchet-scoping

## Decisions

- @convention @archunit — The domain-exception construction ban on services exempts
  constructions inside lambdas. The exempted form is the load-or-404 supplier
  (`repository.find…(..).orElseThrow(() -> new …NotFoundException(..))`), which cannot
  move to a validator because validators receive already-loaded entities; the lambda is
  where "absent" becomes a domain error, which is loading, not validation.
  REJECTED: freezing the eight service classes that use the form — a name allowlist
  rots and stops biting on new inline if-throws in exactly those classes.
- @convention @archunit — The `@Validated` and constraint-annotation bans exempt
  `@ConfigurationProperties` binding classes. Binding-time startup validation runs
  without a proxy and fails fast at boot; it is not the method-validation AOP the
  guarantee-placement convention rejects, and the exemption walks enclosing classes so
  nested binding records stay covered.

## Takeaway

- takeaway: a ratchet earns permanence by carving out named, testable forms rather
  than named classes; forms stay true as code moves, class lists silently go stale.
