/**
 * usePublishedPlannerQuery.test.ts
 *
 * A published planner opened from a stale list may already be gone. That 404
 * is an answer the page renders, not an error the boundary catches.
 */

import { QueryClient } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'

import { NotFoundError, ForbiddenError } from '@/lib/api'

const apiMocks = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@/lib/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/api')>()),
  ApiClient: { get: apiMocks.get },
}))

import {
  fetchPublishedPlanner,
  isPlannerRemoved,
  publishedPlannerStaleTime,
  publishedPlannerQueryKeys,
  type PublishedPlannerQueryResult,
} from '../usePublishedPlannerQuery'
import { STALE_TIME } from '@/lib/constants'

beforeEach(() => {
  apiMocks.get.mockReset()
})

describe('fetchPublishedPlanner', () => {
  it('reports a 404 as removed instead of throwing', async () => {
    apiMocks.get.mockRejectedValue(new NotFoundError('Resource not found'))

    const state = await fetchPublishedPlanner('planner-1')

    expect(isPlannerRemoved(state)).toBe(true)
  })

  it('still throws every other failure to the boundary', async () => {
    apiMocks.get.mockRejectedValue(new ForbiddenError('PLANNER_FORBIDDEN', 'nope'))

    await expect(fetchPublishedPlanner('planner-1')).rejects.toBeInstanceOf(ForbiddenError)
  })
})

describe('publishedPlannerStaleTime', () => {
  it('makes a removed verdict stale at once so a remount re-asks the server', () => {
    // Unpublishing is reversible and no event announces the republish, so a
    // cached removal that kept its freshness would outlive the truth.
    expect(publishedPlannerStaleTime({ removed: true })).toBe(0)
  })

  it('leaves a real planner its ordinary freshness', () => {
    const loaded = { apiData: {}, planner: {} } as unknown as PublishedPlannerQueryResult

    expect(publishedPlannerStaleTime(loaded)).toBe(STALE_TIME.MEDIUM)
  })

  it('treats an empty cache as ordinary rather than removed', () => {
    expect(publishedPlannerStaleTime(undefined)).toBe(STALE_TIME.MEDIUM)
  })
})

/**
 * The route loader is what a fresh navigation runs, and its `fetchQuery` is not
 * a suspense observer, so the zero staleTime survives here. These assertions
 * cover the navigation path the removed verdict has to recover through.
 */
describe('published planner navigation freshness', () => {
  function navigate(cached: unknown) {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    queryClient.setQueryData(publishedPlannerQueryKeys.detail('p1'), cached)
    return queryClient.fetchQuery({
      queryKey: publishedPlannerQueryKeys.detail('p1'),
      queryFn: ({ signal }) => fetchPublishedPlanner('p1', signal),
      staleTime: (query) => publishedPlannerStaleTime(query.state.data),
    })
  }

  it('re-asks the server when the cached verdict is removed', async () => {
    apiMocks.get.mockRejectedValue(new NotFoundError('gone'))

    await navigate({ removed: true })

    expect(apiMocks.get).toHaveBeenCalledTimes(1)
  })

  it('serves a cached planner without going back to the server', async () => {
    apiMocks.get.mockRejectedValue(new Error('must not be called'))

    const result = await navigate({ apiData: { title: 'Kept' }, planner: {} })

    expect(apiMocks.get).not.toHaveBeenCalled()
    expect(isPlannerRemoved(result)).toBe(false)
  })
})
