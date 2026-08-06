import { env } from './env'
import { queryClient } from './queryClient'

const API_BASE_URL = env.VITE_API_BASE_URL

/** Readable cookie holding the double-submit CSRF token (set by the backend). */
const CSRF_COOKIE_NAME = 'csrf'
/** Request header that must echo the CSRF cookie on state-changing requests. */
const CSRF_HEADER_NAME = 'X-CSRF-Token'

/**
 * Read the readable `csrf` cookie for double-submit CSRF protection.
 *
 * SSR-safe: returns null on the server where `document` is undefined.
 */
function readCsrfToken(): string | null {
  if (typeof document === 'undefined') {
    return null
  }
  const match = document.cookie.match(new RegExp(`(?:^|;\\s*)${CSRF_COOKIE_NAME}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

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

/** Shape every backend error body is read through; every field is best-effort. */
interface ErrorBody {
  code?: string
  message?: string
  serverVersion?: number
}

type ApiErrorConstructor = new (message: string) => Error

/** 403 bodies whose code maps to a dedicated restriction error. */
const RESTRICTION_ERROR_BY_CODE: Record<string, ApiErrorConstructor> = {
  USER_BANNED: BannedError,
  USER_TIMED_OUT: TimedOutError,
}

/** 503 bodies whose code maps to a dedicated unavailability error. */
const UNAVAILABLE_ERROR_BY_CODE: Record<string, ApiErrorConstructor> = {
  SERVICE_UPDATING: ServiceUpdatingError,
  WRITE_TEMPORARILY_UNAVAILABLE: WriteTemporarilyUnavailableError,
  AUTH_TEMPORARILY_UNAVAILABLE: AuthTemporarilyUnavailableError,
  RATE_LIMIT_TEMPORARILY_UNAVAILABLE: RetryableUnavailableError,
  DEADLOCK: RetryableUnavailableError,
}

const DEFAULT_UNAVAILABLE_MESSAGE = 'Service temporarily unavailable'
const DEFAULT_RATE_LIMIT_MESSAGE = 'Too many requests'
const DEFAULT_VALIDATION_MESSAGE = 'Invalid request'
const DEFAULT_VALIDATION_CODE = 'VALIDATION_ERROR'
const DEFAULT_CONFLICT_CODE = 'CONFLICT'

/**
 * Read an error response body, yielding null when it is absent or not JSON.
 */
async function readErrorBody(response: Response): Promise<ErrorBody | null> {
  try {
    return (await response.json()) as ErrorBody
  } catch {
    return null
  }
}

export class ApiClient {
  static async fetch<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
    const method = (options.method ?? 'GET').toUpperCase()
    const callerHeaders = (options.headers as Record<string, string> | undefined) ?? {}
    const headers: Record<string, string> = { ...callerHeaders }

    // Bodyless GET/HEAD must stay CORS "simple" — a request Content-Type would force a
    // preflight OPTIONS that blocks the cold-load request burst.
    const isBodylessMethod = method === 'GET' || method === 'HEAD'
    const isFormDataBody = typeof FormData !== 'undefined' && options.body instanceof FormData
    const callerSetContentType = Object.keys(callerHeaders).some(
      (key) => key.toLowerCase() === 'content-type',
    )
    if (!isBodylessMethod && !isFormDataBody && !callerSetContentType) {
      headers['Content-Type'] = 'application/json'
    }

    // Double-submit CSRF: echo the readable `csrf` cookie on state-changing
    // methods. GET/HEAD stay header-free to remain CORS "simple" requests.
    if (!isBodylessMethod) {
      const csrfToken = readCsrfToken()
      if (csrfToken) {
        headers[CSRF_HEADER_NAME] = csrfToken
      }
    }

    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      ...options,
      headers,
      credentials: 'include', // Include HttpOnly cookies
    })

    // Backend handles token refresh automatically via JwtAuthenticationFilter
    // If we get 401, auth has genuinely failed (no valid refresh token)
    if (response.status === 401) {
      queryClient.setQueryData(['auth', 'me'], null)
      throw new Error(`HTTP error! status: 401`)
    }

    // Handle 403 Forbidden with typed errors based on error code
    if (response.status === 403) {
      const body = await readErrorBody(response)
      if (!body) {
        throw new Error('Forbidden')
      }
      const RestrictionError = RESTRICTION_ERROR_BY_CODE[body.code ?? '']
      if (RestrictionError) {
        throw new RestrictionError(body.message ?? '')
      }
      // Other 403 errors (PLANNER_FORBIDDEN, COMMENT_FORBIDDEN, etc.)
      throw new ForbiddenError(body.code ?? '', body.message ?? '')
    }

    // Handle 400 Bad Request with the code the backend classified it under
    if (response.status === 400) {
      const body = await readErrorBody(response)
      throw new ValidationError(
        body?.code ?? DEFAULT_VALIDATION_CODE,
        body?.message || DEFAULT_VALIDATION_MESSAGE,
      )
    }

    // Handle 404 Not Found with typed error
    if (response.status === 404) {
      throw new NotFoundError('Resource not found')
    }

    // Handle 409 conflict with typed error
    if (response.status === 409) {
      const body = await readErrorBody(response)
      throw new ConflictError(
        body?.code ?? DEFAULT_CONFLICT_CODE,
        body?.message || 'Conflict',
        body?.serverVersion ?? null,
      )
    }

    // Handle 429 Too Many Requests with typed error
    if (response.status === 429) {
      const body = await readErrorBody(response)
      throw new RateLimitError(body?.message || DEFAULT_RATE_LIMIT_MESSAGE)
    }

    // Handle 503 Service Unavailable - distinguish planned deploy vs crash
    if (response.status === 503) {
      const body = await readErrorBody(response)
      const message = body?.message || DEFAULT_UNAVAILABLE_MESSAGE
      const UnavailableError =
        UNAVAILABLE_ERROR_BY_CODE[body?.code ?? ''] ?? BackendUnavailableError
      throw new UnavailableError(message)
    }

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    // Handle 204 No Content (e.g., logout, DELETE operations)
    if (response.status === 204) {
      return undefined as T
    }

    return response.json()
  }

  static async get<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
    return this.fetch<T>(endpoint, { ...options, method: 'GET' })
  }

  static async post<T>(endpoint: string, data?: unknown): Promise<T> {
    return this.fetch<T>(endpoint, {
      method: 'POST',
      body: data ? JSON.stringify(data) : undefined,
    })
  }

  static async put<T>(endpoint: string, data?: unknown): Promise<T> {
    return this.fetch<T>(endpoint, {
      method: 'PUT',
      body: data ? JSON.stringify(data) : undefined,
    })
  }

  static async delete<T>(endpoint: string): Promise<T> {
    return this.fetch<T>(endpoint, { method: 'DELETE' })
  }

  static async patch<T>(endpoint: string, data?: unknown): Promise<T> {
    return this.fetch<T>(endpoint, {
      method: 'PATCH',
      body: data ? JSON.stringify(data) : undefined,
    })
  }

  /**
   * Create an EventSource for Server-Sent Events
   * Used for real-time planner sync notifications
   */
  static createEventSource(endpoint: string): EventSource {
    return new EventSource(`${API_BASE_URL}${endpoint}`, {
      withCredentials: true,
    })
  }
}
