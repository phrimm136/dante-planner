/**
 * The v1 to v2 key migration, driven over a stub object store.
 *
 * What matters is which rows survive and in what order they are written and
 * dropped: a source may only go once its copy reads back, and two devices
 * holding one planner id must collapse to the row modified last.
 */

import { describe, it, expect } from 'vitest'

import { migrateToFlatKeys } from '../storage'

interface StubRequest<T> {
  onsuccess: ((event: unknown) => void) | null
  onerror: ((event: unknown) => void) | null
  result: T
}

/** An in-memory object store recording the order of writes and deletes. */
function stubStore(initial: Iterable<readonly [string, string]>) {
  const rows = new Map(initial)
  const written: string[] = []
  const deleted: string[] = []

  function request<T>(produce: () => T): StubRequest<T> {
    const req: StubRequest<T> = { onsuccess: null, onerror: null, result: undefined as T }
    queueMicrotask(() => {
      req.result = produce()
      req.onsuccess?.({ target: req })
    })
    return req
  }

  const store = {
    openCursor: () => {
      const entries = [...rows.entries()]
      let index = 0
      const req: StubRequest<unknown> = { onsuccess: null, onerror: null, result: null }
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
    put: (value: string, key: string) =>
      request(() => {
        rows.set(key, value)
        written.push(key)
      }),
    get: (key: string) => request(() => rows.get(key)),
    delete: (key: string) =>
      request(() => {
        rows.delete(key)
        deleted.push(key)
      }),
  }

  const transaction = { objectStore: () => store } as unknown as IDBTransaction

  return { transaction, rows, written, deleted }
}

/** Drain the microtask queue the stub requests settle on. */
async function flush() {
  for (let i = 0; i < 200; i++) await Promise.resolve()
}

const plannerRow = (id: string, lastModifiedAt: string) =>
  JSON.stringify({ metadata: { id, title: `t-${lastModifiedAt}`, lastModifiedAt } })

describe('migrateToFlatKeys', () => {
  it('rewrites a device-scoped planner key as a flat one', async () => {
    const row = plannerRow('p1', '2026-01-01T00:00:00.000Z')
    const { transaction, rows } = stubStore([['planner:md:devA:p1', row]])

    migrateToFlatKeys(transaction)
    await flush()

    expect(rows.get('planner:p1')).toBe(row)
    expect(rows.has('planner:md:devA:p1')).toBe(false)
  })

  it('keeps the newest row when two devices hold the same planner id', async () => {
    const older = plannerRow('p1', '2026-01-01T00:00:00.000Z')
    const newer = plannerRow('p1', '2026-06-01T00:00:00.000Z')
    const { transaction, rows } = stubStore([
      ['planner:md:devA:p1', newer],
      ['planner:md:devB:p1', older],
    ])

    migrateToFlatKeys(transaction)
    await flush()

    expect(rows.get('planner:p1')).toBe(newer)
    expect(rows.has('planner:md:devA:p1')).toBe(false)
    expect(rows.has('planner:md:devB:p1')).toBe(false)
  })

  it('keeps the newest row whichever order the cursor reaches the devices in', async () => {
    const older = plannerRow('p1', '2026-01-01T00:00:00.000Z')
    const newer = plannerRow('p1', '2026-06-01T00:00:00.000Z')
    const { transaction, rows } = stubStore([
      ['planner:md:devA:p1', older],
      ['planner:md:devB:p1', newer],
    ])

    migrateToFlatKeys(transaction)
    await flush()

    expect(rows.get('planner:p1')).toBe(newer)
  })

  it('leaves the deviceId singleton untouched', async () => {
    const { transaction, rows, written, deleted } = stubStore([
      ['deviceId', 'a-device-uuid'],
      ['planner:md:devA:p1', plannerRow('p1', '2026-01-01T00:00:00.000Z')],
    ])

    migrateToFlatKeys(transaction)
    await flush()

    expect(rows.get('deviceId')).toBe('a-device-uuid')
    expect(written).not.toContain('deviceId')
    expect(deleted).not.toContain('deviceId')
  })

  it('writes the copy before dropping the source', async () => {
    const { transaction, written, deleted } = stubStore([
      ['planner:md:devA:p1', plannerRow('p1', '2026-01-01T00:00:00.000Z')],
    ])

    migrateToFlatKeys(transaction)
    await flush()

    expect(written).toEqual(['planner:p1'])
    expect(deleted).toEqual(['planner:md:devA:p1'])
  })

  it('migrates each planner id independently', async () => {
    const { transaction, rows } = stubStore([
      ['planner:md:devA:p1', plannerRow('p1', '2026-01-01T00:00:00.000Z')],
      ['planner:md:devA:p2', plannerRow('p2', '2026-02-01T00:00:00.000Z')],
    ])

    migrateToFlatKeys(transaction)
    await flush()

    expect(rows.has('planner:p1')).toBe(true)
    expect(rows.has('planner:p2')).toBe(true)
    expect(rows.size).toBe(2)
  })

  it('ignores a row whose key is not a device-scoped planner key', async () => {
    const { transaction, rows, written, deleted } = stubStore([['some:other:key:shape', 'x']])

    migrateToFlatKeys(transaction)
    await flush()

    expect(rows.get('some:other:key:shape')).toBe('x')
    expect(written).toEqual([])
    expect(deleted).toEqual([])
  })

  it('still migrates a row carrying no readable lastModifiedAt', async () => {
    const { transaction, rows } = stubStore([['planner:md:devA:p1', 'not json']])

    migrateToFlatKeys(transaction)
    await flush()

    expect(rows.get('planner:p1')).toBe('not json')
  })

  it('lets a row with a timestamp beat one without', async () => {
    const dated = plannerRow('p1', '2026-01-01T00:00:00.000Z')
    const { transaction, rows } = stubStore([
      ['planner:md:devA:p1', 'not json'],
      ['planner:md:devB:p1', dated],
    ])

    migrateToFlatKeys(transaction)
    await flush()

    expect(rows.get('planner:p1')).toBe(dated)
  })
})
