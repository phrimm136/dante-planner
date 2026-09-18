export const CONFLICT_CODE = {
  SYNC_CONFLICT: 'SYNC_CONFLICT',
  CONCURRENT_WRITE: 'CONCURRENT_WRITE',
} as const

export const STATIC_DATA_STALE_TIME = 7 * 24 * 60 * 60 * 1000

export const STALE_TIME = {
  LIVE: 10 * 1000,
  FREQUENT: 30 * 1000,
  SHORT: 60 * 1000,
  MEDIUM: 5 * 60 * 1000,
  LONG: 10 * 60 * 1000,
  DAY: 24 * 60 * 60 * 1000,
  STATIC: STATIC_DATA_STALE_TIME,
} as const

export const GC_TIME = {
  SHORT: 5 * 60 * 1000,
  MEDIUM: 10 * 60 * 1000,
} as const

export const MAX_RETRYABLE_ATTEMPTS = 3

export const RETRY_BASE_MS = 500

export const RETRY_MAX_MS = 8 * 1000

export const SSE_CONNECTION = {
  INITIAL_DELAY: 500,
  BASE_DELAY: 1000,
  MAX_DELAY: 8000,
  MAX_ATTEMPTS: 10,
  PROACTIVE_RECONNECT_INTERVAL: 13 * 60 * 1000,
  IDLE_RESET_TIMEOUT: 5 * 60 * 1000,
  MAX_JITTER: 5 * 1000,
  STABLE_CONNECTION_THRESHOLD: 30 * 1000,
} as const

export const COMMENT_SSE_CONNECTION = {
  INITIAL_DELAY: 0,
  BASE_DELAY: 1000,
  MAX_DELAY: 8000,
  MAX_ATTEMPTS: 10,
  MAX_JITTER: 5 * 1000,
  IDLE_RESET_TIMEOUT: 5 * 60 * 1000,
  PROACTIVE_RECONNECT_INTERVAL: null,
  STABLE_CONNECTION_THRESHOLD: 30 * 1000,
} as const

export const SSE_TRANSPORT = {
  ACCEPT: 'text/event-stream',
  RETRY_AFTER_HEADER: 'Retry-After',
  RATE_LIMITED_STATUS: 429,
  STREAM_GONE_STATUS: 404,
  RETRY_AFTER_UNIT_MS: 1000,
} as const

/**
 * SSE Event Names
 *
 * The stream names an event with these exact strings; they are the backend's
 * `SseEventType` wire values plus the transport-level `connected` handshake.
 */
export const SSE_EVENTS = {
  CONNECTED: 'connected',
  COMMENT_ADDED: 'comment:added',
  NOTIFY_COMMENT: 'notify:comment',
  NOTIFY_RECOMMENDED: 'notify:recommended',
  NOTIFY_PUBLISHED: 'notify:published',
  SETTINGS_INVALIDATED: 'settings:invalidated',
  ACCOUNT_SUSPENDED: 'account_suspended',
} as const

export type SseEventType = (typeof SSE_EVENTS)[keyof typeof SSE_EVENTS]
