# 567 a-comment-is-split-by-who-writes-it
epic: none · pr: none

## Decisions
- @comment @decomposition @deadlock — A comment is a write-once core (`planner_comments`), a content row that its author and moderators write, and a stats row that holds the vote counter; the content and stats rows reference the core and nothing references them. A vote insert takes a shared lock on the row its foreign key names, so raising the counter on that same row upgrades the lock: two voters deadlocked in 20 of 20 forced pairs on MySQL 8.4.11, and in 0 of 20 with the counter on its own row.
  REJECTED: raising the counter before the vote insert — also 0 of 20, but the protection is the order of two statements and nothing holds the order.
  REJECTED: splitting the counter only — an edit or a withdrawal still writes the row that replies and votes reference.
  REJECTED: no foreign key from the new rows, as in the planner aggregate — the core is never rewritten by the transaction that inserts a child, so no lock is upgraded, and the cascade replaces a sweep.
  INVARIANT: a withdrawal lowers the planner's comment count exactly when its statement matched a row — `CommentVoteRaceIT`.
  INVARIANT: one vote per user and comment, stored before it is counted — the primary key and `CommentVoteRaceIT`.
- @comment @rollout — The first deploy copies the rows and the server also fills the old content column on insert, until a second deploy on the same day copies again, copies withdrawals, blanks the text of withdrawn and of erased accounts' comments in both places, recounts `planner_stats.comment_count`, and stops; old and new servers run together for minutes while comments arrive a few a day.
  REJECTED: writing both places from every command, with reads moved later — three deploys and code written to be deleted, for an overlap that expects well under one comment.
  REJECTED: a trigger that mirrors old writes — hidden writes, and the privilege is not known to exist on the production database.
  REJECTED: stopping writes during the migration — an outage for every user.
  NON-GOAL: an edit or a vote made by an old server during the overlap — it may be lost.
  NON-GOAL: a notification for a comment an old server made just before it stopped — a new server finds no split row, reads the comment as gone and closes the event.

## Takeaway
- takeaway: the row a foreign key points at is locked by every child insert; a counter or a text that changes does not belong on it.
