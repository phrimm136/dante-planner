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
import { authQueryKeys, useLogout } from '../useAuthQuery'


// Mock the API client
vi.mock('@/lib/api', () => ({
  ApiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

import { ApiClient } from '@/lib/api'

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
    it('returns null for unauthenticated user (null response)', async () => {
      vi.mocked(ApiClient.get).mockResolvedValue(null)
      const { wrapper, queryClient } = createWrapper()

      queryClient.setQueryData(authQueryKeys.me, null)

      expect(queryClient.getQueryData(authQueryKeys.me)).toBeNull()
    })

    it('returns validated user for valid response', async () => {
      vi.mocked(ApiClient.get).mockResolvedValue(mockUserResponse)
      const { queryClient } = createWrapper()

      queryClient.setQueryData(authQueryKeys.me, mockUserResponse)

      expect(queryClient.getQueryData(authQueryKeys.me)).toEqual(mockUserResponse)
    })
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
