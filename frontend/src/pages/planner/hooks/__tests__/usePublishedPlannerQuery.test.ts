/**
 * usePublishedPlannerQuery.test.ts
 *
 * A published planner opened from a stale list may already be gone. That 404
 * is an answer the page renders, not an error the boundary catches.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'

import { NotFoundError, ForbiddenError } from '@/lib/api'

const apiMocks = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@/lib/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/api')>()),
  ApiClient: { get: apiMocks.get },
}))

import { fetchPublishedPlanner, isPlannerRemoved } from '../usePublishedPlannerQuery'

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
    apiMocks.get.mockRejectedValue(new ForbiddenError('nope'))

    await expect(fetchPublishedPlanner('planner-1')).rejects.toBeInstanceOf(ForbiddenError)
  })
})
