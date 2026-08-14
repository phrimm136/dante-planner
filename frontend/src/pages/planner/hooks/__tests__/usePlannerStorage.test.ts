/**
 * Two properties of the storage hook that a `null` return used to hide.
 *
 * A device id is minted only when the store answers that it holds none; a read
 * that could not be performed must leave the id alone, because minting over a
 * broken read orphans every row written under the previous one. And the row
 * listing now selects on a two-part key, so the one-part `deviceId` singleton
 * has to fall out of it rather than be filtered by name.
 */

import { describe, it, expect, beforeEach, vi } from 'vitest'
import { renderHook } from '@testing-library/react'

import { ok, err } from '@/lib/result'
import { buildSaveablePlanner } from '@/test-utils'

const mockGetItem = vi.fn()
const mockSetItem = vi.fn()
const mockRemoveItem = vi.fn()
const mockOpenStorageDb = vi.fn()

vi.mock('@/lib/storage', () => ({
  STORAGE_STORE_NAME: 'planner',
  storage: {
    getItem: (key: string) => mockGetItem(key),
    setItem: (key: string, value: string) => mockSetItem(key, value),
    removeItem: (key: string) => mockRemoveItem(key),
    clear: vi.fn(),
  },
  openStorageDb: () => mockOpenStorageDb(),
}))

const { usePlannerStorage } = await import('../usePlannerStorage')

/** A database whose cursor walks `rows` in insertion order. */
function stubDbOver(rows: Map<string, string>) {
  return {
    transaction: () => ({
      objectStore: () => ({
        openCursor: () => {
          const entries = [...rows.entries()]
          let index = 0
          const req: {
            onsuccess: ((event: unknown) => void) | null
            onerror: ((event: unknown) => void) | null
            result: unknown
          } = { onsuccess: null, onerror: null, result: null }
          const step = () => {
            queueMicrotask(() => {
              if (index < entries.length) {
                const [key, value] = entries[index++]
                req.result = { key, value, continue: step }
              } else {
                req.result = null
              }
              req.onsuccess?.({ target: req })
            })
          }
          step()
          return req
        },
      }),
    }),
  }
}

function storageHook() {
  return renderHook(() => usePlannerStorage()).result.current
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('getOrCreateDeviceId', () => {
  it('returns the failure and writes nothing when the read could not be performed', async () => {
    mockGetItem.mockResolvedValue(err({ kind: 'ioError', cause: new Error('disk gone') }))

    const outcome = await storageHook().getOrCreateDeviceId()

    expect(outcome.ok).toBe(false)
    if (outcome.ok) throw new Error('expected a failed read')
    expect(outcome.error.kind).toBe('ioError')
    expect(mockSetItem).not.toHaveBeenCalled()
  })

  it('mints and persists an id when the store answers that it holds none', async () => {
    mockGetItem.mockResolvedValue(ok(null))
    mockSetItem.mockResolvedValue(ok(undefined))

    const outcome = await storageHook().getOrCreateDeviceId()

    expect(outcome.ok).toBe(true)
    if (!outcome.ok) throw new Error('expected a successful read')
    expect(outcome.value).not.toBe('')
    expect(mockSetItem).toHaveBeenCalledWith('deviceId', outcome.value)
  })

  it('returns the stored id without writing when one is already held', async () => {
    mockGetItem.mockResolvedValue(ok('an-existing-device-id'))

    const outcome = await storageHook().getOrCreateDeviceId()

    expect(outcome).toEqual({ ok: true, value: 'an-existing-device-id' })
    expect(mockSetItem).not.toHaveBeenCalled()
  })
})

describe('saveToLocal', () => {
  it('reports the failure when the underlying write could not be performed', async () => {
    mockSetItem.mockResolvedValue(err({ kind: 'ioError', cause: new Error('quota exceeded') }))

    const outcome = await storageHook().saveToLocal(buildSaveablePlanner())

    expect(outcome.ok).toBe(false)
  })

  it('reports success only when the underlying write committed', async () => {
    mockSetItem.mockResolvedValue(ok(undefined))

    const outcome = await storageHook().saveToLocal(buildSaveablePlanner())

    expect(outcome.ok).toBe(true)
  })
})

describe('listLocal', () => {
  it('includes rows under two-part keys and ignores the deviceId singleton', async () => {
    const planner = buildSaveablePlanner()
    const rows = new Map<string, string>([
      ['deviceId', 'a-device-uuid'],
      [`planner:${planner.metadata.id}`, JSON.stringify(planner)],
    ])
    mockOpenStorageDb.mockResolvedValue(stubDbOver(rows))

    const summaries = await storageHook().listLocal()

    expect(summaries).toHaveLength(1)
    expect(summaries[0].id).toBe(planner.metadata.id)
  })

  it('ignores a row still held under an old four-part key', async () => {
    const planner = buildSaveablePlanner()
    const rows = new Map<string, string>([
      [`planner:md:devA:${planner.metadata.id}`, JSON.stringify(planner)],
    ])
    mockOpenStorageDb.mockResolvedValue(stubDbOver(rows))

    const summaries = await storageHook().listLocal()

    expect(summaries).toEqual([])
  })

  it('sorts the rows it returns by lastModifiedAt, newest first', async () => {
    const older = buildSaveablePlanner({
      metadata: {
        id: '00000000-0000-4000-8000-00000000000a',
        lastModifiedAt: '2026-01-01T00:00:00.000Z',
      },
    })
    const newer = buildSaveablePlanner({
      metadata: {
        id: '00000000-0000-4000-8000-00000000000b',
        lastModifiedAt: '2026-06-01T00:00:00.000Z',
      },
    })
    const rows = new Map<string, string>([
      [`planner:${older.metadata.id}`, JSON.stringify(older)],
      [`planner:${newer.metadata.id}`, JSON.stringify(newer)],
    ])
    mockOpenStorageDb.mockResolvedValue(stubDbOver(rows))

    const summaries = await storageHook().listLocal()

    expect(summaries.map((s) => s.id)).toEqual([newer.metadata.id, older.metadata.id])
  })
})
