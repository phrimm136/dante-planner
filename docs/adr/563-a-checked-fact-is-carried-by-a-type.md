# 563 a-checked-fact-is-carried-by-a-type
epic: none · pr: none

## Decisions
- @parse @types @archunit — A fact that code after the check relies on is carried by a type annotated `@Parsed`, which only its own factory creates; the factory returns it or throws the refusal, and later code takes the type instead of the raw value. A void check leaves nothing behind, so a caller that skipped it compiles like one that did not.
  REJECTED: void checks kept in order by review — nothing in a signature says a check has run.
  NON-GOAL: refusing a line added to either list by hand — the diff of the list is where it is reviewed.
  REJECTED: a private record constructor instead of a rule — a record's canonical constructor is as visible as the record.
  INVARIANT: a `@Parsed` type is constructed only inside itself, lambdas, anonymous and local classes included, and is not an interface — `ParsedTypeRuleTest`.
  INVARIANT: the set of methods named require, check, validate, ensure or verify that return void only shrinks — `VoidCheckRuleTest` over a committed sorted list.
  INVARIANT: a fact about a row is used only under the lock or transaction it was read in, and is parsed again after a lock — unenforced.
  NON-GOAL: a void check that authorizes an action and leaves no value behind — it stays on the list.
  NON-GOAL: typed ids — an id carries nothing to validate, and the raw `Long` and `UUID` stay.
- @parse @types @archunit — A public method takes no boolean: a choice between behaviors is an enum or two methods, because a call site reads `true` without saying which behavior it picks.
  REJECTED: banning only a boolean the method branches on — whether a value is a choice or a fact is not visible to a rule over signatures.
  INVARIANT: the set of public methods with a boolean parameter only shrinks — `FlagParameterRuleTest` over a committed sorted list; constructors, one-parameter setters, overrides of library types and generated code are outside it.
  NON-GOAL: a boolean returned as a control signal — a signature does not tell it from an answer.

## Takeaway
- takeaway: a check whose result is thrown away has to be trusted at every later line; a type makes the compiler remember it.
