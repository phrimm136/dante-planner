# 068 security-filter-servlet-registration

## Decisions

- @security @filters — A filter the security chain wires and component scanning finds is
  removed from the servlet container with a disabled `FilterRegistrationBean`, so it runs only
  where the chain places it. Boot registers every `Filter` bean with the container, so such a
  filter otherwise runs twice per request: once at the servlet level, ahead of the chain and
  outside its ordering, and once inside it — the servlet-level pass authenticating before the
  CSRF check meant to precede it, or logging with a `SecurityContext` nothing has populated
  yet.
  REJECTED: relying on `OncePerRequestFilter`'s dedup attribute, which hides the second run
  today — a filter that stops extending it, that clears its attribute, or that needs state the
  servlet-level pass has not produced loses the dedup silently, and nothing about the loss is
  visible in a passing suite.

## Takeaway

- takeaway: when a framework's convenience behaviour happens to cancel out a defect, the
  defect is still there and the cancellation is the part that can be refactored away; remove
  the cause rather than depend on the coincidence.
