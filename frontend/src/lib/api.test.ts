/**
 * api.test.ts
 *
 * Tests for ApiClient request/response handling.
 * Covers: 204 No Content, 401 refresh flow, 409 Conflict, error propagation
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ApiClient, ConflictError } from './api'

// Mock the env module
vi.mock('./env', () => ({
  env: {
    VITE_API_BASE_URL: 'http://localhost:8080',
  },
}))

// Mock global fetch
const mockFetch = vi.fn()
global.fetch = mockFetch

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
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  describe('204 No Content handling', () => {
    it('returns undefined for 204 response without parsing JSON', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 204,
        json: vi.fn().mockRejectedValue(new Error('Should not be called')),
      })

      const result = await ApiClient.delete('/api/auth/logout')

      expect(result).toBeUndefined()
      expect(mockFetch).toHaveBeenCalledWith(
        'http://localhost:8080/api/auth/logout',
        expect.objectContaining({ method: 'DELETE' })
      )
    })

    it('handles DELETE operations returning 204', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 204,
      })

      const result = await ApiClient.delete('/api/planner/123')

      expect(result).toBeUndefined()
    })

    it('handles POST logout returning 204', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 204,
      })

      const result = await ApiClient.post('/api/auth/logout')

      expect(result).toBeUndefined()
    })
  })

  describe('200 OK with JSON body', () => {
    it('parses JSON response for 200 status', async () => {
      const mockData = { id: 1, email: 'test@example.com' }
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: vi.fn().mockResolvedValue(mockData),
      })

      const result = await ApiClient.get('/api/auth/me')

      expect(result).toEqual(mockData)
    })
  })

  describe('401 Unauthorized handling', () => {
    it('triggers refresh for TOKEN_EXPIRED on any endpoint', async () => {
      // First call: 401 with TOKEN_EXPIRED
      // Second call: successful refresh
      // Third call: retry original request succeeds
      mockFetch
        .mockResolvedValueOnce({
          ok: false,
          status: 401,
          json: vi.fn().mockResolvedValue({ error: 'TOKEN_EXPIRED' }),
        })
        .mockResolvedValueOnce({
          ok: true,
          status: 200,
        })
        .mockResolvedValueOnce({
          ok: true,
          status: 200,
          json: vi.fn().mockResolvedValue({ id: 1 }),
        })

      const result = await ApiClient.get('/api/planner/123')

      expect(mockFetch).toHaveBeenCalledTimes(3)
      expect(mockFetch).toHaveBeenNthCalledWith(
        2,
        'http://localhost:8080/api/auth/refresh',
        expect.objectContaining({ method: 'POST' })
      )
      expect(result).toEqual({ id: 1 })
    })

    it('triggers refresh for TOKEN_EXPIRED on /auth/me', async () => {
      mockFetch
        .mockResolvedValueOnce({
          ok: false,
          status: 401,
          json: vi.fn().mockResolvedValue({ error: 'TOKEN_EXPIRED' }),
        })
        .mockResolvedValueOnce({
          ok: true,
          status: 200,
        })
        .mockResolvedValueOnce({
          ok: true,
          status: 200,
          json: vi.fn().mockResolvedValue({ id: 1, email: 'test@example.com' }),
        })

      const result = await ApiClient.get('/api/auth/me')

      expect(mockFetch).toHaveBeenCalledTimes(3)
      expect(result).toEqual({ id: 1, email: 'test@example.com' })
    })

    it('does NOT refresh for TOKEN_MISSING (guest user)', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 401,
        json: vi.fn().mockResolvedValue({ error: 'TOKEN_MISSING' }),
      })

      await expect(ApiClient.get('/api/auth/me')).rejects.toThrow('HTTP error! status: 401')

      // Should only call once, no refresh attempt
      expect(mockFetch).toHaveBeenCalledTimes(1)
    })

    it('does NOT refresh for TOKEN_INVALID (malformed/tampered token)', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 401,
        json: vi.fn().mockResolvedValue({ error: 'TOKEN_INVALID' }),
      })

      await expect(ApiClient.get('/api/auth/me')).rejects.toThrow('HTTP error! status: 401')

      expect(mockFetch).toHaveBeenCalledTimes(1)
    })

    it('does NOT refresh for TOKEN_EXPIRED on /auth/refresh (prevent loop)', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 401,
        json: vi.fn().mockResolvedValue({ error: 'TOKEN_EXPIRED' }),
      })

      await expect(ApiClient.post('/api/auth/refresh')).rejects.toThrow('HTTP error! status: 401')

      expect(mockFetch).toHaveBeenCalledTimes(1)
    })

    it('does NOT refresh for TOKEN_EXPIRED on /auth/logout (prevent loop)', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 401,
        json: vi.fn().mockResolvedValue({ error: 'TOKEN_EXPIRED' }),
      })

      await expect(ApiClient.post('/api/auth/logout')).rejects.toThrow('HTTP error! status: 401')

      expect(mockFetch).toHaveBeenCalledTimes(1)
    })

    it('redirects to home when refresh fails', async () => {
      mockFetch
        .mockResolvedValueOnce({
          ok: false,
          status: 401,
          json: vi.fn().mockResolvedValue({ error: 'TOKEN_EXPIRED' }),
        })
        .mockResolvedValueOnce({
          ok: false,
          status: 401,
        })

      await expect(ApiClient.get('/api/planner/123')).rejects.toThrow('Token refresh failed')

      expect(mockLocation.href).toBe('/')
    })

    it('treats unknown error code as no-refresh (safe fallback)', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 401,
        json: vi.fn().mockResolvedValue({ error: 'UNKNOWN_ERROR' }),
      })

      await expect(ApiClient.get('/api/auth/me')).rejects.toThrow('HTTP error! status: 401')

      expect(mockFetch).toHaveBeenCalledTimes(1)
    })

    it('handles JSON parse failure gracefully (no refresh)', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 401,
        json: vi.fn().mockRejectedValue(new Error('Parse error')),
      })

      await expect(ApiClient.get('/api/auth/me')).rejects.toThrow('HTTP error! status: 401')

      expect(mockFetch).toHaveBeenCalledTimes(1)
    })
  })

  describe('409 Conflict handling', () => {
    it('throws ConflictError with serverVersion from response', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 409,
        json: vi.fn().mockResolvedValue({
          code: 'VERSION_CONFLICT',
          message: 'Version conflict',
          serverVersion: 5,
        }),
      })

      await expect(ApiClient.put('/api/planner/123', { version: 3 })).rejects.toThrow(ConflictError)

      try {
        await ApiClient.put('/api/planner/123', { version: 3 })
      } catch (error) {
        expect(error).toBeInstanceOf(ConflictError)
        expect((error as ConflictError).serverVersion).toBe(5)
      }
    })

    it('defaults serverVersion to 1 when response body parsing fails', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 409,
        json: vi.fn().mockRejectedValue(new Error('Parse error')),
      })

      try {
        await ApiClient.put('/api/planner/123', { version: 3 })
      } catch (error) {
        expect(error).toBeInstanceOf(ConflictError)
        expect((error as ConflictError).serverVersion).toBe(1)
      }
    })
  })

  describe('Error propagation', () => {
    it('throws error with status for 4xx responses', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 404,
      })

      await expect(ApiClient.get('/api/planner/nonexistent')).rejects.toThrow(
        'HTTP error! status: 404'
      )
    })

    it('throws error with status for 5xx responses', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
      })

      await expect(ApiClient.get('/api/planner/123')).rejects.toThrow('HTTP error! status: 500')
    })
  })

  describe('Request configuration', () => {
    it('includes credentials for cookie-based auth', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: vi.fn().mockResolvedValue({}),
      })

      await ApiClient.get('/api/auth/me')

      expect(mockFetch).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({ credentials: 'include' })
      )
    })

    it('sets Content-Type to application/json', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: vi.fn().mockResolvedValue({}),
      })

      await ApiClient.post('/api/planner', { name: 'Test' })

      expect(mockFetch).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({
          headers: expect.objectContaining({
            'Content-Type': 'application/json',
          }),
        })
      )
    })

    it('stringifies request body for POST', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: vi.fn().mockResolvedValue({}),
      })

      const data = { name: 'Test', version: 1 }
      await ApiClient.post('/api/planner', data)

      expect(mockFetch).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({
          body: JSON.stringify(data),
        })
      )
    })
  })
})
