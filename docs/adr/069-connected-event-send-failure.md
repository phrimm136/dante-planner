# 069 connected-event-send-failure

## Decisions

- @sse @errors — No SSE service carries a hook for a failure of the initial `connected` event;
  delivery failure reaches the stream through `onError` like any other. The event is buffered
  until Spring attaches the emitter's handler, so its send cannot fail at the point it is
  issued.
  REJECTED: the `onConnectedSendFailure` hook that existed — it sat on a path with no failure
  mode, so the tests around it asserted on a state the runtime cannot produce, and its presence
  implied registration-time delivery was something a caller had to handle.

## Takeaway

- takeaway: an error hook on a path that cannot fail is worse than no hook: it produces
  coverage that passes, describes a failure mode that does not exist, and draws the next
  reader's handling away from the path where the failure actually lands.
