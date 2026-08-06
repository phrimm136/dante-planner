/**
 * Query cache lifetimes and server-event transport settings.
 */

/**
 * Stale time for static JSON data queries in milliseconds (7 days).
 * Static data only changes on deploy, so it is effectively immutable at runtime.
 */
export const STATIC_DATA_STALE_TIME = 7 * 24 * 60 * 60 * 1000

/**
 * Freshness windows for server-backed queries, named by how fast the
 * underlying data turns over.
 */
export const STALE_TIME = {
  /** Moderation queues and other near-live views */
  LIVE: 10 * 1000,
  /** Lists that change on every user action (comments, personal planners) */
  FREQUENT: 30 * 1000,
  /** Community lists and notification inbox */
  SHORT: 60 * 1000,
  /** Auth identity, settings, published planner content */
  MEDIUM: 5 * 60 * 1000,
  /** Reference data that changes at most daily (epithet catalogue) */
  DAY: 24 * 60 * 60 * 1000,
  /** Bundled static JSON */
  STATIC: STATIC_DATA_STALE_TIME,
} as const

/**
 * How long an inactive query is retained before garbage collection.
 */
export const GC_TIME = {
  SHORT: 5 * 60 * 1000,
  MEDIUM: 10 * 60 * 1000,
} as const

/**
 * Attempts a query makes against a backend that reported the failure retryable
 * before the error surfaces to the caller.
 */
export const MAX_RETRYABLE_ATTEMPTS = 3

/** First backoff step for a retried query in milliseconds. */
export const RETRY_BASE_MS = 500

/** Ceiling the exponential retry backoff saturates at in milliseconds. */
export const RETRY_MAX_MS = 8 * 1000

/**
 * SSE Connection Constants
 * Used by useSseEngine hook for reconnection and token management
 */
export const SSE_CONNECTION = {
  /** Initial delay before first connection to let cookies settle (ms) */
  INITIAL_DELAY: 500,
  /** Base delay for reconnection in ms */
  BASE_DELAY: 1000,
  /** Maximum delay for reconnection in ms */
  MAX_DELAY: 8000,
  /** Maximum reconnection attempts before giving up */
  MAX_ATTEMPTS: 10,
  /** Proactive reconnect interval (13 min) - reconnect BEFORE token expires */
  PROACTIVE_RECONNECT_INTERVAL: 13 * 60 * 1000,
  /** Idle timeout (5 min) after which reconnect attempts reset */
  IDLE_RESET_TIMEOUT: 5 * 60 * 1000,
  /** Random reconnect jitter ceiling (0–5s) to avoid thundering-herd reconnects */
  MAX_JITTER: 5 * 1000,
  /**
   * Minimum time (30s) a connection must stay open to count as healthy. A
   * connection that dies sooner is treated as a failed attempt so backoff keeps
   * growing — without this floor, a stream that opens then immediately drops
   * resets the attempt counter and pins the client at ~1 reconnect/sec forever.
   */
  STABLE_CONNECTION_THRESHOLD: 30 * 1000,
} as const

/**
 * SSE Connection Constants for the per-planner comment stream
 *
 * The comment stream carries no token to refresh, so it opens immediately and
 * never reconnects pre-emptively; the rest of the timings match SSE_CONNECTION.
 */
export const COMMENT_SSE_CONNECTION = {
  /** Open on mount: no auth cookie has to settle first */
  INITIAL_DELAY: 0,
  /** Base delay for reconnection in ms */
  BASE_DELAY: 1000,
  /** Maximum delay for reconnection in ms */
  MAX_DELAY: 8000,
  /** Maximum reconnection attempts before waiting for the idle reset */
  MAX_ATTEMPTS: 10,
  /** Random reconnect jitter ceiling (0–5s) to avoid thundering-herd reconnects */
  MAX_JITTER: 5 * 1000,
  /** Idle timeout (5 min) after which reconnect attempts reset */
  IDLE_RESET_TIMEOUT: 5 * 60 * 1000,
  /** No pre-expiry reconnect: nothing on this stream expires */
  PROACTIVE_RECONNECT_INTERVAL: null,
  /** Minimum time (30s) a connection must stay open to count as healthy */
  STABLE_CONNECTION_THRESHOLD: 30 * 1000,
} as const

/**
 * SSE Event Names
 *
 * The stream names an event with these exact strings; they are the backend's
 * `SseEventType` wire values plus the transport-level `connected` handshake.
 */
export const SSE_EVENTS = {
  CONNECTED: 'connected',
  CREATED: 'created',
  UPDATED: 'updated',
  DELETED: 'deleted',
  COMMENT_ADDED: 'comment:added',
  NOTIFY_COMMENT: 'notify:comment',
  NOTIFY_RECOMMENDED: 'notify:recommended',
  NOTIFY_PUBLISHED: 'notify:published',
  ACCOUNT_SUSPENDED: 'account_suspended',
} as const
