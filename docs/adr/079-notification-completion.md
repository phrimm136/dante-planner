# 079 notification-completion
epic: none · pr: none

## Decisions

- @notification — Notification construction goes through named per-type static factories, and
  the type-parameterized constructors leave the public surface. REJECTED: JPA inheritance — the
  highest-volume write path is a native fanout insert that never constructs the entity, and the
  read side never dispatches on type.
- @notification @report — REPORT_RECEIVED is wired rather than deleted: recipients are
  admin/moderator users, producers are the planner and comment report services, and the dedup
  key (user_id, content_id, notification_type) collapses repeat reports of one subject into one
  unread notification. REJECTED: deleting the enum value — the type was intended, not
  accidental. REJECTED: notifying the reported content's owner — invites retaliation.
- @notification @gates — Every NotificationType value must have a producer, a DomainEventType
  counterpart, and an effect arm, enforced by a matrix test. REJECTED: review-time vigilance —
  an unwired enum value compiles and ships unnoticed, because every existing gate sees only what
  exists.
- @notification @recipients — A notification row is written only for a recipient whose account is live: every insert selects the recipient from `users` with `deleted_at IS NULL`, as the publication fanout already did, so no arm has to remember the check. A deactivated author's comment stays in the tree and still accepts replies through the API, so a recipient can be deactivated at any point before an arm runs. REJECTED: a liveness check in each effect arm — covers only the arms that remember it, and every new notification type starts without it.

## Takeaway

- takeaway: an enum value compiles whether or not anything produces it; only a matrix test makes
  a declared-but-unwired variant loud.
