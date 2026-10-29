# 493 the-edge-drops-accept-language
epic: none · pr: none

## Decisions
- @edge @gateway @backend — The backend HTTPRoute removes the `Accept-Language` request header before it reaches Spring, because the backend reads no request locale and Tomcat 11 rejects a request whose header lists more than ten locales: Spring's `RequestContextFilter` parses the locale on every request, so such a client failed every API call, with 500 when signed in and 401 from the secured error path when not. Re-check: `rg -n 'getLocale|LocaleContextHolder|LocaleResolver|MessageSource|Accept-Language' backend/src/main` finds nothing.
  REJECTED: raising the connector's `maxLocaleCount` — it keeps parsing an input nothing reads, and widens a limit Tomcat added against denial of service.
  REJECTED: truncating the header in a servlet filter — code to trim a value nothing reads, and it must run before `RequestContextFilter`; Gateway API can set or remove a header but not trim it.
  INVARIANT: no request through the backend route carries `Accept-Language` to the backend — the route's RequestHeaderModifier filter; unenforced by any test until a behavior test of the edge exists.
  NON-GOAL: server-side localization — a feature that needs the request locale replaces this filter with a bounded parse.

## Takeaway
- takeaway: drop an untrusted input the service never reads at the edge, rather than tuning the parser that chokes on it.
