# 067 architecture-rule-freezing

## Decisions

- @architecture @conventions — An architecture rule is stated at the width the boundary
  actually has, and the classes that violate it today are frozen by name in a list the rule
  excludes; the rule is never narrowed until the tree passes it. A narrowed rule stops
  describing the boundary, so a new violation of the part that was carved away reads as
  compliant, while a named list keeps the full rule enforced against everything else and
  leaves what is outstanding enumerable.
  REJECTED: narrowing the rule to the currently-passing subset — the debt becomes invisible
  rather than paid, and nothing distinguishes a boundary nobody has crossed from one the rule
  no longer looks at.
  REJECTED: leaving the rule absent until the tree is clean — every new edge added in the
  meantime is one more to unwind, and the rule that would have stopped it does not exist.
- @architecture @conventions — Each frozen list is paired with a staleness test that fails
  once an entry no longer describes a real violation, so an entry cannot outlive the edge it
  excuses. An entry names a source file, so nested and compiler-generated classes are frozen
  with the class they came from.
- @architecture @controllers — Controllers exchange DTOs, never mapped entities, and the
  controllers that dereference one are frozen individually. An entity at the HTTP boundary
  couples the wire format to the mapping: returned, it serializes whatever the mapping
  exposes, so a new column silently becomes a response field nobody chose; bound as a
  parameter, the request writes onto the mapping directly.
  REJECTED: excusing the current violators with a narrower rule shape — unwinding one moves
  the read out of the handler and into the service it calls, which is a service-boundary
  change, so a rule shaped to permit it would permit the next one too.
- @architecture @features — A feature's `repository`, `validation` and `entity` packages are
  private to it; the cross-feature edges that already reach into them are frozen by name.
  REJECTED: allowing the edges as a permitted dependency — a permitted edge is a design
  statement, and these are outstanding work that a later pass has to reverse.

## Takeaway

- takeaway: a rule weakened to fit the code stops being a rule; a rule kept at full strength
  with today's exceptions written down stays a rule, and the exception list is the only
  honest measure of how far the code is from it.
