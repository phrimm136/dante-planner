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
import { usePlannerVote } from '../usePlannerVote'
import { gesellschaftQueryKeys } from '../useMDGesellschaftData'

// Mock the API client — must include ConflictError since the hook imports it
const { MockConflictError } = vi.hoisted(() => {
  class MockConflictError extends Error {
    readonly serverVersion: number
    constructor(message: string, serverVersion: number) {
      super(message)
      this.name = 'ConflictError'
      this.serverVersion = serverVersion
    }
  }
  return { MockConflictError }
})

vi.mock('@/lib/api', () => ({
  ApiClient: {
    post: vi.fn(),
  },
  ConflictError: MockConflictError,
}))

// The toast sink is the observable for how many reporters a failure had, and
// translating a key to itself makes the key what the sink receives.
vi.mock('sonner', () => {
  const toastFn = Object.assign(vi.fn(), {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
    dismiss: vi.fn(),
  })
  return { toast: toastFn }
})

vi.mock('@/lib/i18n', () => ({ default: { t: (key: string) => key } }))

// Import after mocking
import { ApiClient } from '@/lib/api'
import { toast as sonnerToast } from 'sonner'
import { ConflictError } from '@/lib/apiErrors'
import { createTestQueryClient } from '@/test-utils/queryClient'

// Mock response data matching VoteResponseSchema
const mockVoteResponse = {
  plannerId: '123e4567-e89b-12d3-a456-426614174000',
  hasUpvoted: true,
  upvoteCount: 11,
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
        '/api/planner/md/123e4567-e89b-12d3-a456-426614174000/upvote',
        { voteType: 'UP' },
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

    it('sends upvote with correct vote type', async () => {
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
        hasUpvoted: true,
        upvoteCount: 11,
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
        }),
      ).rejects.toThrow(/^\[planner vote\] Validation failed: /)
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

  /**
   * Over the caches production ships, so the mutation cache's reporting is in
   * the frame: a hook with its own copy for a rejection has to be the only
   * voice for it, not the first of two.
   */
  describe('failure reporting', () => {
    const plannerId = '123e4567-e89b-12d3-a456-426614174000'

    function renderVoteOverRealCaches() {
      const queryClient = createTestQueryClient()
      return renderHook(() => usePlannerVote(), {
        wrapper: function Wrapper({ children }: { children: React.ReactNode }) {
          return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
        },
      })
    }

    async function voteAndSwallow(result: { current: ReturnType<typeof usePlannerVote> }) {
      await act(async () => {
        try {
          await result.current.mutateAsync({ plannerId, voteType: 'UP' })
        } catch {
          // The rejection is the subject; the hook reports it.
        }
      })
    }

    it('names the duplicate vote once, not alongside a generic report', async () => {
      vi.mocked(ApiClient.post).mockRejectedValue(
        new ConflictError('VOTE_ALREADY_EXISTS', 'already voted', null),
      )
      const { result } = renderVoteOverRealCaches()

      await voteAndSwallow(result)

      await waitFor(() => {
        expect(sonnerToast.error).toHaveBeenCalledWith('planner:toast.alreadyVoted', undefined)
      })
      expect(sonnerToast.error).toHaveBeenCalledOnce()
    })

    it('reports a failure it has no copy for exactly once', async () => {
      vi.mocked(ApiClient.post).mockRejectedValue(new Error('network down'))
      const { result } = renderVoteOverRealCaches()

      await voteAndSwallow(result)

      await waitFor(() => {
        expect(sonnerToast.error).toHaveBeenCalledOnce()
      })
      expect(sonnerToast.error).toHaveBeenCalledWith('common:errors.generic.message', {
        description: expect.anything(),
      })
    })
  })
})
