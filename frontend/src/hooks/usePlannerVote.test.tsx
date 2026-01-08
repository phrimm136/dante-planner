/**
 * usePlannerVote.test.ts
 *
 * Tests for planner vote mutation hook.
 * Uses Vitest + React Testing Library for testing.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { usePlannerVote } from './usePlannerVote'
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
const mockVoteResponse = {
  plannerId: '123e4567-e89b-12d3-a456-426614174000',
  vote: 'UP',
  upvoteCount: 11,
  downvoteCount: 2,
}

const mockRemoveVoteResponse = {
  plannerId: '123e4567-e89b-12d3-a456-426614174000',
  vote: null,
  upvoteCount: 10,
  downvoteCount: 2,
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

describe('usePlannerVote', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  describe('mutation function', () => {
    it('calls correct API endpoint with planner ID', async () => {
      vi.mocked(ApiClient.post).mockResolvedValue(mockVoteResponse)
      const { wrapper } = createWrapper()

      const { result } = renderHook(() => usePlannerVote(), { wrapper })

      await act(async () => {
        await result.current.mutateAsync({
          plannerId: '123e4567-e89b-12d3-a456-426614174000',
          voteType: 'UP',
        })
      })

      expect(ApiClient.post).toHaveBeenCalledWith(
        '/api/planner/md/123e4567-e89b-12d3-a456-426614174000/vote',
        { voteType: 'UP' }
      )
    })

    it('sends upvote correctly', async () => {
      vi.mocked(ApiClient.post).mockResolvedValue(mockVoteResponse)
      const { wrapper } = createWrapper()

      const { result } = renderHook(() => usePlannerVote(), { wrapper })

      await act(async () => {
        await result.current.mutateAsync({
          plannerId: '123e4567-e89b-12d3-a456-426614174000',
          voteType: 'UP',
        })
      })

      expect(ApiClient.post).toHaveBeenCalledWith(expect.any(String), { voteType: 'UP' })
    })

    it('sends downvote correctly', async () => {
      const downvoteResponse = { ...mockVoteResponse, vote: 'DOWN', downvoteCount: 3 }
      vi.mocked(ApiClient.post).mockResolvedValue(downvoteResponse)
      const { wrapper } = createWrapper()

      const { result } = renderHook(() => usePlannerVote(), { wrapper })

      await act(async () => {
        await result.current.mutateAsync({
          plannerId: '123e4567-e89b-12d3-a456-426614174000',
          voteType: 'DOWN',
        })
      })

      expect(ApiClient.post).toHaveBeenCalledWith(expect.any(String), { voteType: 'DOWN' })
    })

    it('sends null to remove vote', async () => {
      vi.mocked(ApiClient.post).mockResolvedValue(mockRemoveVoteResponse)
      const { wrapper } = createWrapper()

      const { result } = renderHook(() => usePlannerVote(), { wrapper })

      await act(async () => {
        await result.current.mutateAsync({
          plannerId: '123e4567-e89b-12d3-a456-426614174000',
          voteType: null,
        })
      })

      expect(ApiClient.post).toHaveBeenCalledWith(expect.any(String), { voteType: null })
    })
  })

  describe('cache invalidation', () => {
    it('invalidates planner list queries on success', async () => {
      vi.mocked(ApiClient.post).mockResolvedValue(mockVoteResponse)
      const { wrapper, queryClient } = createWrapper()

      // Spy on invalidateQueries
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

      const { result } = renderHook(() => usePlannerVote(), { wrapper })

      await act(async () => {
        await result.current.mutateAsync({
          plannerId: '123e4567-e89b-12d3-a456-426614174000',
          voteType: 'UP',
        })
      })

      // Wait for the onSuccess callback to run
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

      const { result } = renderHook(() => usePlannerVote(), { wrapper })

      await act(async () => {
        try {
          await result.current.mutateAsync({
            plannerId: '123e4567-e89b-12d3-a456-426614174000',
            voteType: 'UP',
          })
        } catch {
          // Expected to fail
        }
      })

      // Should not have invalidated on error
      expect(invalidateSpy).not.toHaveBeenCalled()
    })
  })

  describe('response handling', () => {
    it('returns vote response data on success', async () => {
      vi.mocked(ApiClient.post).mockResolvedValue(mockVoteResponse)
      const { wrapper } = createWrapper()

      const { result } = renderHook(() => usePlannerVote(), { wrapper })

      let response
      await act(async () => {
        response = await result.current.mutateAsync({
          plannerId: '123e4567-e89b-12d3-a456-426614174000',
          voteType: 'UP',
        })
      })

      expect(response).toEqual({
        plannerId: '123e4567-e89b-12d3-a456-426614174000',
        vote: 'UP',
        upvoteCount: 11,
        downvoteCount: 2,
      })
    })

    it('throws on validation failure', async () => {
      // Mock invalid response
      vi.mocked(ApiClient.post).mockResolvedValue({
        plannerId: 'not-a-uuid',
        vote: 'INVALID',
        upvoteCount: 'not a number',
      })
      const { wrapper } = createWrapper()

      const { result } = renderHook(() => usePlannerVote(), { wrapper })

      await expect(
        act(async () => {
          await result.current.mutateAsync({
            plannerId: '123e4567-e89b-12d3-a456-426614174000',
            voteType: 'UP',
          })
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
      const { result } = renderHook(() => usePlannerVote(), { wrapper })

      expect(result.current.isPending).toBe(false)

      act(() => {
        result.current.mutate({
          plannerId: '123e4567-e89b-12d3-a456-426614174000',
          voteType: 'UP',
        })
      })

      await waitFor(() => {
        expect(result.current.isPending).toBe(true)
      })

      // Resolve the promise
      act(() => {
        resolvePromise!(mockVoteResponse)
      })

      await waitFor(() => {
        expect(result.current.isPending).toBe(false)
      })
    })

    it('sets isError on failure', async () => {
      vi.mocked(ApiClient.post).mockRejectedValue(new Error('Vote failed'))
      const { wrapper } = createWrapper()

      const { result } = renderHook(() => usePlannerVote(), { wrapper })

      await act(async () => {
        try {
          await result.current.mutateAsync({
            plannerId: '123e4567-e89b-12d3-a456-426614174000',
            voteType: 'UP',
          })
        } catch {
          // Expected
        }
      })

      // Wait for error state to be set
      await waitFor(() => {
        expect(result.current.isError).toBe(true)
      })

      expect(result.current.error).toBeInstanceOf(Error)
    })
  })
})
