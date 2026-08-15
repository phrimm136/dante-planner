import { describe, it, expect, vi, beforeEach } from 'vitest'
import { BATCH_PULL_MAX_IDS } from '@/lib/constants'

const mockPost = vi.hoisted(() => vi.fn())

vi.mock('@/lib/api', () => ({
  ApiClient: { post: (...args: unknown[]) => mockPost(...args) },
}))

import { plannerApi } from '../plannerApi'
import type { ServerPlannerResponse } from '../../types/PlannerTypes'

/**
 * A response row that satisfies ServerPlannerResponseSchema. It carries the
 * backend's contentDigest, which the client tolerates and never reads.
 */
function responseFor(id: string) {
  return {
    id,
    title: 'Test Planner',
    category: '5F',
    status: 'saved',
    content: '{}',
    contentDigest: 'ab'.repeat(32),
    schemaVersion: 2,
    contentVersion: 6,
    plannerType: 'MIRROR_DUNGEON',
    syncVersion: 1,
    published: false,
    createdAt: '2026-01-01T00:00:00.000Z',
    lastModifiedAt: '2026-01-01T00:00:00.000Z',
  }
}

function uuidAt(index: number): string {
  return `550e8400-e29b-41d4-a716-${String(index).padStart(12, '0')}`
}

function idsOf(count: number): string[] {
  return Array.from({ length: count }, (_, i) => uuidAt(i))
}

async function collect(ids: string[]): Promise<ServerPlannerResponse[]> {
  const out: ServerPlannerResponse[] = []
  for await (const chunk of plannerApi.batchChunks(ids)) out.push(...chunk)
  return out
}

describe('plannerApi.batchChunks', () => {
  beforeEach(() => {
    mockPost.mockReset()
    mockPost.mockImplementation(async (_url: string, body: { ids: string[] }) =>
      body.ids.map(responseFor),
    )
  })

  it('sends a residue at the id cap as a single request', async () => {
    const result = await collect(idsOf(BATCH_PULL_MAX_IDS))

    expect(mockPost).toHaveBeenCalledTimes(1)
    expect(result).toHaveLength(BATCH_PULL_MAX_IDS)
  })

  it('chunks a residue one over the id cap into two requests', async () => {
    const result = await collect(idsOf(BATCH_PULL_MAX_IDS + 1))

    expect(mockPost).toHaveBeenCalledTimes(2)
    const [firstBody, secondBody] = mockPost.mock.calls.map((call) => call[1] as { ids: string[] })
    expect(firstBody?.ids).toHaveLength(BATCH_PULL_MAX_IDS)
    expect(secondBody?.ids).toHaveLength(1)
    expect(result).toHaveLength(BATCH_PULL_MAX_IDS + 1)
  })

  it('issues no request for an empty id list', async () => {
    const result = await collect([])

    expect(mockPost).not.toHaveBeenCalled()
    expect(result).toEqual([])
  })

  it('accepts a response that omits ids, which is not positionally aligned', async () => {
    mockPost.mockImplementation(async (_url: string, body: { ids: string[] }) =>
      body.ids.filter((id) => id !== uuidAt(1)).map(responseFor),
    )

    const result = await collect([uuidAt(0), uuidAt(1), uuidAt(2)])

    expect(result.map((planner) => planner.id)).toEqual([uuidAt(0), uuidAt(2)])
  })

  it('yields earlier chunks before a later one fails', async () => {
    mockPost.mockImplementationOnce(async (_url: string, body: { ids: string[] }) =>
      body.ids.map(responseFor),
    )
    mockPost.mockImplementationOnce(async () => {
      throw new Error('second chunk failed')
    })

    const delivered: ServerPlannerResponse[] = []
    await expect(
      (async () => {
        for await (const chunk of plannerApi.batchChunks(idsOf(BATCH_PULL_MAX_IDS + 1))) {
          delivered.push(...chunk)
        }
      })(),
    ).rejects.toThrow('second chunk failed')

    expect(delivered).toHaveLength(BATCH_PULL_MAX_IDS)
  })

  it('fails a whole chunk when one of its rows is malformed', async () => {
    mockPost.mockImplementation(async (_url: string, body: { ids: string[] }) =>
      // The second row of the chunk carries an out-of-range syncVersion.
      body.ids.map((id, index) =>
        index === 1 ? { ...responseFor(id), syncVersion: -3 } : responseFor(id),
      ),
    )

    await expect(collect([uuidAt(0), uuidAt(1)])).rejects.toThrow(
      'Too small: expected number to be >0',
    )
  })
})
