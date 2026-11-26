import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { usePlannerSubscription } from '../usePlannerSubscription'
import { publishedPlannerQueryKeys } from '../usePublishedPlannerQuery'
import type { PublishedPlannerQueryState } from '../usePublishedPlannerQuery'

vi.mock('@/lib/api', () => ({
  ApiClient: {
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

import { ApiClient } from '@/lib/api'

const PLANNER_ID = '123e4567-e89b-12d3-a456-426614174000'
const SUBSCRIPTION_PATH = `/api/planner/md/${PLANNER_ID}/subscription`

const subscribedResponse = { plannerId: PLANNER_ID, subscribed: true }
const unsubscribedResponse = { plannerId: PLANNER_ID, subscribed: false }

function detailState(isSubscribed: boolean): PublishedPlannerQueryState {
  return {
    apiData: { id: PLANNER_ID, isSubscribed },
    planner: {},
  } as unknown as PublishedPlannerQueryState
}

function cachedIsSubscribed(queryClient: QueryClient): boolean | undefined {
  const state = queryClient.getQueryData<PublishedPlannerQueryState>(
    publishedPlannerQueryKeys.detail(PLANNER_ID),
  )
  return state && 'apiData' in state ? state.apiData.isSubscribed : undefined
}

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

describe('usePlannerSubscription', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  describe('intent routing', () => {
    it('subscribes with one PUT to the subscription route', async () => {
      vi.mocked(ApiClient.put).mockResolvedValue(subscribedResponse)
      const { wrapper } = createWrapper()
      const { result } = renderHook(() => usePlannerSubscription(), { wrapper })

      await act(async () => {
        await result.current.mutateAsync({ plannerId: PLANNER_ID, intent: 'subscribe' })
      })

      expect(ApiClient.put).toHaveBeenCalledTimes(1)
      expect(ApiClient.put).toHaveBeenCalledWith(SUBSCRIPTION_PATH)
      expect(ApiClient.delete).not.toHaveBeenCalled()
      expect(ApiClient.post).not.toHaveBeenCalled()
    })

    it('leaves with one DELETE to the subscription route', async () => {
      vi.mocked(ApiClient.delete).mockResolvedValue(unsubscribedResponse)
      const { wrapper } = createWrapper()
      const { result } = renderHook(() => usePlannerSubscription(), { wrapper })

      await act(async () => {
        await result.current.mutateAsync({ plannerId: PLANNER_ID, intent: 'unsubscribe' })
      })

      expect(ApiClient.delete).toHaveBeenCalledTimes(1)
      expect(ApiClient.delete).toHaveBeenCalledWith(SUBSCRIPTION_PATH)
      expect(ApiClient.put).not.toHaveBeenCalled()
      expect(ApiClient.post).not.toHaveBeenCalled()
    })

    it('returns the validated response', async () => {
      vi.mocked(ApiClient.put).mockResolvedValue(subscribedResponse)
      const { wrapper } = createWrapper()
      const { result } = renderHook(() => usePlannerSubscription(), { wrapper })

      let response
      await act(async () => {
        response = await result.current.mutateAsync({ plannerId: PLANNER_ID, intent: 'subscribe' })
      })

      expect(response).toEqual(subscribedResponse)
    })
  })

  describe('cache update', () => {
    it('writes the subscribed state the server answered into the detail', async () => {
      vi.mocked(ApiClient.put).mockResolvedValue(subscribedResponse)
      const { wrapper, queryClient } = createWrapper()
      queryClient.setQueryData(publishedPlannerQueryKeys.detail(PLANNER_ID), detailState(false))
      const { result } = renderHook(() => usePlannerSubscription(), { wrapper })

      await act(async () => {
        await result.current.mutateAsync({ plannerId: PLANNER_ID, intent: 'subscribe' })
      })

      expect(cachedIsSubscribed(queryClient)).toBe(true)
    })

    it('follows the response, not the intent', async () => {
      vi.mocked(ApiClient.delete).mockResolvedValue(subscribedResponse)
      const { wrapper, queryClient } = createWrapper()
      queryClient.setQueryData(publishedPlannerQueryKeys.detail(PLANNER_ID), detailState(true))
      const { result } = renderHook(() => usePlannerSubscription(), { wrapper })

      await act(async () => {
        await result.current.mutateAsync({ plannerId: PLANNER_ID, intent: 'unsubscribe' })
      })

      expect(cachedIsSubscribed(queryClient)).toBe(true)
    })

    it('leaves a removed detail as it is', async () => {
      vi.mocked(ApiClient.put).mockResolvedValue(subscribedResponse)
      const { wrapper, queryClient } = createWrapper()
      queryClient.setQueryData(publishedPlannerQueryKeys.detail(PLANNER_ID), { removed: true })
      const { result } = renderHook(() => usePlannerSubscription(), { wrapper })

      await act(async () => {
        await result.current.mutateAsync({ plannerId: PLANNER_ID, intent: 'subscribe' })
      })

      expect(queryClient.getQueryData(publishedPlannerQueryKeys.detail(PLANNER_ID))).toEqual({
        removed: true,
      })
    })

    it('leaves the detail unchanged on error', async () => {
      vi.mocked(ApiClient.put).mockRejectedValue(new Error('Network error'))
      const { wrapper, queryClient } = createWrapper()
      queryClient.setQueryData(publishedPlannerQueryKeys.detail(PLANNER_ID), detailState(false))
      const { result } = renderHook(() => usePlannerSubscription(), { wrapper })

      await act(async () => {
        await result.current
          .mutateAsync({ plannerId: PLANNER_ID, intent: 'subscribe' })
          .catch(() => undefined)
      })

      expect(cachedIsSubscribed(queryClient)).toBe(false)
    })
  })

  describe('response validation', () => {
    it('throws on invalid response', async () => {
      vi.mocked(ApiClient.put).mockResolvedValue({
        plannerId: 'not-a-uuid',
        subscribed: 'not-a-boolean',
      })
      const { wrapper } = createWrapper()
      const { result } = renderHook(() => usePlannerSubscription(), { wrapper })

      await expect(
        act(async () => {
          await result.current.mutateAsync({ plannerId: PLANNER_ID, intent: 'subscribe' })
        }),
      ).rejects.toThrow(/^\[planner subscription\] Validation failed: /)
    })
  })

  describe('mutation state', () => {
    it('sets isPending during mutation', async () => {
      let resolvePromise: (value: unknown) => void
      const pendingPromise = new Promise((resolve) => {
        resolvePromise = resolve
      })
      vi.mocked(ApiClient.put).mockReturnValue(pendingPromise as Promise<unknown>)

      const { wrapper } = createWrapper()
      const { result } = renderHook(() => usePlannerSubscription(), { wrapper })

      expect(result.current.isPending).toBe(false)

      act(() => {
        result.current.mutate({ plannerId: PLANNER_ID, intent: 'subscribe' })
      })

      await waitFor(() => {
        expect(result.current.isPending).toBe(true)
      })

      act(() => {
        resolvePromise!(subscribedResponse)
      })

      await waitFor(() => {
        expect(result.current.isPending).toBe(false)
      })
    })

    it('sets isError on failure', async () => {
      vi.mocked(ApiClient.put).mockRejectedValue(new Error('Subscription failed'))
      const { wrapper } = createWrapper()
      const { result } = renderHook(() => usePlannerSubscription(), { wrapper })

      await act(async () => {
        await result.current
          .mutateAsync({ plannerId: PLANNER_ID, intent: 'subscribe' })
          .catch(() => undefined)
      })

      await waitFor(() => {
        expect(result.current.isError).toBe(true)
      })

      expect(result.current.error).toBeInstanceOf(Error)
    })
  })
})
