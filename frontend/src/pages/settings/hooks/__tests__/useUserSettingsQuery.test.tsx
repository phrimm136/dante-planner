/**
 * useUserSettingsQuery.test.tsx
 *
 * Tests for user settings query and mutation hooks.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React, { Suspense } from 'react'
import {
  userSettingsQueryKeys,
  useUpdateEpithetMutation,
  useEpithetsQuery,
} from '../useUserSettingsQuery'
import { authQueryKeys } from '@/shared/auth'
import { STALE_TIME } from '@/lib/constants'

// Mock the API client
vi.mock('@/lib/api', () => ({
  ApiClient: {
    get: vi.fn(),
    put: vi.fn(),
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
      return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    },
    queryClient,
  }
}

describe('userSettingsQueryKeys', () => {
  it('creates consistent key for epithets', () => {
    const key = userSettingsQueryKeys.epithets()
    expect(key).toEqual(['user', 'epithets'])
  })
})

describe('useEpithetsQuery — staleness window', () => {
  const EPITHETS = { epithets: ['W_CORP', 'NAIVE'] }

  function createSuspenseWrapper() {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    return {
      queryClient,
      wrapper: function Wrapper({ children }: { children: React.ReactNode }) {
        return (
          <QueryClientProvider client={queryClient}>
            <Suspense fallback={null}>{children}</Suspense>
          </QueryClientProvider>
        )
      },
    }
  }

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(ApiClient.get).mockResolvedValue(EPITHETS)
  })

  it('re-mounting on a fresh cache reads the cache instead of refetching', async () => {
    const { wrapper } = createSuspenseWrapper()

    const first = renderHook(() => useEpithetsQuery(), { wrapper })
    await waitFor(() => expect(first.result.current?.epithets).toEqual(EPITHETS.epithets))
    first.unmount()

    const second = renderHook(() => useEpithetsQuery(), { wrapper })
    await waitFor(() => expect(second.result.current?.epithets).toEqual(EPITHETS.epithets))

    expect(ApiClient.get).toHaveBeenCalledTimes(1)
  })

  it('re-mounting past the staleness window refetches', async () => {
    const { queryClient, wrapper } = createSuspenseWrapper()

    const first = renderHook(() => useEpithetsQuery(), { wrapper })
    await waitFor(() => expect(first.result.current?.epithets).toEqual(EPITHETS.epithets))
    first.unmount()

    // Backdate the cached entry past the window rather than moving the clock,
    // which react-query reads through Date.now on both sides of the comparison.
    const cached = queryClient.getQueryCache().find({ queryKey: userSettingsQueryKeys.epithets() })
    cached?.setState({ dataUpdatedAt: Date.now() - STALE_TIME.LONG - 1 })

    const second = renderHook(() => useEpithetsQuery(), { wrapper })
    await waitFor(() => expect(ApiClient.get).toHaveBeenCalledTimes(2))
    second.unmount()
  })
})

describe('useUpdateEpithetMutation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  it('calls correct API endpoint with epithet', async () => {
    vi.mocked(ApiClient.put).mockResolvedValue(mockUserResponse)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useUpdateEpithetMutation(), { wrapper })

    await act(async () => {
      await result.current.mutateAsync({ epithet: 'W_CORP' })
    })

    expect(ApiClient.put).toHaveBeenCalledWith('/api/user/me/username-epithet', {
      epithet: 'W_CORP',
    })
  })

  it('updates auth cache with new user data on success', async () => {
    vi.mocked(ApiClient.put).mockResolvedValue(mockUserResponse)
    const { wrapper, queryClient } = createWrapper()

    const setQueryDataSpy = vi.spyOn(queryClient, 'setQueryData')

    const { result } = renderHook(() => useUpdateEpithetMutation(), { wrapper })

    await act(async () => {
      await result.current.mutateAsync({ epithet: 'W_CORP' })
    })

    await waitFor(() => {
      expect(setQueryDataSpy).toHaveBeenCalledWith(authQueryKeys.me, mockUserResponse)
    })
  })

  it('returns user data on success', async () => {
    vi.mocked(ApiClient.put).mockResolvedValue(mockUserResponse)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useUpdateEpithetMutation(), { wrapper })

    let response
    await act(async () => {
      response = await result.current.mutateAsync({ epithet: 'W_CORP' })
    })

    expect(response).toEqual(mockUserResponse)
  })

  it('sets isError on failure', async () => {
    vi.mocked(ApiClient.put).mockRejectedValue(new Error('Update failed'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useUpdateEpithetMutation(), { wrapper })

    await act(async () => {
      try {
        await result.current.mutateAsync({ epithet: 'W_CORP' })
      } catch {
        // Expected
      }
    })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })
  })

  it('sets isSuccess on successful mutation', async () => {
    vi.mocked(ApiClient.put).mockResolvedValue(mockUserResponse)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useUpdateEpithetMutation(), { wrapper })

    await act(async () => {
      await result.current.mutateAsync({ epithet: 'W_CORP' })
    })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })
  })
})
