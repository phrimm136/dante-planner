# 132 rate-limiter-fails-open-except-auth
epic: none · pr: none

## Decisions
- @rate-limit @degradation — When the rate-limit store is unreachable, rate-limited requests proceed unmetered and a counter records each skipped charge, except on the authentication endpoints (login, OAuth callback, token refresh), which answer with the typed 503. One region's ephemeral rate-limit pod dying turned every public read in that region into a 503 while its database and auth store were healthy, which is the whole-service failure ADR 010 rejects; the limiter protects capacity, and an outage of its store is not an attack.
  REJECTED: failing closed everywhere — converts a cache outage into a total outage for anonymous readers who need nothing from the store.
  REJECTED: failing open everywhere — lifts brute-force protection from credential endpoints exactly while its store is down.
  REJECTED: a per-pod in-memory fallback bucket — keeps state in pods and meters each pod separately, so the effective limit multiplies by the pod count.

## Takeaway
- takeaway: a limiter's failure mode is a question about what the limit protects; capacity limits fail open, security limits fail closed.
