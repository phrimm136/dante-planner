/**
 * usePlannerReport.test.ts
 *
 * Tests for planner report mutation hook.
 * Uses Vitest + React Testing Library for testing.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { usePlannerReport } from './usePlannerReport'
import { gesellschaftQueryKeys } from './useMDGesellschaftData'

// Mock the API client
vi.mock('@/lib/api', () => ({
  ApiClient: {
    post: vi.fn(),
  },
  ConflictError: class ConflictError extends Error {
    constructor(message: string) {
      super(message)
      this.name = 'ConflictError'
    }
  },
}))

// Import after mocking
import { ApiClient, ConflictError } from '@/lib/api'

// Mock response data
const mockReportResponse = {
  plannerId: '123e4567-e89b-12d3-a456-426614174000',
  message: 'Report submitted successfully',
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

describe('usePlannerReport', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  describe('mutation function', () => {
    it('calls correct API endpoint with planner ID', async () => {
      vi.mocked(ApiClient.post).mockResolvedValue(mockReportResponse)
      const { wrapper } = createWrapper()

      const { result } = renderHook(() => usePlannerReport(), { wrapper })

      await act(async () => {
        await result.current.mutateAsync('123e4567-e89b-12d3-a456-426614174000')
      })

      expect(ApiClient.post).toHaveBeenCalledWith(
        '/api/planner/md/123e4567-e89b-12d3-a456-426614174000/report'
      )
    })

    it('returns report response data on success', async () => {
      vi.mocked(ApiClient.post).mockResolvedValue(mockReportResponse)
      const { wrapper } = createWrapper()

      const { result } = renderHook(() => usePlannerReport(), { wrapper })

      let response
      await act(async () => {
        response = await result.current.mutateAsync('123e4567-e89b-12d3-a456-426614174000')
      })

      expect(response).toEqual({
        plannerId: '123e4567-e89b-12d3-a456-426614174000',
        message: 'Report submitted successfully',
      })
    })
  })

  describe('cache invalidation', () => {
    it('invalidates planner list queries on success', async () => {
      vi.mocked(ApiClient.post).mockResolvedValue(mockReportResponse)
      const { wrapper, queryClient } = createWrapper()

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

      const { result } = renderHook(() => usePlannerReport(), { wrapper })

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

      const { result } = renderHook(() => usePlannerReport(), { wrapper })

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

  describe('conflict handling', () => {
    it('handles ConflictError for duplicate report', async () => {
      vi.mocked(ApiClient.post).mockRejectedValue(new ConflictError('Already reported'))
      const { wrapper } = createWrapper()

      const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

      const { result } = renderHook(() => usePlannerReport(), { wrapper })

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

      expect(consoleErrorSpy).toHaveBeenCalledWith(
        'Report already exists - users can only report each planner once'
      )

      consoleErrorSpy.mockRestore()
    })
  })

  describe('response validation', () => {
    it('throws on invalid response', async () => {
      vi.mocked(ApiClient.post).mockResolvedValue({
        plannerId: 'not-a-uuid',
        message: 12345, // should be a string
      })
      const { wrapper } = createWrapper()

      const { result } = renderHook(() => usePlannerReport(), { wrapper })

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
      const { result } = renderHook(() => usePlannerReport(), { wrapper })

      expect(result.current.isPending).toBe(false)

      act(() => {
        result.current.mutate('123e4567-e89b-12d3-a456-426614174000')
      })

      await waitFor(() => {
        expect(result.current.isPending).toBe(true)
      })

      act(() => {
        resolvePromise!(mockReportResponse)
      })

      await waitFor(() => {
        expect(result.current.isPending).toBe(false)
      })
    })

    it('sets isError on failure', async () => {
      vi.mocked(ApiClient.post).mockRejectedValue(new Error('Report failed'))
      const { wrapper } = createWrapper()

      const { result } = renderHook(() => usePlannerReport(), { wrapper })

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
