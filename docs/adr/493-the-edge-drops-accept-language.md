# 493 the-edge-drops-accept-language
epic: none · pr: none · supersedes: 011 @edge @traefik

## Decisions
- @edge @gateway @backend — The backend HTTPRoute removes the `Accept-Language` request header before it reaches Spring, because the backend reads no request locale and Tomcat 11 rejects a request whose header lists more than ten locales: Spring's `RequestContextFilter` parses the locale on every request, so such a client failed every API call, with 500 when signed in and 401 from the secured error path when not. Re-check: `rg -n 'getLocale|LocaleContextHolder|LocaleResolver|MessageSource|Accept-Language' backend/src/main` finds nothing.
  REJECTED: raising the connector's `maxLocaleCount` — it keeps parsing an input nothing reads, and widens a limit Tomcat added against denial of service.
  REJECTED: truncating the header in a servlet filter — code to trim a value nothing reads, and it must run before `RequestContextFilter`; Gateway API can set or remove a header but not trim it.
  INVARIANT: no request through the backend route carries `Accept-Language` to the backend — the route's RequestHeaderModifier filter, enforced by the PR Gate edge behavior test that serves the rendered routes through Traefik to a header-echo backend.
  NON-GOAL: server-side localization — a feature that needs the request locale replaces this filter with a bounded parse.
- @edge @traefik — The in-cluster ingress is retained as the edge's request policy point: the backend route's filters apply before Spring sees a request, and the node it runs on is kept for a future routing seam regardless, making its marginal cost one sub-millisecond hop and some manifest upkeep.
  REJECTED: pointing the tunnel straight at the backend service — technically leaner, but it discards the routing seam, the route's header policy, and the ability to run the stack without the edge provider in local development.

## Takeaway
- takeaway: drop an untrusted input the service never reads at the edge, rather than tuning the parser that chokes on it.
