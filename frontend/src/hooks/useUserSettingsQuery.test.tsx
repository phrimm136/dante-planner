/**
 * useUserSettingsQuery.test.tsx
 *
 * Tests for user settings query and mutation hooks.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { userSettingsQueryKeys, useUpdateEpithetMutation } from './useUserSettingsQuery'
import { authQueryKeys } from './useAuthQuery'

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
