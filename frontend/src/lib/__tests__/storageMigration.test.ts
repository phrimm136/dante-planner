/**
 * The v1 to v2 key migration, at two levels.
 *
 * The first suite drives `migrateToFlatKeys` over a stub object store, where
 * what matters is which rows survive and in what order they are written and
 * dropped: a source may only go once its copy reads back, and two devices
 * holding one planner id must collapse to the row modified last.
 *
 * The second runs the whole upgrade through a real IndexedDB implementation,
 * which is the only place the `versionchange` transaction, the version gate,
 * and the store's own durability actually exist.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { IDBFactory, IDBKeyRange } from 'fake-indexeddb'

import { migrateToFlatKeys, STORAGE_STORE_NAME } from '../storage'

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
          const entry = entries[index++]
          if (entry) {
            const [key, value] = entry
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

  const listeners = new Map<string, (() => void)[]>()
  const transaction = {
    objectStore: () => store,
    error: null,
    addEventListener: (type: string, handler: () => void) => {
      listeners.set(type, [...(listeners.get(type) ?? []), handler])
    },
  } as unknown as IDBTransaction

  /** Fire a transaction lifecycle event the way the real store would. */
  const emit = (type: string) => {
    for (const handler of listeners.get(type) ?? []) handler()
  }

  return { transaction, rows, written, deleted, emit }
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

const DB_NAME = 'danteplanner'

/** Open the database directly, outside the module under test. */
function openRaw(
  factory: IDBFactory,
  version?: number,
  upgrade?: (db: IDBDatabase) => void,
): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = version === undefined ? factory.open(DB_NAME) : factory.open(DB_NAME, version)
    request.onupgradeneeded = () => upgrade?.(request.result)
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

/** Create the database at v1 holding `rows`, then close the seeding connection. */
async function seedV1(factory: IDBFactory, rows: Record<string, string>): Promise<void> {
  const db = await openRaw(factory, 1, (created) => created.createObjectStore(STORAGE_STORE_NAME))
  await new Promise<void>((resolve, reject) => {
    const transaction = db.transaction(STORAGE_STORE_NAME, 'readwrite')
    const store = transaction.objectStore(STORAGE_STORE_NAME)
    for (const [key, value] of Object.entries(rows)) store.put(value, key)
    transaction.oncomplete = () => resolve()
    transaction.onerror = () => reject(transaction.error)
  })
  db.close()
}

/** Every row the database currently holds, read on its own connection. */
async function readAll(factory: IDBFactory): Promise<Map<string, string>> {
  const db = await openRaw(factory)
  const rows = new Map<string, string>()
  await new Promise<void>((resolve, reject) => {
    const request = db
      .transaction(STORAGE_STORE_NAME, 'readonly')
      .objectStore(STORAGE_STORE_NAME)
      .openCursor()
    request.onsuccess = () => {
      const cursor = request.result
      if (!cursor) return resolve()
      const key: IDBValidKey = cursor.key
      const value: unknown = cursor.value
      if (typeof key === 'string' && typeof value === 'string') rows.set(key, value)
      cursor.continue()
    }
    request.onerror = () => reject(request.error)
  })
  db.close()
  return rows
}

/** Write one row on its own connection, outside the module under test. */
async function writeRow(factory: IDBFactory, key: string, value: string): Promise<void> {
  const db = await openRaw(factory)
  await new Promise<void>((resolve, reject) => {
    const transaction = db.transaction(STORAGE_STORE_NAME, 'readwrite')
    transaction.objectStore(STORAGE_STORE_NAME).put(value, key)
    transaction.oncomplete = () => resolve()
    transaction.onabort = () => reject(transaction.error)
  })
  db.close()
}

/**
 * Run the migration over the current database on its own transaction.
 *
 * The version gate would never call it again once the store is at v2, so this
 * is the only way to see what it would do to rows written since the upgrade.
 */
async function runMigrationDirectly(factory: IDBFactory): Promise<void> {
  const db = await openRaw(factory)
  await new Promise<void>((resolve, reject) => {
    const transaction = db.transaction(STORAGE_STORE_NAME, 'readwrite')
    migrateToFlatKeys(transaction)
    transaction.oncomplete = () => resolve()
    transaction.onabort = () => reject(transaction.error)
  })
  db.close()
}

/** The database version currently on disk, read on its own connection. */
async function currentVersion(factory: IDBFactory): Promise<number> {
  const db = await openRaw(factory)
  const { version } = db
  db.close()
  return version
}

/** Import the module fresh so it opens a new connection of its own. */
async function openThroughStorage(): Promise<void> {
  vi.resetModules()
  const { storage } = await import('../storage')
  // Any read forces the shared connection open, which runs the upgrade.
  await storage.getItem('deviceId')
}

describe('the v1 to v2 upgrade over a real IndexedDB', () => {
  let factory: IDBFactory

  beforeEach(() => {
    factory = new IDBFactory()
    vi.stubGlobal('indexedDB', factory)
    vi.stubGlobal('IDBKeyRange', IDBKeyRange)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.resetModules()
  })

  it('collapses two devices onto one planner key and leaves the deviceId singleton alone', async () => {
    const older = plannerRow('p1', '2026-01-01T00:00:00.000Z')
    const newer = plannerRow('p1', '2026-06-01T00:00:00.000Z')
    await seedV1(factory, {
      deviceId: 'a-device-uuid',
      'planner:md:devA:p1': older,
      'planner:md:devB:p1': newer,
    })

    await openThroughStorage()

    const rows = await readAll(factory)
    expect(rows.get('planner:p1')).toBe(newer)
    expect(rows.get('deviceId')).toBe('a-device-uuid')
    expect(rows.has('planner:md:devA:p1')).toBe(false)
    expect(rows.has('planner:md:devB:p1')).toBe(false)
    expect(await currentVersion(factory)).toBe(2)
  })

  it('migrates every planner id the database held', async () => {
    await seedV1(factory, {
      'planner:md:devA:p1': plannerRow('p1', '2026-01-01T00:00:00.000Z'),
      'planner:md:devA:p2': plannerRow('p2', '2026-02-01T00:00:00.000Z'),
    })

    await openThroughStorage()

    const rows = await readAll(factory)
    expect([...rows.keys()].sort()).toEqual(['planner:p1', 'planner:p2'])
  })

  it('does not run the migration again on a second open at v2', async () => {
    await seedV1(factory, {
      deviceId: 'a-device-uuid',
      'planner:md:devA:p1': plannerRow('p1', '2026-01-01T00:00:00.000Z'),
    })

    await openThroughStorage()
    const afterUpgrade = await readAll(factory)

    // A row written after the upgrade would be destroyed by a migration that
    // ran a second time, so its survival is what makes the no-op observable.
    await writeRow(factory, 'planner:p1', plannerRow('p1', '2026-09-01T00:00:00.000Z'))
    await openThroughStorage()
    const afterSecondOpen = await readAll(factory)

    expect(afterUpgrade.get('planner:p1')).toBe(plannerRow('p1', '2026-01-01T00:00:00.000Z'))
    expect(afterSecondOpen.get('planner:p1')).toBe(plannerRow('p1', '2026-09-01T00:00:00.000Z'))
    expect(afterSecondOpen.get('deviceId')).toBe('a-device-uuid')
    expect(await currentVersion(factory)).toBe(2)
  })

  it('leaves a current flat row alone when a stale source key is still present', async () => {
    const current = plannerRow('p1', '2026-09-01T00:00:00.000Z')
    const stale = plannerRow('p1', '2026-01-01T00:00:00.000Z')
    await seedV1(factory, { 'planner:md:devA:p1': stale })
    await openThroughStorage()
    await writeRow(factory, 'planner:p1', current)
    await writeRow(factory, 'planner:md:devA:p1', stale)

    // The database is already at v2, so drive the migration directly to prove
    // what it would do to a flat row that a later save has moved on.
    await runMigrationDirectly(factory)

    const rows = await readAll(factory)
    expect(rows.get('planner:p1')).toBe(current)
    expect(rows.has('planner:md:devA:p1')).toBe(false)
  })

  it('keeps both rows when a stale source ties the flat row but differs', async () => {
    const flat = plannerRow('p1', '2026-01-01T00:00:00.000Z')
    const other = JSON.stringify({
      metadata: { id: 'p1', title: 'divergent', lastModifiedAt: '2026-01-01T00:00:00.000Z' },
    })
    await seedV1(factory, { 'planner:md:devA:p1': flat })
    await openThroughStorage()
    await writeRow(factory, 'planner:md:devA:p1', other)

    await runMigrationDirectly(factory)

    const rows = await readAll(factory)
    expect(rows.get('planner:p1')).toBe(flat)
    expect(rows.get('planner:md:devA:p1')).toBe(other)
  })

  it('creates an empty store when no database existed, with nothing to migrate', async () => {
    await openThroughStorage()

    expect(await readAll(factory)).toEqual(new Map())
    expect(await currentVersion(factory)).toBe(2)
  })

  it('serves a migrated row back through the reader under its new key', async () => {
    const row = plannerRow('p1', '2026-01-01T00:00:00.000Z')
    await seedV1(factory, { 'planner:md:devA:p1': row })

    vi.resetModules()
    const { storage } = await import('../storage')

    await expect(storage.getItem('planner:p1')).resolves.toEqual({ ok: true, value: row })
    await expect(storage.getItem('planner:md:devA:p1')).resolves.toEqual({ ok: true, value: null })
  })
})
