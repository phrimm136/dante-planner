# 088 tombstone-evidence-purge
epic: none · pr: none

## Decisions
- @sync @storage @tombstone — The pull pass purges a local planner row only when the sync
  listing carries that row with a `deletedAt` tombstone, and keeps every local row the
  server does not list; a local draft survives even a tombstone, since pulling the deletion
  would discard unsaved edits. The retired heuristic (purge an absent row when
  `status==='saved' && savedAt!==null`) read those fields as witnesses of a prior upload,
  but every manual save stamps both whether or not a push happened and no flow bulk-uploads
  local rows, so absence also described sole-copy rows saved while signed out or sync-off
  and forks whose best-effort upload was swallowed — and purging on that reading destroys
  the only copy. REJECTED: repairing the witnesses with a server-acked marker persisted at
  ack time — it keeps deletion an inference and adds a field whose only job is making the
  inference safe, when `planner_content.deleted_at` already exists so cross-device pulls
  see the deletion without a join.
- @sync @wire @rollout — Tombstoned summaries ride the existing own-list behind an opt-in
  `includeDeleted` query parameter, serialized with `deletedAt` omitted-when-null so the
  live-row wire shape is byte-identical for deployed strict Zod clients, making rollout
  order-free: an old client never asks and sees today's wire, a new client against an old
  server merely sees no tombstones and purges nothing. REJECTED: a separate deleted-ids
  endpoint — a second paginated view of the same truth can disagree with the listing
  mid-sync. REJECTED: emitting `deletedAt` unconditionally — strict clients reject an
  unknown key.
- @sync @accounts — Consequences accepted: a device offline past tombstone retention (or a
  hard-deleted row) keeps a zombie local row; local-only rows accumulate until a push flow
  exists; and the local store being account-unscoped means nothing purges another
  account's rows anymore, so a second account on a shared device keeps the first account's
  planners in its list indefinitely, where the retired heuristic used to remove the synced
  ones by accident. Scoping local rows to an account is its own pending decision.

## Takeaway
- takeaway: deleting user data on inferred evidence is asymmetric risk — the inference's
  false positive destroys a sole copy while its false negative costs a stale row; deletion
  wants positive server evidence, and the server can state the fact for the price of one
  nullable field on an existing listing.
