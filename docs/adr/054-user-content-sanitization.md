# 054 user-content-sanitization

## Decisions

- @sanitization @api — User-content sanitization runs at the request-DTO boundary via a
  `@Sanitized(kind)` annotation on record components, backed by a contextual Jackson
  deserializer, plus a reflection guard test requiring every String component of a
  request DTO in the user-content features to carry `@Sanitized` or an explicit
  opt-out. The sanitizers sat unwired because nothing enforced wiring; the guard is the
  durable half of the fix.
  REJECTED: explicit sanitizer calls in services — the unenforceable honor system that
  already failed once.
  REJECTED: compact-constructor sanitization in the DTOs — same automatic effect, but
  invisible to reflection, so no guard can enforce it.
  REJECTED: entity/persistence-boundary sanitization — every feature touching the DB
  pays for a policy only three API surfaces need.
- @sanitization — Tiptap content is sanitized by schema allowlist (node types, mark
  types, attributes; unknown elements dropped) followed by the URL-protocol pass. The
  allowlists mirror the frontend editor extension sets, and a contract e2e test guards
  the drift, since no compiler crosses that boundary.
- @comment @format — Comment content is stored and wired as Tiptap JSON, converted from
  the legacy HTML rows by a one-shot Node script that imports the frontend's own
  extension set (`generateJSON`), run inside the deployment window. One format means
  one sanitizer and faithful re-edit round-trips; notification snippets switch to a
  JSON-to-plain-text walker.
  REJECTED: keep HTML with a Jsoup allowlist — a permanent second sanitizer and lossy
  edit round-trips.
  REJECTED: a dual-format reader behind a marker — a compatibility layer with no
  removal forcing function.

## Takeaway

- takeaway: a transformation policy without an enforcement artifact decays into dead
  code; place the policy where a test can see it.
