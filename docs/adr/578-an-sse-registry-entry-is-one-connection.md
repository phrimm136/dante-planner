# 578 an-sse-registry-entry-is-one-connection
epic: none · pr: none · supersedes: 042 @sse @identity

## Decisions
- @sse @identity — An SSE registry entry is one connection, identified by its emitter: registering removes no other entry, and an entry leaves only on its own stream's completion, timeout or error, its own failed send, or its own failed heartbeat or probe. The device cookie is one value per browser while a browser holds one stream per tab, so an entry keyed by device let a second tab's subscribe drop the first tab's entry without closing its stream, and let that stream's end drop whichever entry then held the device id. A stream outside the registry receives no heartbeat, is cut by an intermediary after about two minutes of silence, and its reconnect repeats the cycle; in production this showed as reconnect gaps of 127 to 132 seconds on about a fifth of connected devices and as 429s for a user with tens of tabs. The device id stays on the entry as a log label and decides nothing.
  REJECTED: keeping the device as the key and completing the replaced emitter — the client reconnects a closed stream within seconds, so two tabs would close each other without end; it is the eviction cascade of the capacity decision with one browser on both sides.
  REJECTED: keeping the device key for reconnect de-duplication — a connection the client abandoned fails its next heartbeat and leaves within one sweep interval, so the key saved one short-lived entry per reconnect and cost every multi-tab browser its stream.
  REJECTED: a connection id minted by the client — it is one more client-writable value, and the server already holds an identity no client can name, the emitter.
  INVARIANT: a stream the server holds open is in the registry — SseSameDeviceStreamsTest, SseEmitterRegistryAtomicityTest.
  INVARIANT: ending one connection never removes another — SseSameDeviceStreamsTest.
  NON-GOAL: bounding the streams one account holds — a client that opens one stream per browser bounds it at the source; until it does, a browser holds one stream per tab.
- @sse @capacity — A comment registry at its cap refuses every arrival, a reconnect from a device already holding a slot included. With no device key there is nothing to recognise a reconnect by, and the stale entry it would have replaced leaves at the next heartbeat; the refused client retries after its own delay.
  REJECTED: recognising the reconnect by device and admitting it — the cookie is client-writable, so the exemption is a way past the cap for anyone who repeats a device id that holds a slot.

## Takeaway
- takeaway: a de-duplication key must be at least as fine as the thing it de-duplicates; a key coarser than the connection turns one client's second connection into the first one's eviction.
