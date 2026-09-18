/**
 * Custom error class for 409 Conflict responses
 * Enables typed error handling with instanceof checks
 *
 * A conflict raised by a concurrent write rather than by optimistic locking
 * carries no server version; `serverVersion` is null there, and a caller that
 * needs the version has to read it back rather than assume one.
 */
export class ConflictError extends Error {
  readonly code: string
  readonly serverVersion: number | null

  constructor(code: string, message: string, serverVersion: number | null) {
    super(message)
    this.name = 'ConflictError'
    this.code = code
    this.serverVersion = serverVersion
  }
}

export class RateLimitError extends Error {
  readonly code = 'RATE_LIMIT_EXCEEDED'

  constructor(message: string) {
    super(message)
    this.name = 'RateLimitError'
  }
}

export class ValidationError extends Error {
  readonly code: string

  constructor(code: string, message: string) {
    super(message)
    this.name = 'ValidationError'
    this.code = code
  }
}

export class NotFoundError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'NotFoundError'
  }
}

export class UnauthorizedError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'UnauthorizedError'
  }
}

export class BannedError extends Error {
  readonly code = 'USER_BANNED'

  constructor(message: string) {
    super(message)
    this.name = 'BannedError'
  }
}

export class TimedOutError extends Error {
  readonly code = 'USER_TIMED_OUT'

  constructor(message: string) {
    super(message)
    this.name = 'TimedOutError'
  }
}

export class ForbiddenError extends Error {
  readonly code: string

  constructor(code: string, message: string) {
    super(message)
    this.name = 'ForbiddenError'
    this.code = code
  }
}

export class ServiceUpdatingError extends Error {
  readonly code = 'SERVICE_UPDATING'

  constructor(message: string) {
    super(message)
    this.name = 'ServiceUpdatingError'
  }
}

export class BackendUnavailableError extends Error {
  readonly code = 'BACKEND_UNAVAILABLE'

  constructor(message: string) {
    super(message)
    this.name = 'BackendUnavailableError'
  }
}

export class WriteTemporarilyUnavailableError extends Error {
  readonly code = 'WRITE_TEMPORARILY_UNAVAILABLE'

  constructor(message: string) {
    super(message)
    this.name = 'WriteTemporarilyUnavailableError'
  }
}

export class AuthTemporarilyUnavailableError extends Error {
  readonly code = 'AUTH_TEMPORARILY_UNAVAILABLE'

  constructor(message: string) {
    super(message)
    this.name = 'AuthTemporarilyUnavailableError'
  }
}

export class RetryableUnavailableError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'RetryableUnavailableError'
  }
}
