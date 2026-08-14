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
  result: T
  error: unknown
}

function makeRequest<T>(): StubRequest<T> {
  return { onsuccess: null, onerror: null, result: undefined as T, error: null }
}

/** A database whose object store answers `get` from a plain map. */
function stubDb(rows: Map<string, string>) {
  return {
    onversionchange: null as Handler,
    close: vi.fn(),
    objectStoreNames: { contains: () => true },
    createObjectStore: vi.fn(),
    transaction: () => ({
      objectStore: () => ({
        get: (key: string) => {
          const request = makeRequest<string | undefined>()
          queueMicrotask(() => {
            request.result = rows.get(key)
            request.onsuccess?.({ target: request })
          })
          return request
        },
      }),
    }),
  }
}

type OpenOutcome =
  | { kind: 'success'; rows: Map<string, string> }
  | { kind: 'error'; error: unknown }

/**
 * Installs a stub `indexedDB` that plays `outcomes` in order, one per `open`
 * call, and records how many opens were attempted.
 */
function installIndexedDB(outcomes: OpenOutcome[]) {
  const opens: unknown[] = []

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
        request.result = stubDb(outcome.rows)
        request.onsuccess?.({ target: request })
      })

      return request
    },
  }

  vi.stubGlobal('indexedDB', indexedDB)
  return { opens }
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

describe('storage store name', () => {
  it('is the name every caller opens its transaction against', async () => {
    const { STORAGE_STORE_NAME } = await importStorage()
    expect(STORAGE_STORE_NAME).toBe(STORE_NAME)
  })
})
