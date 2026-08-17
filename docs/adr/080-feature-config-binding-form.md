# 080 feature-config-binding-form
epic: none · pr: none

## Decisions

- @config — Feature configuration binds through @ConfigurationProperties classes with one
  binding site per key: jwt rotation settings nest under the existing jwt prefix owner, and keys
  bound independently in multiple classes converge to one. REJECTED: per-class @Value bindings —
  the same key bound in three places is a drift hazard no compiler checks.
- @convention @archunit — Constructor arity gets a form ratchet, not a number: @Value
  constructor parameters are banned outside @ConfigurationProperties classes, flipping to
  blocking at a zero baseline. REJECTED: a numeric arity cap — a frozen over-cap class growing
  worse adds no new violation, so freezing is blind exactly where it matters. REJECTED: an
  external analyzer rule — a second toolchain gate for a rule the architecture suite already
  expresses.

## Takeaway

- takeaway: a threshold measures a symptom and invites exemptions; the shape of the mechanism
  producing the symptom is what a test can ban outright.
