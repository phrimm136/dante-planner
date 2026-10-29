# 184 a-push-carries-what-it-announces
epic: none · pr: none · supersedes: 008 @sse @recipients

## Decisions
- @sse @payload — Every server-sent event carries the state it announces, and the client writes
  that state into its cache instead of refetching. An event that only says "something changed" is
  not allowed for any state. The client is invalidate-heavy and comment creation discards its own
  POST response, so client-side masking alone cannot cover a refetch that races replication.
  REJECTED: invalidate-then-refetch — the refetch runs on whatever replica the client reaches and
  costs a round trip the payload already paid; under 183 @freshness it is correct but slower, and
  correctness would then hang on every handler remembering the refetch.
  REJECTED: banning trigger-only events for user state alone — the same refetch race and cost apply
  to public state.
  INVARIANT: no event type is published without a payload — an architecture test over the event
  types.
- @sse @notifications — A notification push carries the notification and the recipient's new
  unread count, and the client writes both into its inbox cache. The insert is a server-side write
  the recipient never answered, so before 183 @freshness a refetch raced replication; the payload
  also saves the refetch.
  REJECTED: a shorter client stale time — a calibrated delay (ADR 008 @correctness @timing).
  INVARIANT: after a push, the client shows the pushed notification and count with no refetch —
  unenforced until a client cache test covers it.
  NON-GOAL: delivering missed pushes — a client without a push connection reads the inbox on its
  next load, fresh under 183 @freshness.
- @sse @settings — After a settings save responds, every device of that user applies the new
  settings: the save pushes an event carrying the full saved settings to the user's other devices,
  and each device writes them into its cache. The settings decide device behavior (server sync,
  and which notifications a device subscribes to), so a device that keeps old values keeps syncing
  or subscribing against the user's choice.
  REJECTED: no push, so other devices read settings on their next load or focus — a device that
  stays open keeps the old behavior until it reloads.
  INVARIANT: a settings push carries the saved settings, applied with no refetch — unenforced until
  the client and the SSE contract tests cover it.

## Takeaway
- takeaway: an event that only says "something changed" sends the reader on a round trip the
  event could have carried. Carry the value.
