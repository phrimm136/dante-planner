/**
 * `getItem` has to tell absence apart from breakage, and a broken open must not
 * poison the reads that follow it. Both are properties of the shared connection
 * promise, so every case here drives the real module through a stub
 * `indexedDB` and re-imports it to get a fresh connection cache.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'

const STORE_NAME = 'planner'

type Handler = ((event: unknown) => void) | null

interface StubRequest<T> {
  onsuccess: Handler
  onerror: Handler
  onblocked: Handler
  result: T
  error: unknown
}

function makeRequest<T>(): StubRequest<T> {
  return {
    onsuccess: null,
    onerror: null,
    onblocked: null,
    result: undefined as T,
    error: null,
  }
}

/** How a write transaction ends: committed, or aborted the way a quota failure ends one. */
type WriteOutcome = 'commit' | 'abort'

interface StubTransaction {
  onabort: Handler
  oncomplete: Handler
  onerror: Handler
  error: unknown
  objectStore: () => unknown
}

/** A database whose object store answers `get` from a plain map and writes into it. */
function stubDb(rows: Map<string, string>, writeOutcome: WriteOutcome = 'commit') {
  return {
    onversionchange: null as Handler,
    close: vi.fn(),
    transaction: () => {
      const transaction: StubTransaction = {
        onabort: null,
        oncomplete: null,
        onerror: null,
        error: null,
        objectStore: () => store,
      }

      /** A write settles the request and then the transaction, as IndexedDB does. */
      const write = (apply: () => void) => {
        const request = makeRequest<undefined>()
        queueMicrotask(() => {
          if (writeOutcome === 'abort') {
            const error = { name: 'QuotaExceededError', message: 'quota exceeded' }
            request.error = error
            transaction.error = error
            request.onerror?.({ target: request })
            transaction.onabort?.({ target: transaction })
            return
          }
          apply()
          request.onsuccess?.({ target: request })
          transaction.oncomplete?.({ target: transaction })
        })
        return request
      }

      const store = {
        get: (key: string) => {
          const request = makeRequest<string | undefined>()
          queueMicrotask(() => {
            request.result = rows.get(key)
            request.onsuccess?.({ target: request })
          })
          return request
        },
        put: (value: string, key: string) => write(() => rows.set(key, value)),
        delete: (key: string) => write(() => rows.delete(key)),
      }

      return transaction
    },
  }
}

type OpenOutcome =
  | { kind: 'success'; rows: Map<string, string>; writeOutcome?: WriteOutcome }
  | { kind: 'error'; error: unknown }
  | { kind: 'blocked' }

/**
 * Installs a stub `indexedDB` that plays `outcomes` in order, one per `open`
 * call, recording every open request and every database it handed out.
 */
function installIndexedDB(outcomes: OpenOutcome[]) {
  const opens: StubRequest<ReturnType<typeof stubDb>>[] = []
  const dbs: ReturnType<typeof stubDb>[] = []

  const indexedDB = {
    open: () => {
      const outcome = outcomes[opens.length] ?? outcomes[outcomes.length - 1]
      const request = makeRequest<ReturnType<typeof stubDb>>()
      opens.push(request)

      queueMicrotask(() => {
        if (outcome.kind === 'error') {
          request.error = outcome.error
          request.onerror?.({ target: request })
          return
        }
        if (outcome.kind === 'blocked') {
          request.onblocked?.({ target: request })
          return
        }
        const db = stubDb(outcome.rows, outcome.writeOutcome)
        dbs.push(db)
        request.result = db
        request.onsuccess?.({ target: request })
      })

      return request
    },
  }

  vi.stubGlobal('indexedDB', indexedDB)
  return { opens, dbs }
}

async function importStorage() {
  vi.resetModules()
  return import('../storage')
}

beforeEach(() => {
  vi.resetModules()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('storage.getItem', () => {
  it('answers ok(null) for a key the store does not hold', async () => {
    installIndexedDB([{ kind: 'success', rows: new Map() }])
    const { storage } = await importStorage()

    const result = await storage.getItem('absent')

    expect(result).toEqual({ ok: true, value: null })
  })

  it('answers ok with the stored string for a key the store holds', async () => {
    installIndexedDB([{ kind: 'success', rows: new Map([['present', 'value']]) }])
    const { storage } = await importStorage()

    const result = await storage.getItem('present')

    expect(result).toEqual({ ok: true, value: 'value' })
  })

  it('reports ioError rather than null when the open rejects', async () => {
    installIndexedDB([{ kind: 'error', error: new Error('open failed') }])
    const { storage } = await importStorage()

    const result = await storage.getItem('anything')

    expect(result.ok).toBe(false)
    if (result.ok) throw new Error('expected a failed read')
    expect(result.error.kind).toBe('ioError')
  })

  it('reopens on the next call because a failed open clears the shared promise', async () => {
    const { opens } = installIndexedDB([
      { kind: 'error', error: new Error('open failed') },
      { kind: 'success', rows: new Map([['k', 'v']]) },
    ])
    const { storage } = await importStorage()

    const first = await storage.getItem('k')
    const second = await storage.getItem('k')

    expect(first.ok).toBe(false)
    expect(second).toEqual({ ok: true, value: 'v' })
    expect(opens).toHaveLength(2)
  })

  it('reuses the shared promise across reads once an open has succeeded', async () => {
    const { opens } = installIndexedDB([{ kind: 'success', rows: new Map([['k', 'v']]) }])
    const { storage } = await importStorage()

    await storage.getItem('k')
    await storage.getItem('k')

    expect(opens).toHaveLength(1)
  })

  it('drops the shared promise when another tab requests an upgrade', async () => {
    const { opens } = installIndexedDB([
      { kind: 'success', rows: new Map([['k', 'v']]) },
      { kind: 'success', rows: new Map([['k', 'v2']]) },
    ])
    const { storage, openStorageDb } = await importStorage()

    await storage.getItem('k')
    const db = await openStorageDb()
    // The browser fires this when another connection asks for a new version.
    ;(db as unknown as { onversionchange: () => void }).onversionchange()

    const after = await storage.getItem('k')

    expect(after).toEqual({ ok: true, value: 'v2' })
    expect(opens).toHaveLength(2)
  })
})

describe('storage connection lifecycle', () => {
  it('surfaces a blocked upgrade as ioError and reopens on the next call', async () => {
    const { opens } = installIndexedDB([
      { kind: 'blocked' },
      { kind: 'success', rows: new Map([['k', 'v']]) },
    ])
    const { storage } = await importStorage()

    const blocked = await storage.getItem('k')
    const after = await storage.getItem('k')

    expect(blocked.ok).toBe(false)
    if (blocked.ok) throw new Error('expected a failed read')
    expect(blocked.error.kind).toBe('ioError')
    expect(after).toEqual({ ok: true, value: 'v' })
    expect(opens).toHaveLength(2)
  })

  it('closes the connection when another tab requests an upgrade', async () => {
    const { dbs } = installIndexedDB([{ kind: 'success', rows: new Map([['k', 'v']]) }])
    const { storage, openStorageDb } = await importStorage()

    await storage.getItem('k')
    const db = await openStorageDb()
    ;(db as unknown as { onversionchange: () => void }).onversionchange()

    expect(dbs[0].close).toHaveBeenCalledTimes(1)
  })

  it('closes a superseded connection instead of leaking it', async () => {
    // The first open is still in flight when a blocked second open clears the
    // shared promise, so the connection it eventually hands over is orphaned.
    const { dbs } = installIndexedDB([
      { kind: 'success', rows: new Map([['k', 'v']]) },
      { kind: 'success', rows: new Map([['k', 'v']]) },
    ])
    const { storage } = await importStorage()

    await storage.getItem('k')
    const first = dbs[0]
    ;(first as unknown as { onversionchange: () => void }).onversionchange()
    await storage.getItem('k')

    expect(dbs).toHaveLength(2)
    expect(first.close).toHaveBeenCalled()
  })
})

describe('storage writes', () => {
  it('reports ok once the write transaction commits', async () => {
    const rows = new Map<string, string>()
    installIndexedDB([{ kind: 'success', rows }])
    const { storage } = await importStorage()

    await expect(storage.setItem('k', 'v')).resolves.toEqual({ ok: true, value: undefined })
    expect(rows.get('k')).toBe('v')
  })

  it('reports ioError when the write transaction aborts, rather than claiming success', async () => {
    const rows = new Map<string, string>()
    installIndexedDB([{ kind: 'success', rows, writeOutcome: 'abort' }])
    const { storage } = await importStorage()

    const result = await storage.setItem('k', 'v')

    expect(result.ok).toBe(false)
    if (result.ok) throw new Error('expected a failed write')
    expect(result.error.kind).toBe('ioError')
    expect(rows.has('k')).toBe(false)
  })

  it('reports ioError when a delete transaction aborts', async () => {
    const rows = new Map<string, string>([['k', 'v']])
    installIndexedDB([{ kind: 'success', rows, writeOutcome: 'abort' }])
    const { storage } = await importStorage()

    const result = await storage.removeItem('k')

    expect(result.ok).toBe(false)
    expect(rows.get('k')).toBe('v')
  })

  it('reports ioError on a write when the database cannot be opened', async () => {
    installIndexedDB([{ kind: 'error', error: new Error('open failed') }])
    const { storage } = await importStorage()

    const result = await storage.setItem('k', 'v')

    expect(result.ok).toBe(false)
    if (result.ok) throw new Error('expected a failed write')
    expect(result.error.kind).toBe('ioError')
  })
})

describe('storage store name', () => {
  it('is the name every caller opens its transaction against', async () => {
    const { STORAGE_STORE_NAME } = await importStorage()
    expect(STORAGE_STORE_NAME).toBe(STORE_NAME)
  })
})
