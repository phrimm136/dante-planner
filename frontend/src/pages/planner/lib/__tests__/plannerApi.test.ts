import { describe, it, expect, vi, beforeEach } from 'vitest'
import { BATCH_PULL_MAX_IDS } from '@/lib/constants'

const mockGet = vi.hoisted(() => vi.fn())
const mockPost = vi.hoisted(() => vi.fn())

vi.mock('@/lib/api', () => ({
  ApiClient: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
  },
}))

import { plannerApi } from '../plannerApi'
import type { ServerPlannerResponse } from '../../types/PlannerTypes'

/**
 * A response row that satisfies ServerPlannerResponseSchema.
 */
function responseFor(id: string) {
  return {
    id,
    title: 'Test Planner',
    category: '5F',
    status: 'saved',
    content: '{}',
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

function idsIn(url: string): string[] {
  const query = new URL(url, 'http://localhost').searchParams.get('ids')
  return query ? query.split(',') : []
}

async function collect(ids: string[]): Promise<ServerPlannerResponse[]> {
  const out: ServerPlannerResponse[] = []
  for await (const chunk of plannerApi.batchChunks(ids)) out.push(...chunk)
  return out
}

describe('plannerApi.batchChunks', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockPost.mockReset()
    mockGet.mockImplementation(async (url: string) => idsIn(url).map(responseFor))
  })

  it('pulls a few ids with one bodyless GET carrying them comma-joined in the query', async () => {
    const ids = [uuidAt(0), uuidAt(1), uuidAt(2)]

    const result = await collect(ids)

    expect(mockGet).toHaveBeenCalledTimes(1)
    expect(mockGet.mock.calls[0]).toEqual([`/api/planner/md/batch?ids=${ids.join(',')}`])
    expect(mockPost).not.toHaveBeenCalled()
    expect(result.map((planner) => planner.id)).toEqual(ids)
  })

  it('sends a residue at the id cap as a single request', async () => {
    const result = await collect(idsOf(BATCH_PULL_MAX_IDS))

    expect(mockGet).toHaveBeenCalledTimes(1)
    expect(result).toHaveLength(BATCH_PULL_MAX_IDS)
  })

  it('chunks ids over the cap into ceil(n / cap) requests, in order', async () => {
    const ids = idsOf(2 * BATCH_PULL_MAX_IDS + 1)

    const result = await collect(ids)

    expect(mockGet).toHaveBeenCalledTimes(3)
    const chunks = mockGet.mock.calls.map((call) => idsIn(call[0] as string))
    expect(chunks.map((chunk) => chunk.length)).toEqual([BATCH_PULL_MAX_IDS, BATCH_PULL_MAX_IDS, 1])
    expect(chunks.flat()).toEqual(ids)
    expect(result.map((planner) => planner.id)).toEqual(ids)
  })

  it('percent-encodes an id carrying query metacharacters', async () => {
    mockGet.mockResolvedValue([])

    await collect(['a&b=c,d'])

    expect(mockGet.mock.calls[0]).toEqual(['/api/planner/md/batch?ids=a%26b%3Dc%2Cd'])
  })

  it('issues no request for an empty id list', async () => {
    const result = await collect([])

    expect(mockGet).not.toHaveBeenCalled()
    expect(result).toEqual([])
  })

  it('accepts a response that omits ids, which is not positionally aligned', async () => {
    mockGet.mockImplementation(async (url: string) =>
      idsIn(url)
        .filter((id) => id !== uuidAt(1))
        .map(responseFor),
    )

    const result = await collect([uuidAt(0), uuidAt(1), uuidAt(2)])

    expect(result.map((planner) => planner.id)).toEqual([uuidAt(0), uuidAt(2)])
  })

  it('yields earlier chunks before a later one fails', async () => {
    mockGet.mockImplementationOnce(async (url: string) => idsIn(url).map(responseFor))
    mockGet.mockImplementationOnce(async () => {
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
    mockGet.mockImplementation(async (url: string) =>
      // The second row of the chunk carries an out-of-range syncVersion.
      idsIn(url).map((id, index) =>
        index === 1 ? { ...responseFor(id), syncVersion: -3 } : responseFor(id),
      ),
    )

    await expect(collect([uuidAt(0), uuidAt(1)])).rejects.toThrow(
      'Too small: expected number to be >0',
    )
  })

  it('fails a chunk whose row still carries upvotes', async () => {
    mockGet.mockImplementation(async (url: string) =>
      idsIn(url).map((id) => Object.assign(responseFor(id), { upvotes: 0 })),
    )

    await expect(collect([uuidAt(0)])).rejects.toThrow('upvotes')
  })
})
