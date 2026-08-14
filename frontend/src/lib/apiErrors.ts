/**
 * Typed errors for the API's failure responses.
 *
 * They live apart from the client so the query cache and the error
 * classifier can recognise a failure without importing the module that
 * performs requests.
 */

/**
 * Custom error class for 409 Conflict responses
 * Enables typed error handling with instanceof checks
 *
 * A conflict raised by a concurrent write rather than by optimistic locking
 * carries no server version; `serverVersion` is null there, and a caller that
 * needs the version has to read it back rather than assume one.
 */
export class ConflictError extends Error {
  /** The backend's conflict code (`SYNC_CONFLICT`, `CONCURRENT_WRITE`) */
  readonly code: string
  /** Server's current version for sync resolution, or null when unreported */
  readonly serverVersion: number | null

  constructor(code: string, message: string, serverVersion: number | null) {
    super(message)
    this.name = 'ConflictError'
    this.code = code
    this.serverVersion = serverVersion
  }
}

/**
 * Custom error class for 429 Too Many Requests responses
 */
export class RateLimitError extends Error {
  readonly code = 'RATE_LIMIT_EXCEEDED'

  constructor(message: string) {
    super(message)
    this.name = 'RateLimitError'
  }
}

/**
 * Custom error class for 400 Bad Request responses the backend classified
 */
export class ValidationError extends Error {
  readonly code: string

  constructor(code: string, message: string) {
    super(message)
    this.name = 'ValidationError'
    this.code = code
  }
}

/**
 * Custom error class for 404 Not Found responses
 * Enables typed error handling with instanceof checks
 */
export class NotFoundError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'NotFoundError'
  }
}

/**
 * Custom error class for 403 USER_BANNED responses
 * User account has been permanently banned
 */
export class BannedError extends Error {
  readonly code = 'USER_BANNED'

  constructor(message: string) {
    super(message)
    this.name = 'BannedError'
  }
}

/**
 * Custom error class for 403 USER_TIMED_OUT responses
 * User account is temporarily restricted
 */
export class TimedOutError extends Error {
  readonly code = 'USER_TIMED_OUT'

  constructor(message: string) {
    super(message)
    this.name = 'TimedOutError'
  }
}

/**
 * Custom error class for 403 Forbidden with error code
 * Used for PLANNER_FORBIDDEN, COMMENT_FORBIDDEN, etc.
 */
export class ForbiddenError extends Error {
  readonly code: string

  constructor(code: string, message: string) {
    super(message)
    this.name = 'ForbiddenError'
    this.code = code
  }
}

/**
 * Custom error class for 503 during planned deploy
 * Thrown when nginx returns SERVICE_UPDATING (maintenance flag present)
 */
export class ServiceUpdatingError extends Error {
  readonly code = 'SERVICE_UPDATING'

  constructor(message: string) {
    super(message)
    this.name = 'ServiceUpdatingError'
  }
}

/**
 * Custom error class for 503 during unexpected backend downtime
 * Thrown when nginx returns BACKEND_UNAVAILABLE (no maintenance flag)
 */
export class BackendUnavailableError extends Error {
  readonly code = 'BACKEND_UNAVAILABLE'

  constructor(message: string) {
    super(message)
    this.name = 'BackendUnavailableError'
  }
}

/**
 * Custom error class for 503 when a write cannot be served during regional failover
 * Thrown when the backend returns WRITE_TEMPORARILY_UNAVAILABLE
 */
export class WriteTemporarilyUnavailableError extends Error {
  readonly code = 'WRITE_TEMPORARILY_UNAVAILABLE'

  constructor(message: string) {
    super(message)
    this.name = 'WriteTemporarilyUnavailableError'
  }
}

/**
 * Custom error class for 503 when auth cannot be served during regional failover
 * Thrown when the backend returns AUTH_TEMPORARILY_UNAVAILABLE
 */
export class AuthTemporarilyUnavailableError extends Error {
  readonly code = 'AUTH_TEMPORARILY_UNAVAILABLE'

  constructor(message: string) {
    super(message)
    this.name = 'AuthTemporarilyUnavailableError'
  }
}

/**
 * Custom error class for 503 responses the backend says to retry unchanged —
 * a database deadlock or an unreachable rate limiter.
 */
export class RetryableUnavailableError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'RetryableUnavailableError'
  }
}
