# 050 validation-traversal
epic: none · pr: none

## Decisions
- @validation @traversal — Dynamic-JSON type and uniqueness branching lives in one internal-iterator helper that emits the contractual messages; validators receive only valid elements with their index and keep the domain membership checks. Validation is accumulate-and-continue with byte-identical, path-bearing messages, which fail-empty or short-circuiting chains cannot express. REJECTED: `Optional`/stream chains per validator — `Optional` forgets the value, index, and reason the message contract requires, and monadic short-circuiting drops accumulated errors. REJECTED: absorbing domain checks into the helper — leaves empty-forwarder validators and puts a domain dependency inside a generic module. REJECTED: a JSON Schema library — cannot emit the byte-identical messages.
- @validation @classifiers — Backend classifiers are pure functions from input to a union value; reactions live with the consumers of the classified value. A classifier that fires side effects cannot be table-tested without mocks and hides IO behind a name that promises a mapping. REJECTED: reacting inside the classifier — one hidden side effect per classification site.

## Takeaway
- takeaway: an error-accumulating traversal is a fold with a rejection side channel, not a map; the type branch on dynamic input cannot vanish, only move to exactly one owner.
