import { describe, it, expect, vi } from 'vitest'

import { deferred } from './deferred'
import { publishedPayload } from './publishedPlannerPayload'

const apiMocks = vi.hoisted(() => ({ get: vi.fn() }))
const importGate = vi.hoisted(() => ({ opened: Promise.resolve(), loaded: false }))

vi.mock('@/lib/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/api')>()),
  ApiClient: { get: apiMocks.get },
}))

vi.mock('../usePublishedPlannerQuery', async (importOriginal) => {
  await importGate.opened
  const module = await importOriginal<typeof import('../usePublishedPlannerQuery')>()
  importGate.loaded = true
  return module
})

import { loadPublishedPlanner } from '@/lib/routeLoaders'

describe('loadPublishedPlanner on a cold start', () => {
  it('starts the detail request before the validating module has loaded', async () => {
    const gate = deferred<void>()
    importGate.opened = gate.promise
    apiMocks.get.mockResolvedValue(publishedPayload)
    const abortController = new AbortController()

    const result = loadPublishedPlanner({ params: { id: 'p1' }, abortController })
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(importGate.loaded).toBe(false)
    expect(apiMocks.get).toHaveBeenCalledTimes(1)
    expect(apiMocks.get).toHaveBeenCalledWith('/api/planner/md/published/p1', {
      signal: abortController.signal,
    })

    gate.resolve()
    await expect(result).resolves.toEqual({ title: 'Published run' })
    expect(importGate.loaded).toBe(true)
    expect(apiMocks.get).toHaveBeenCalledTimes(1)
  })
})
