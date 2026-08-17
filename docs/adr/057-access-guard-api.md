# 057 access-guard-api

## Decisions

- @planner @authorization — Planner access checks stay method calls on the access
  guard, split by return shape: `check*` methods are void pure authorization, and
  `require*` methods authorize and return the loaded aggregate. A call site that
  discards a `require*` return value converts to the `check*` form, which may verify
  by existence query instead of loading the aggregate. Thirteen of eighteen call sites
  consume the loaded entity, so the guard is also the load path; the split makes the
  remaining pure-authorization sites say so.
  REJECTED: annotation-driven checks (`@PreAuthorize` via the AOP starter, or a
  handler interceptor on the `@RateLimited` precedent) — the annotation layer would
  re-resolve an aggregate the services still need loaded, enforcing the guard twice
  with a double lookup, and method security adds a dependency plus self-invocation
  holes for nothing the call-site split does not already provide.

## Takeaway

- takeaway: a guard that usually returns the loaded aggregate is a load path with
  authorization attached; split the API by return shape rather than hiding the load
  behind an annotation.
