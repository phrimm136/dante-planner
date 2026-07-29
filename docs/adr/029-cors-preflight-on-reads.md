# 029 cors-preflight-on-reads
epic: none · pr: none

## Decisions
- @cors @preflight @latency — Bodyless GET and HEAD requests send no content-type header. That header is not CORS-safelisted, so setting it makes an otherwise-simple cross-origin request preflight, and on a cold trans-Pacific connection the first preflight took well over a second and blocked the entire authenticated GET burst queued behind it on the shared connection. The scope is GET and HEAD specifically rather than "whenever there is no body", which keeps write-request semantics untouched.
- @cors @writes — Requests carrying a JSON body keep the header and preflight correctly, since the preflight is a legitimate part of the cross-origin gate there. A multipart body is never forced to a JSON content type, because it must set its own boundary parameter.

## Takeaway
- takeaway: the measurement located the cost in the browser's CORS machinery while the server-side timing looked healthy throughout, so a request header nobody thought of as a performance decision was the dominant latency term. Layer-by-layer measurement found it; reasoning about the backend never would have.
