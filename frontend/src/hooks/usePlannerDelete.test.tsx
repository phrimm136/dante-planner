/**
 * usePlannerDelete.test.ts
 *
 * Tests for planner delete mutation hook.
 * Uses Vitest + React Testing Library for testing.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { usePlannerDelete } from './usePlannerDelete'
import { gesellschaftQueryKeys } from './useMDGesellschaftData'
import { userPlannersQueryKeys } from './useMDUserPlannersData'

// Mock the API client
vi.mock('@/lib/api', () => ({
  ApiClient: {
    delete: vi.fn(),
  },
}))

// Import after mocking
import { ApiClient } from '@/lib/api'

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

describe('usePlannerDelete', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  describe('mutation function', () => {
    it('calls correct API endpoint with planner ID', async () => {
      vi.mocked(ApiClient.delete).mockResolvedValue(undefined)
      const { wrapper } = createWrapper()

      const { result } = renderHook(() => usePlannerDelete(), { wrapper })

      await act(async () => {
        await result.current.mutateAsync('123e4567-e89b-12d3-a456-426614174000')
      })

      expect(ApiClient.delete).toHaveBeenCalledWith(
        '/api/planner/md/123e4567-e89b-12d3-a456-426614174000'
      )
    })

    it('returns void on success (204 No Content)', async () => {
      vi.mocked(ApiClient.delete).mockResolvedValue(undefined)
      const { wrapper } = createWrapper()

      const { result } = renderHook(() => usePlannerDelete(), { wrapper })

      let response
      await act(async () => {
        response = await result.current.mutateAsync('123e4567-e89b-12d3-a456-426614174000')
      })

      expect(response).toBeUndefined()
    })
  })

  describe('cache invalidation', () => {
    it('invalidates both gesellschaft and user planner queries on success', async () => {
      vi.mocked(ApiClient.delete).mockResolvedValue(undefined)
      const { wrapper, queryClient } = createWrapper()

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

      const { result } = renderHook(() => usePlannerDelete(), { wrapper })

      await act(async () => {
        await result.current.mutateAsync('123e4567-e89b-12d3-a456-426614174000')
      })

      await waitFor(() => {
        expect(invalidateSpy).toHaveBeenCalledWith({
          queryKey: gesellschaftQueryKeys.all,
        })
      })

      await waitFor(() => {
        expect(invalidateSpy).toHaveBeenCalledWith({
          queryKey: userPlannersQueryKeys.all,
        })
      })
    })

    it('does not invalidate on error', async () => {
      vi.mocked(ApiClient.delete).mockRejectedValue(new Error('Network error'))
      const { wrapper, queryClient } = createWrapper()

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

      const { result } = renderHook(() => usePlannerDelete(), { wrapper })

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

  describe('mutation state', () => {
    it('sets isPending during mutation', async () => {
      let resolvePromise: (value: unknown) => void
      const pendingPromise = new Promise((resolve) => {
        resolvePromise = resolve
      })
      vi.mocked(ApiClient.delete).mockReturnValue(pendingPromise as Promise<unknown>)

      const { wrapper } = createWrapper()
      const { result } = renderHook(() => usePlannerDelete(), { wrapper })

      expect(result.current.isPending).toBe(false)

      act(() => {
        result.current.mutate('123e4567-e89b-12d3-a456-426614174000')
      })

      await waitFor(() => {
        expect(result.current.isPending).toBe(true)
      })

      act(() => {
        resolvePromise!(undefined)
      })

      await waitFor(() => {
        expect(result.current.isPending).toBe(false)
      })
    })

    it('sets isError on failure', async () => {
      vi.mocked(ApiClient.delete).mockRejectedValue(new Error('Delete failed'))
      const { wrapper } = createWrapper()

      const { result } = renderHook(() => usePlannerDelete(), { wrapper })

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

  describe('onSuccess callback', () => {
    it('allows caller to provide onSuccess callback for navigation', async () => {
      vi.mocked(ApiClient.delete).mockResolvedValue(undefined)
      const { wrapper } = createWrapper()

      const onSuccessCallback = vi.fn()

      const { result } = renderHook(() => usePlannerDelete(), { wrapper })

      await act(async () => {
        await result.current.mutateAsync('123e4567-e89b-12d3-a456-426614174000', {
          onSuccess: onSuccessCallback,
        })
      })

      expect(onSuccessCallback).toHaveBeenCalled()
    })
  })
})
