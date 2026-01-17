/**
 * usePlannerSubscription.test.ts
 *
 * Tests for planner subscription mutation hook.
 * Uses Vitest + React Testing Library for testing.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { usePlannerSubscription } from './usePlannerSubscription'
import { gesellschaftQueryKeys } from './useMDGesellschaftData'

// Mock the API client
vi.mock('@/lib/api', () => ({
  ApiClient: {
    post: vi.fn(),
  },
}))

// Import after mocking
import { ApiClient } from '@/lib/api'

// Mock response data
const mockSubscriptionResponse = {
  plannerId: '123e4567-e89b-12d3-a456-426614174000',
  subscribed: true,
}

const mockUnsubscribeResponse = {
  plannerId: '123e4567-e89b-12d3-a456-426614174000',
  subscribed: false,
}

/**
 * Create a wrapper component with QueryClientProvider
 */
function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
      mutations: {
        retry: false,
      },
    },
  })

  return {
    wrapper: function Wrapper({ children }: { children: React.ReactNode }) {
      return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    },
    queryClient,
  }
}

describe('usePlannerSubscription', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  describe('mutation function', () => {
    it('calls correct API endpoint with planner ID', async () => {
      vi.mocked(ApiClient.post).mockResolvedValue(mockSubscriptionResponse)
      const { wrapper } = createWrapper()

      const { result } = renderHook(() => usePlannerSubscription(), { wrapper })

      await act(async () => {
        await result.current.mutateAsync('123e4567-e89b-12d3-a456-426614174000')
      })

      expect(ApiClient.post).toHaveBeenCalledWith(
        '/api/planner/md/123e4567-e89b-12d3-a456-426614174000/subscribe'
      )
    })

    it('handles subscribe response correctly', async () => {
      vi.mocked(ApiClient.post).mockResolvedValue(mockSubscriptionResponse)
      const { wrapper } = createWrapper()

      const { result } = renderHook(() => usePlannerSubscription(), { wrapper })

      let response
      await act(async () => {
        response = await result.current.mutateAsync('123e4567-e89b-12d3-a456-426614174000')
      })

      expect(response).toEqual({
        plannerId: '123e4567-e89b-12d3-a456-426614174000',
        subscribed: true,
      })
    })

    it('handles unsubscribe response correctly', async () => {
      vi.mocked(ApiClient.post).mockResolvedValue(mockUnsubscribeResponse)
      const { wrapper } = createWrapper()

      const { result } = renderHook(() => usePlannerSubscription(), { wrapper })

      let response
      await act(async () => {
        response = await result.current.mutateAsync('123e4567-e89b-12d3-a456-426614174000')
      })

      expect(response).toEqual({
        plannerId: '123e4567-e89b-12d3-a456-426614174000',
        subscribed: false,
      })
    })
  })

  describe('cache invalidation', () => {
    it('invalidates planner list queries on success', async () => {
      vi.mocked(ApiClient.post).mockResolvedValue(mockSubscriptionResponse)
      const { wrapper, queryClient } = createWrapper()

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

      const { result } = renderHook(() => usePlannerSubscription(), { wrapper })

      await act(async () => {
        await result.current.mutateAsync('123e4567-e89b-12d3-a456-426614174000')
      })

      await waitFor(() => {
        expect(invalidateSpy).toHaveBeenCalledWith({
          queryKey: gesellschaftQueryKeys.all,
        })
      })
    })

    it('does not invalidate on error', async () => {
      vi.mocked(ApiClient.post).mockRejectedValue(new Error('Network error'))
      const { wrapper, queryClient } = createWrapper()

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

      const { result } = renderHook(() => usePlannerSubscription(), { wrapper })

      await act(async () => {
        try {
          await result.current.mutateAsync('123e4567-e89b-12d3-a456-426614174000')
        } catch {
          // Expected to fail
        }
      })

      expect(invalidateSpy).not.toHaveBeenCalled()
    })
  })

  describe('response validation', () => {
    it('throws on invalid response', async () => {
      vi.mocked(ApiClient.post).mockResolvedValue({
        plannerId: 'not-a-uuid',
        subscribed: 'not-a-boolean',
      })
      const { wrapper } = createWrapper()

      const { result } = renderHook(() => usePlannerSubscription(), { wrapper })

      await expect(
        act(async () => {
          await result.current.mutateAsync('123e4567-e89b-12d3-a456-426614174000')
        })
      ).rejects.toThrow()
    })
  })

  describe('mutation state', () => {
    it('sets isPending during mutation', async () => {
      let resolvePromise: (value: unknown) => void
      const pendingPromise = new Promise((resolve) => {
        resolvePromise = resolve
      })
      vi.mocked(ApiClient.post).mockReturnValue(pendingPromise as Promise<unknown>)

      const { wrapper } = createWrapper()
      const { result } = renderHook(() => usePlannerSubscription(), { wrapper })

      expect(result.current.isPending).toBe(false)

      act(() => {
        result.current.mutate('123e4567-e89b-12d3-a456-426614174000')
      })

      await waitFor(() => {
        expect(result.current.isPending).toBe(true)
      })

      act(() => {
        resolvePromise!(mockSubscriptionResponse)
      })

      await waitFor(() => {
        expect(result.current.isPending).toBe(false)
      })
    })

    it('sets isError on failure', async () => {
      vi.mocked(ApiClient.post).mockRejectedValue(new Error('Subscription failed'))
      const { wrapper } = createWrapper()

      const { result } = renderHook(() => usePlannerSubscription(), { wrapper })

      await act(async () => {
        try {
          await result.current.mutateAsync('123e4567-e89b-12d3-a456-426614174000')
        } catch {
          // Expected
        }
      })

      await waitFor(() => {
        expect(result.current.isError).toBe(true)
      })

      expect(result.current.error).toBeInstanceOf(Error)
    })
  })
})
