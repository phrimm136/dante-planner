# 061 guarantee-placement

## Decisions

- @convention @invariants — Guarantees live in types first, checks second, and every
  check runs exactly once. The ladder: (1) make the invalid state unrepresentable —
  primitive over box, enum over string, validated record whose compact constructor is
  the single gate; JPA entities are exempt from the pretense and lean on the schema.
  (2) Request input is validated by Jakarta annotations on the DTO — the `@Valid`
  pipeline is the only place an annotation executes; the response boundary is the
  consumer's schema, and the database boundary is the schema plus `@PostLoad` guards
  plus the drift audit. (3) Business rules live in validator components. (4) Residual
  invariants types cannot carry are `Assert` one-liners into the
  IllegalArgumentException bug lane. (5) Enforced by lint where lintable: no
  method-validation AOP, no `jakarta.validation` annotations outside dto packages
  (ArchUnit), downstream re-check idioms flagged by the forbidden-patterns hook.
  REJECTED: re-checking downstream of a gate — every defensive re-check is the ladder's
  rung 1 or 2 having been skipped, and it hides which check is authoritative.
- @convention @nullability — A declaration may be nullable only where null is data at a
  wire boundary, mapped to explicit types immediately on both sides; internal
  null-as-data (nullable columns encoding a third state) is a migration target, not a
  convention. Provably present values are primitive or non-null. A possibly-absent
  return value is `Optional`; `Optional` never appears on fields, components, or
  parameters. Callers never write null literals — a delegating overload or named
  factory spells the absent case at the definition. Boundary boxes remain where Bean
  Validation needs the box to detect absence.
  REJECTED: sentinel values in place of absence — a sentinel is a lie the type system
  repeats.
  REJECTED: `Optional` fields and parameters — absence there is spelled by overloads
  and factories, and JPA cannot map the wrapper.

## Takeaway

- takeaway: count the checks per guarantee; the correct number is one, placed at the
  outermost point where the value exists, with a type carrying the proof from there on.
