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

## Takeaway

- takeaway: an enum value compiles whether or not anything produces it; only a matrix test makes
  a declared-but-unwired variant loud.
