# 574 a-notification-is-unread-or-read
epic: none · pr: none

## Decisions
- @notification @state — A notification is unread or read, stored as `read_at`, written once. The page only ever deleted: before this change production held 14,410 unread rows, 1,758 deleted rows and none with `read` = 1, so "deleted" was the read state and the 90-day rule for read rows never matched. A read notification is not listed. Re-check: `ops sql "SELECT COUNT(*) FROM notifications WHERE read_at IS NOT NULL"`.
  REJECTED: keeping read and deleted as two states — every command must then say what it does to the other, and nothing used the difference.
  REJECTED: listing read notifications — the list shows the newest 20 without paging, so read ones would push unread ones out.
  INVARIANT: the first `read_at` stays when marks overlap — `NotificationReadRaceIT`.
  NON-GOAL: removing old unread notifications.
- @notification @deadlock — Marking all reads the unread ids without locks, then updates by primary key in ascending order. One update by user locks the new index before the row, and marking one locks the row before that index: 16 and 18 of 20 rounds of the two together ended in a deadlock on MySQL 8.4.11, none in this form.
  REJECTED: `FORCE INDEX (uk_notification_dedup)` — locks every row of the user, read ones included, and depends on a hint.
  REJECTED: no index on `read_at` — the inbox and the unread count lose theirs.
  NON-GOAL: a notification that arrives between the two statements — it stays unread.
- @notification @rollout — V070 copies `deleted_at` into `read_at` and the new server reads and writes `read_at` only. `DELETE /api/notifications/{id}` and `/all` mark as read until the frontend no longer calls them. Removal condition: the frontend release on the mark-read routes is live, and the DELETE routes then have no call for 24 h in `http_server_requests_seconds_count`; `read` and `deleted_at` are dropped after a deploy that does not map them is on every server.
  REJECTED: writing both columns during the overlap — code written to be deleted, against a notification that shows once more.
  NON-GOAL: a notification dismissed on one version while both run, or before a rollback — it may show again.

## Takeaway
- takeaway: two flags that no screen tells apart are one state with two ways to disagree.
