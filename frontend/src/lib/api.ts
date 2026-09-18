import { env } from './env'
import {
  AuthTemporarilyUnavailableError,
  BackendUnavailableError,
  BannedError,
  ConflictError,
  ForbiddenError,
  NotFoundError,
  RateLimitError,
  RetryableUnavailableError,
  ServiceUpdatingError,
  TimedOutError,
  UnauthorizedError,
  ValidationError,
  WriteTemporarilyUnavailableError,
} from './apiErrors'
import { ProblemSchema, type Problem } from './problem'
import { queryClient } from './queryClient'

export { ProblemSchema, type Problem }

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
  const [, encoded] =
    document.cookie.match(new RegExp(`(?:^|;\\s*)${CSRF_COOKIE_NAME}=([^;]*)`)) ?? []
  return encoded === undefined ? null : decodeURIComponent(encoded)
}

type ApiErrorConstructor = new (message: string) => Error

const RESTRICTION_ERROR_BY_CODE: Record<string, ApiErrorConstructor> = {
  USER_BANNED: BannedError,
  USER_TIMED_OUT: TimedOutError,
}

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

async function readErrorBody(response: Response): Promise<Problem | null> {
  const parsed = ProblemSchema.safeParse(await response.json().catch(() => null))
  return parsed.success ? parsed.data : null
}

export class ApiClient {
  static async fetch<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
    const method = (options.method ?? 'GET').toUpperCase()
    // HeadersInit is a Headers instance, a tuple array, or a record; asserting the
    const headers = new Headers(options.headers)

    // Bodyless GET/HEAD must stay CORS "simple" — a request Content-Type would force a
    const isBodylessMethod = method === 'GET' || method === 'HEAD'
    const isFormDataBody = typeof FormData !== 'undefined' && options.body instanceof FormData
    const callerSetContentType = headers.has('Content-Type')
    if (!isBodylessMethod && !isFormDataBody && !callerSetContentType) {
      headers.set('Content-Type', 'application/json')
    }

    // Double-submit CSRF: echo the readable `csrf` cookie on state-changing
    if (!isBodylessMethod) {
      const csrfToken = readCsrfToken()
      if (csrfToken) {
        headers.set(CSRF_HEADER_NAME, csrfToken)
      }
    }

    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      ...options,
      headers,
      credentials: 'include', // Include HttpOnly cookies
    })

    if (response.status === 401) {
      queryClient.setQueryData(['auth', 'me'], null)
      throw new UnauthorizedError('Authentication required')
    }

    if (response.status === 403) {
      const body = await readErrorBody(response)
      if (!body) {
        throw new Error('Forbidden')
      }
      const RestrictionError = RESTRICTION_ERROR_BY_CODE[body.code ?? '']
      if (RestrictionError) {
        throw new RestrictionError(body.detail ?? '')
      }
      throw new ForbiddenError(body.code ?? '', body.detail ?? '')
    }

    if (response.status === 400) {
      const body = await readErrorBody(response)
      throw new ValidationError(
        body?.code ?? DEFAULT_VALIDATION_CODE,
        body?.detail || DEFAULT_VALIDATION_MESSAGE,
      )
    }

    if (response.status === 404) {
      throw new NotFoundError('Resource not found')
    }

    if (response.status === 409) {
      const body = await readErrorBody(response)
      throw new ConflictError(
        body?.code ?? DEFAULT_CONFLICT_CODE,
        body?.detail || 'Conflict',
        body?.serverVersion ?? null,
      )
    }

    if (response.status === 429) {
      const body = await readErrorBody(response)
      throw new RateLimitError(body?.detail || DEFAULT_RATE_LIMIT_MESSAGE)
    }

    if (response.status === 503) {
      const body = await readErrorBody(response)
      const message = body?.detail || DEFAULT_UNAVAILABLE_MESSAGE
      const UnavailableError =
        UNAVAILABLE_ERROR_BY_CODE[body?.code ?? ''] ?? BackendUnavailableError
      throw new UnavailableError(message)
    }

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

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
      ...(data ? { body: JSON.stringify(data) } : {}),
    })
  }

  static async put<T>(endpoint: string, data?: unknown): Promise<T> {
    return this.fetch<T>(endpoint, {
      method: 'PUT',
      ...(data ? { body: JSON.stringify(data) } : {}),
    })
  }

  static async delete<T>(endpoint: string): Promise<T> {
    return this.fetch<T>(endpoint, { method: 'DELETE' })
  }

  static async patch<T>(endpoint: string, data?: unknown): Promise<T> {
    return this.fetch<T>(endpoint, {
      method: 'PATCH',
      ...(data ? { body: JSON.stringify(data) } : {}),
    })
  }
}
