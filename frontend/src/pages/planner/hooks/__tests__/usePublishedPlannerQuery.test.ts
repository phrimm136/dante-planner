/**
 * usePublishedPlannerQuery.test.ts
 *
 * A published planner opened from a stale list may already be gone. That 404
 * is an answer the page renders, not an error the boundary catches.
 */

import { QueryClient } from '@tanstack/react-query'
import { describe, it, expect, vi, beforeEach } from 'vitest'

import { NotFoundError, ForbiddenError } from '@/lib/apiErrors'

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
import { isMDPlanner } from '../../types/PlannerTypes'

beforeEach(() => {
  apiMocks.get.mockReset()
})

describe('fetchPublishedPlanner', () => {
  it('reports a 404 as removed instead of throwing', async () => {
    apiMocks.get.mockRejectedValue(new NotFoundError('Resource not found'))

    const state = await fetchPublishedPlanner('planner-1')

    expect(isPlannerRemoved(state)).toBe(true)
  })

  it('accepts a real published payload through draft validation', async () => {
    // A positive control for the ingest gate: the content schemas are strict, so
    // this fails the moment a server field stops matching what they accept.
    const content = {
      selectedKeywords: ['Combustion'],
      selectedBuffIds: [101],
      selectedGiftKeyword: 'Combustion',
      selectedGiftIds: ['9001'],
      observationGiftIds: ['9002'],
      comprehensiveGiftIds: ['19003'],
      equipment: {},
      deploymentOrder: [0, 1, 2],
      skillEAState: { '1': { 0: 3, 1: 2, 2: 1 } },
      floorSelections: [{ themePackId: '1001', difficulty: 1, giftIds: ['9001'] }],
      sectionNotes: {},
    }
    apiMocks.get.mockResolvedValue({
      id: '00000000-0000-4000-8000-000000000001',
      title: 'Published run',
      plannerType: 'MIRROR_DUNGEON',
      category: '5F',
      selectedKeywords: ['Combustion'],
      upvotes: 0,
      viewCount: 0,
      createdAt: '2026-01-01T00:00:00Z',
      firstPublishedAt: '2026-01-01T00:00:00Z',
      hasUpvoted: false,
      isBookmarked: false,
      commentCount: 0,
      lastModifiedAt: '2026-01-02T00:00:00Z',
      content: JSON.stringify(content),
      schemaVersion: 2,
      contentVersion: 7,
      status: 'saved',
      syncVersion: 1,
      isSubscribed: false,
      hasReported: false,
      ownerNotificationsEnabled: false,
    })

    const state = await fetchPublishedPlanner('planner-1')

    expect(isPlannerRemoved(state)).toBe(false)
    if (isPlannerRemoved(state)) throw new Error('expected a loaded planner')
    expect(state.planner.config).toEqual({ type: 'MIRROR_DUNGEON', category: '5F' })
    expect(state.planner.metadata.deviceId).toBe('published')
    if (!isMDPlanner(state.planner)) throw new Error('expected a Mirror Dungeon planner')
    expect(state.planner.content.selectedGiftIds).toEqual(['9001'])
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
