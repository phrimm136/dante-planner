# 055 comment-side-effect-coupling

## Decisions

## Superseded

- @comment @notification → 072 — Comment and reply notifications move to the
  publish-then-AFTER_COMMIT-listener pattern (the event carries the full snippet
  payload; the listener writes under REQUIRES_NEW), matching the recommended-planner
  notification path. A notification INSERT failure must not roll back the user's
  comment. Consequence accepted: notifications become at-most-once — a crash between
  commit and listener drops one, where the joined transaction made the pair atomic.
  REJECTED: keeping the notification inside the comment transaction — couples a
  secondary write's failure to the primary user action, and leaves two notification
  paths with opposite coupling policies.
- @comment @stats → 072 — The comment counter stays synchronous under the stats service's
  MANDATORY propagation, so the counter can never commit apart from the row it counts.
  REJECTED: event-based counter increment like the view count — sacrifices row/counter
  atomicity for a decoupling the hot view path needs and the comment path does not.

## Takeaway

- takeaway: side effects of one user action deserve per-effect coupling decisions — an
  effect whose failure should fail the action joins its transaction; every other effect
  leaves through an after-commit event.
