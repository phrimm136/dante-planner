/**
 * api.test.ts
 *
 * Tests for ApiClient request/response handling.
 * Covers: 204 No Content, 401 refresh flow, 409 Conflict, error propagation
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { http, HttpResponse, type JsonBodyType } from 'msw'

import { server } from '@/test-utils/mswServer'
import { ApiClient } from '../api'
import { useServerStatusStore, type ServerStatus } from '../banner/serverStatus'
import {
  ConflictError,
  ForbiddenError,
  RateLimitError,
  RetryableUnavailableError,
  ValidationError,
  WriteTemporarilyUnavailableError,
  AuthTemporarilyUnavailableError,
  BackendUnavailableError,
  ServiceUpdatingError,
  UnauthorizedError,
} from '../apiErrors'

// Mock the env module
vi.mock('../env', () => ({
  env: {
    VITE_API_BASE_URL: 'http://localhost:8080',
  },
}))

// Mock queryClient
const mockSetQueryData = vi.fn()
vi.mock('../queryClient', () => ({
  queryClient: {
    setQueryData: (...args: unknown[]) => mockSetQueryData(...args),
  },
}))

interface SeenRequest {
  method: string
  url: string
  headers: Headers
  body: string
  credentials: RequestCredentials
}

let seen: SeenRequest[] = []

/** Answers every API request with `resolver`, recording the request as the server sees it. */
function answer(resolver: () => Response) {
  server.use(
    http.all('http://localhost:8080/*', async ({ request }) => {
      seen.push({
        method: request.method,
        url: request.url,
        headers: request.headers,
        body: await request.clone().text(),
        credentials: request.credentials,
      })
      return resolver()
    }),
  )
}

const json = (status: number, body: JsonBodyType) => () => HttpResponse.json(body, { status })
const empty = (status: number) => () => new HttpResponse(null, { status })
const unparseable = (status: number) => () => HttpResponse.text('<!doctype html>', { status })

function firstRequest(): SeenRequest {
  const [first] = seen
  if (!first) throw new Error('no request reached the server')
  return first
}

// Mock window.location for redirect tests
const mockLocation = { href: '' }
Object.defineProperty(window, 'location', {
  value: mockLocation,
  writable: true,
})

describe('ApiClient', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockLocation.href = ''
    seen = []
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  describe('204 No Content handling', () => {
    it('returns undefined for 204 response without parsing JSON', async () => {
      answer(empty(204))

      const result = await ApiClient.delete('/api/auth/logout')

      expect(result).toBeUndefined()
      expect(firstRequest()).toMatchObject({
        method: 'DELETE',
        url: 'http://localhost:8080/api/auth/logout',
      })
    })

    it('handles DELETE operations returning 204', async () => {
      answer(empty(204))

      const result = await ApiClient.delete('/api/planner/123')

      expect(result).toBeUndefined()
    })

    it('handles POST logout returning 204', async () => {
      answer(empty(204))

      const result = await ApiClient.post('/api/auth/logout')

      expect(result).toBeUndefined()
    })
  })

  describe('200 OK with JSON body', () => {
    it('parses JSON response for 200 status', async () => {
      const mockData = { id: 1, email: 'test@example.com' }
      answer(json(200, mockData))

      const result = await ApiClient.get('/api/auth/me')

      expect(result).toEqual(mockData)
    })
  })

  describe('401 Unauthorized handling', () => {
    it('clears auth state and throws error for 401 response', async () => {
      answer(empty(401))

      await expect(ApiClient.get('/api/planner/123')).rejects.toBeInstanceOf(UnauthorizedError)

      // Auth state should be cleared so UI shows logged-out state
      expect(mockSetQueryData).toHaveBeenCalledWith(['auth', 'me'], null)
      expect(seen).toHaveLength(1)
    })

    it('clears auth state for 401 on /auth/me', async () => {
      answer(empty(401))

      await expect(ApiClient.get('/api/auth/me')).rejects.toBeInstanceOf(UnauthorizedError)

      expect(mockSetQueryData).toHaveBeenCalledWith(['auth', 'me'], null)
    })

    it('clears auth state for 401 on /auth/logout', async () => {
      answer(empty(401))

      await expect(ApiClient.post('/api/auth/logout')).rejects.toBeInstanceOf(UnauthorizedError)

      expect(mockSetQueryData).toHaveBeenCalledWith(['auth', 'me'], null)
    })
  })

  describe('409 Conflict handling', () => {
    it('throws ConflictError with serverVersion from response', async () => {
      answer(
        json(409, {
          code: 'VERSION_CONFLICT',
          detail: 'Version conflict',
          serverVersion: 5,
        }),
      )

      await expect(ApiClient.put('/api/planner/123', { version: 3 })).rejects.toThrow(ConflictError)

      const error = await ApiClient.put('/api/planner/123', { version: 3 }).catch((e: unknown) => e)

      expect(error).toBeInstanceOf(ConflictError)
      expect((error as ConflictError).serverVersion).toBe(5)
    })

    it('reports no server version when the response body cannot be parsed', async () => {
      answer(unparseable(409))

      const error = await ApiClient.put('/api/planner/123', { version: 3 }).catch((e: unknown) => e)

      expect(error).toBeInstanceOf(ConflictError)
      expect((error as ConflictError).serverVersion).toBeNull()
    })

    it('reports no server version for a concurrent write, and carries its code', async () => {
      answer(
        json(409, {
          code: 'CONCURRENT_WRITE',
          detail: 'The resource was modified concurrently',
          serverVersion: null,
        }),
      )

      const error = await ApiClient.put('/api/planner/123', {}).catch((e: unknown) => e)

      expect(error).toBeInstanceOf(ConflictError)
      expect((error as ConflictError).serverVersion).toBeNull()
      expect((error as ConflictError).code).toBe('CONCURRENT_WRITE')
    })
  })

  describe('403 Forbidden handling', () => {
    it('throws ForbiddenError carrying the code and detail', async () => {
      answer(
        json(403, {
          code: 'PLANNER_FORBIDDEN',
          detail: 'This planner belongs to someone else',
        }),
      )

      const error = await ApiClient.get('/api/planner/md/123').catch((e: unknown) => e)

      expect(error).toBeInstanceOf(ForbiddenError)
      expect((error as ForbiddenError).code).toBe('PLANNER_FORBIDDEN')
      expect((error as ForbiddenError).message).toBe('This planner belongs to someone else')
    })

    it('falls back to a generic error when the body fails the problem schema', async () => {
      answer(json(403, { code: 5 }))

      const error = await ApiClient.get('/api/planner/md/123').catch((e: unknown) => e)

      expect(error).toBeInstanceOf(Error)
      expect(error).not.toBeInstanceOf(ForbiddenError)
      expect((error as Error).message).toBe('Forbidden')
    })
  })

  describe('400 / 429 typed errors', () => {
    it('400 throws ValidationError carrying the backend code', async () => {
      answer(
        json(400, {
          code: 'VALIDATION_ERROR',
          detail: 'Invalid planner content structure',
        }),
      )

      const error = await ApiClient.post('/api/planner/md', {}).catch((e: unknown) => e)

      expect(error).toBeInstanceOf(ValidationError)
      expect((error as ValidationError).code).toBe('VALIDATION_ERROR')
      expect((error as ValidationError).message).toBe('Invalid planner content structure')
    })

    it('429 throws RateLimitError', async () => {
      answer(
        json(429, {
          code: 'RATE_LIMIT_EXCEEDED',
          detail: 'Too many requests',
        }),
      )

      const error = await ApiClient.post('/api/planner/md', {}).catch((e: unknown) => e)

      expect(error).toBeInstanceOf(RateLimitError)
      expect((error as RateLimitError).code).toBe('RATE_LIMIT_EXCEEDED')
    })

    it.each(['RATE_LIMIT_TEMPORARILY_UNAVAILABLE', 'DEADLOCK'])(
      '503 with code %s throws RetryableUnavailableError',
      async (code) => {
        answer(json(503, { code, detail: 'please retry' }))

        await expect(ApiClient.post('/api/planner/md', {})).rejects.toBeInstanceOf(
          RetryableUnavailableError,
        )
      },
    )
  })

  describe('503 regional failover typed errors', () => {
    it('503 with code WRITE_TEMPORARILY_UNAVAILABLE throws WriteTemporarilyUnavailableError', async () => {
      answer(
        json(503, {
          code: 'WRITE_TEMPORARILY_UNAVAILABLE',
          detail: 'Database temporarily unavailable, please retry',
        }),
      )

      await expect(ApiClient.put('/api/planner/123', {})).rejects.toBeInstanceOf(
        WriteTemporarilyUnavailableError,
      )
    })

    it('503 with code AUTH_TEMPORARILY_UNAVAILABLE throws AuthTemporarilyUnavailableError', async () => {
      answer(
        json(503, {
          code: 'AUTH_TEMPORARILY_UNAVAILABLE',
          detail: 'Authentication service temporarily unavailable, please retry',
        }),
      )

      await expect(ApiClient.put('/api/planner/123', {})).rejects.toBeInstanceOf(
        AuthTemporarilyUnavailableError,
      )
    })

    it('503 without a recognized code still throws BackendUnavailableError', async () => {
      answer(
        json(503, {
          code: 'BACKEND_UNAVAILABLE',
          detail: 'Service temporarily unavailable',
        }),
      )

      await expect(ApiClient.put('/api/planner/123', {})).rejects.toBeInstanceOf(
        BackendUnavailableError,
      )
    })
  })

  describe('Error propagation', () => {
    it('throws error with status for 4xx responses', async () => {
      answer(empty(404))

      await expect(ApiClient.get('/api/planner/nonexistent')).rejects.toThrow('Resource not found')
    })

    it('throws error with status for 5xx responses', async () => {
      answer(empty(500))

      await expect(ApiClient.get('/api/planner/123')).rejects.toThrow('HTTP error! status: 500')
    })
  })

  describe('Request configuration', () => {
    it('includes credentials for cookie-based auth', async () => {
      answer(json(200, {}))

      await ApiClient.get('/api/auth/me')

      expect(firstRequest().credentials).toBe('include')
    })

    it('sets Content-Type to application/json', async () => {
      answer(json(200, {}))

      await ApiClient.post('/api/planner', { name: 'Test' })

      expect(firstRequest().headers.get('Content-Type')).toBe('application/json')
    })

    it('stringifies request body for POST', async () => {
      answer(json(200, {}))

      const data = { name: 'Test', version: 1 }
      await ApiClient.post('/api/planner', data)

      expect(firstRequest().body).toBe(JSON.stringify(data))
    })
  })

  describe('CORS preflight avoidance (regression guard)', () => {
    // Bodyless GET/HEAD must stay CORS "simple" requests: a request Content-Type would
    // force a preflight OPTIONS that blocks the cold-load request burst.
    const sentHeaders = () => firstRequest().headers

    it('GET requests send no Content-Type header', async () => {
      answer(json(200, {}))

      await ApiClient.get('/api/planner/md/published?page=0&size=20')

      expect(sentHeaders().has('Content-Type')).toBe(false)
    })

    it('GET requests carry only CORS-safelisted headers', async () => {
      answer(json(200, {}))

      await ApiClient.get('/api/auth/me')

      const safelisted = ['accept', 'accept-language', 'content-language']
      const nonSimple = [...sentHeaders().keys()].filter(
        (key) => !safelisted.includes(key.toLowerCase()),
      )
      expect(nonSimple).toEqual([])
    })

    it('POST with a JSON body still sends Content-Type (write path unchanged)', async () => {
      answer(json(200, {}))

      await ApiClient.post('/api/planner/md/123/publish', { published: true })

      expect(sentHeaders().get('Content-Type')).toBe('application/json')
    })

    it('does not force application/json on a FormData body', async () => {
      answer(json(200, {}))

      const form = new FormData()
      form.append('file', new Blob(['x']), 'x.png')
      await ApiClient.fetch('/api/upload', { method: 'POST', body: form })

      expect(sentHeaders().get('Content-Type')).toMatch(/^multipart\/form-data; boundary=/)
    })

    it('preserves a caller-supplied Content-Type', async () => {
      answer(json(200, {}))

      await ApiClient.fetch('/api/thing', {
        method: 'POST',
        body: 'raw',
        headers: { 'Content-Type': 'text/plain' },
      })

      expect(sentHeaders().get('Content-Type')).toBe('text/plain')
    })
  })

  describe('CSRF double-submit header', () => {
    const sentHeaders = () => firstRequest().headers

    let cookieValue = ''
    beforeEach(() => {
      cookieValue = ''
      Object.defineProperty(document, 'cookie', {
        configurable: true,
        get: () => cookieValue,
      })
    })

    it('attaches X-CSRF-Token from the csrf cookie on a POST request', async () => {
      cookieValue = 'foo=bar; csrf=token-abc123; baz=qux'
      answer(json(200, {}))

      await ApiClient.post('/api/planner', { name: 'Test' })

      expect(sentHeaders().get('X-CSRF-Token')).toBe('token-abc123')
    })

    it('does not attach X-CSRF-Token on a GET request', async () => {
      cookieValue = 'csrf=token-abc123'
      answer(json(200, {}))

      await ApiClient.get('/api/auth/me')

      expect(sentHeaders().has('X-CSRF-Token')).toBe(false)
    })

    it('attaches no header when the csrf cookie is absent', async () => {
      cookieValue = 'foo=bar'
      answer(json(200, {}))

      await ApiClient.post('/api/planner', { name: 'Test' })

      expect(sentHeaders().has('X-CSRF-Token')).toBe(false)
    })

    it('selects csrf, not a cookie whose name merely ends with csrf', async () => {
      cookieValue = 'mycsrf=fake; csrf=real-token'
      answer(json(200, {}))

      await ApiClient.post('/api/planner', { name: 'Test' })

      expect(sentHeaders().get('X-CSRF-Token')).toBe('real-token')
    })

    it('preserves "=" characters in the cookie value', async () => {
      cookieValue = 'csrf=abc=def=='
      answer(json(200, {}))

      await ApiClient.post('/api/planner', { name: 'Test' })

      expect(sentHeaders().get('X-CSRF-Token')).toBe('abc=def==')
    })
  })

  describe('server status recording', () => {
    const status = () => useServerStatusStore.getState().status
    const respond = (code: number, body: JsonBodyType = null) =>
      answer(code === 204 ? empty(204) : json(code, body))

    function startFrom(initial: ServerStatus) {
      useServerStatusStore.setState({ status: initial })
    }

    beforeEach(() => {
      startFrom('ok')
    })

    it('records down when a read answers 503 WRITE_TEMPORARILY_UNAVAILABLE', async () => {
      respond(503, { code: 'WRITE_TEMPORARILY_UNAVAILABLE' })

      await expect(ApiClient.get('/api/auth/me')).rejects.toBeInstanceOf(
        WriteTemporarilyUnavailableError,
      )

      expect(status()).toBe('down')
    })

    it('records down when /api/auth/me answers 503 without a code', async () => {
      respond(503, { detail: 'down' })

      await expect(ApiClient.get('/api/auth/me')).rejects.toBeInstanceOf(BackendUnavailableError)

      expect(status()).toBe('down')
    })

    it('records down for a 503 whose body cannot be parsed', async () => {
      answer(unparseable(503))

      await expect(ApiClient.get('/api/planner/md')).rejects.toBeInstanceOf(BackendUnavailableError)

      expect(status()).toBe('down')
    })

    it('records down when the request never reaches a server', async () => {
      answer(() => HttpResponse.error())

      await expect(ApiClient.get('/api/planner/md')).rejects.toBeInstanceOf(TypeError)

      expect(status()).toBe('down')
    })

    it('leaves the status alone when the caller aborted the request', async () => {
      const controller = new AbortController()
      controller.abort()
      answer(json(200, {}))

      await expect(
        ApiClient.get('/api/planner/md', { signal: controller.signal }),
      ).rejects.toBeInstanceOf(DOMException)

      expect(status()).toBe('ok')
    })

    it.each([200, 204])('clears the status on a %i response', async (code) => {
      startFrom('down')
      respond(code, {})

      await ApiClient.get('/api/planner/md')

      expect(status()).toBe('ok')
    })

    const initialStatuses: readonly ServerStatus[] = ['ok', 'down']

    it.each(
      [
        ['AUTH_TEMPORARILY_UNAVAILABLE', AuthTemporarilyUnavailableError],
        ['RATE_LIMIT_TEMPORARILY_UNAVAILABLE', RetryableUnavailableError],
        ['DEADLOCK', RetryableUnavailableError],
        ['SERVICE_UPDATING', ServiceUpdatingError],
      ].flatMap(([code, error]) =>
        initialStatuses.map((initial) => [code, error, initial] as const),
      ),
    )('leaves the status alone for a 503 %s from %s', async (code, error, initial) => {
      startFrom(initial)
      respond(503, { code })

      await expect(ApiClient.get('/api/planner/md')).rejects.toBeInstanceOf(error)

      expect(status()).toBe(initial)
    })

    it.each(
      [401, 404, 409, 429, 500].flatMap((code) =>
        initialStatuses.map((initial) => [code, initial] as const),
      ),
    )('leaves the status alone for a %i from %s', async (code, initial) => {
      startFrom(initial)
      respond(code, {})

      await expect(ApiClient.get('/api/planner/md')).rejects.toBeInstanceOf(Error)

      expect(status()).toBe(initial)
    })
  })
})
