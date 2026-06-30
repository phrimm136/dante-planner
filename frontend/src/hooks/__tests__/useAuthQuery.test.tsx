/**
 * useAuthQuery.test.tsx
 *
 * Tests for authentication query hooks.
 * Covers: error distinction (401 vs 5xx), logout mutation, login mutation
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React, { Suspense } from 'react'
import { authQueryKeys, useLogout, createAuthMeQueryOptions } from '../useAuthQuery'
import { queryClient } from '@/lib/queryClient'


// Mock only the API client; keep the real error classes (instanceof checks rely on them)
vi.mock('@/lib/api', async (importActual) => {
  const actual = await importActual<typeof import('@/lib/api')>()
  return {
    ...actual,
    ApiClient: {
      get: vi.fn(),
      post: vi.fn(),
    },
  }
})

import { ApiClient, BackendUnavailableError, ServiceUpdatingError } from '@/lib/api'

/**
 * Mock user response matching UserSchema
 */
const mockUserResponse = {
  email: 'test@example.com',
  usernameEpithet: 'W_CORP',
  usernameSuffix: 'test1',
  role: 'NORMAL' as const,
}

/**
 * Create a wrapper component with QueryClientProvider
 */
function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })

  return {
    wrapper: function Wrapper({ children }: { children: React.ReactNode }) {
      return (
        <QueryClientProvider client={queryClient}>
          <Suspense fallback={<div>Loading...</div>}>{children}</Suspense>
        </QueryClientProvider>
      )
    },
    queryClient,
  }
}

describe('authQueryKeys', () => {
  it('creates consistent key for me', () => {
    const key = authQueryKeys.me
    expect(key).toEqual(['auth', 'me'])
  })
})

describe('useAuthQuery error handling', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  describe('queryFn behavior', () => {
    const callQueryFn = () =>
      (createAuthMeQueryOptions().queryFn as unknown as () => Promise<unknown>)()

    it('returns null for unauthenticated user (null response)', async () => {
      vi.mocked(ApiClient.get).mockResolvedValue(null)

      const result = await callQueryFn()

      expect(result).toBeNull()
    })

    it('returns validated user for valid response', async () => {
      vi.mocked(ApiClient.get).mockResolvedValue(mockUserResponse)

      const result = await callQueryFn()

      expect(result).toEqual(mockUserResponse)
    })
  })
})

describe('useAuthQuery transient-failure session preservation (Fix 2b)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    queryClient.clear()
  })

  afterEach(() => {
    vi.resetAllMocks()
    queryClient.clear()
  })

  const callQueryFn = () =>
    (createAuthMeQueryOptions().queryFn as unknown as () => Promise<unknown>)()

  it('preserves the cached user when /auth/me hits a transient BackendUnavailableError', async () => {
    queryClient.setQueryData(authQueryKeys.me, mockUserResponse)
    vi.mocked(ApiClient.get).mockRejectedValue(new BackendUnavailableError('server down'))

    const result = await callQueryFn()

    // Transient outage must NOT log the user out — last-known identity is preserved
    expect(result).toEqual(mockUserResponse)
  })

  it('preserves the cached user when /auth/me hits a ServiceUpdatingError (rolling deploy)', async () => {
    queryClient.setQueryData(authQueryKeys.me, mockUserResponse)
    vi.mocked(ApiClient.get).mockRejectedValue(new ServiceUpdatingError('deploying'))

    const result = await callQueryFn()

    // A rolling deploy (SERVICE_UPDATING) is availability, not identity — stay logged in
    expect(result).toEqual(mockUserResponse)
  })

  it('degrades to guest (null) on a transient error when there is no cached identity', async () => {
    queryClient.removeQueries({ queryKey: authQueryKeys.me })
    vi.mocked(ApiClient.get).mockRejectedValue(new BackendUnavailableError('server down'))

    const result = await callQueryFn()

    // Cold load with backend down: can't restore identity, render as guest (page still works)
    expect(result).toBeNull()
  })

  it('degrades to guest (null) on a genuine auth error even with a cached user', async () => {
    queryClient.setQueryData(authQueryKeys.me, mockUserResponse)
    vi.mocked(ApiClient.get).mockRejectedValue(new Error('HTTP error! status: 401'))

    const result = await callQueryFn()

    // A real 401/invalid-token is identity state, not availability — clear it
    expect(result).toBeNull()
  })
})

describe('useLogout', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  it('calls correct API endpoint', async () => {
    vi.mocked(ApiClient.post).mockResolvedValue(undefined)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useLogout(), { wrapper })

    await act(async () => {
      await result.current.mutateAsync()
    })

    expect(ApiClient.post).toHaveBeenCalledWith('/api/auth/logout')
  })

  it('clears auth cache on success', async () => {
    vi.mocked(ApiClient.post).mockResolvedValue(undefined)
    const { wrapper, queryClient } = createWrapper()

    // Pre-populate auth cache
    queryClient.setQueryData(authQueryKeys.me, mockUserResponse)

    const { result } = renderHook(() => useLogout(), { wrapper })

    await act(async () => {
      await result.current.mutateAsync()
    })

    await waitFor(() => {
      expect(queryClient.getQueryData(authQueryKeys.me)).toBeNull()
    })
  })

  it('sets isSuccess on successful logout', async () => {
    vi.mocked(ApiClient.post).mockResolvedValue(undefined)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useLogout(), { wrapper })

    await act(async () => {
      await result.current.mutateAsync()
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })
  })

  it('sets isError on failure', async () => {
    vi.mocked(ApiClient.post).mockRejectedValue(new Error('Logout failed'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useLogout(), { wrapper })

    await act(async () => {
      try {
        await result.current.mutateAsync()
      } catch {
        // Expected
      }
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })
  })
})
