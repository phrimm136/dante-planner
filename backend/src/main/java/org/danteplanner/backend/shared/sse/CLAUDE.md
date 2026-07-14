# SSE

- Emitters live in a per-key registry: extend `AbstractSseService<K>` (ConcurrentHashMap keyed by user/planner ID, with per-device de-dup). Never build a parallel emitter list or a flat broadcast collection.
- `SSE_TIMEOUT_MS` is 1 hour — do not shorten it per-endpoint.
- Cross-node fan-out is Redis pub/sub via `SsePublisher`/`SseRedisSubscriber`. Never add an in-process-only broadcast path — it silently drops clients connected to other nodes.
- Emitter disconnect/timeout is expected churn: `log.debug`, never Sentry.
