# 140 content-validators-return-violations-as-values
epic: none · pr: none

## Decisions
- @validation @convention — A planner content rule is a function from parsed input to a returned
  list of violations; a violation is a value carrying its code, a typed content path and a
  message; nothing is collected through a parameter, thrown per violation, or logged inside a
  rule. The forcing constraint: the floor module and the rest of planner validation encoded the
  same concerns two ways, paths were built by string concatenation in several grammars and then
  dropped before the result, and the nearest pattern a new validator copied was the old one.
  REJECTED: keeping the collector (`ValidationContext.reject`) — the path is logged and lost, and
  every rule depends on a mutable argument.
  REJECTED: a validation library (Vavr `Validation`) — the sealed result types already carry the
  accumulate-or-accept distinction.
- @validation @path — Content paths are a value type with typed segments (field, index, key),
  rendered to the existing dotted/bracket text only at the edge, and ordered segment by segment
  with numeric indices. The forcing constraint: string paths sorted lexicographically, so index 10
  preceded index 2, and three path grammars coexisted.
  REJECTED: string paths with a regex-parsing comparator — re-derives structure the builder
  already had.
- @validation @boundary — JSON node kinds are dispatched with `switch (node.getNodeType())`, not
  `isX()` chains.
  REJECTED: a type-pattern switch over `JsonNode` subclasses — Jackson 2's `JsonNode` is not
  sealed, so the switch needs a default and loses the compiler's exhaustiveness check.
- @validation @enforcement — The convention is enforced by backend ast-grep rules over the
  planner content packages, with no frozen exceptions: the rules join the build once the last
  violator is converted.
  REJECTED: freezing the old files and converting them when touched — the scope is one closed
  package, and a frozen list keeps the old shape as a nearest pattern.

## Takeaway
- takeaway: the pattern a codebase teaches is whichever one is nearest; make one module exemplary,
  make the rules mechanical, and leave no exception list for the old shape to live in.
