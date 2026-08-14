/**
 * SSR-safe storage utility using IndexedDB
 *
 * Provides persistent storage with SSR compatibility.
 * Uses IndexedDB for better security and larger storage capacity.
 *
 * Used for guest's planner data.
 *
 * @example
 * import { storage } from '@/lib/storage'
 *
 * await storage.setItem('plannerData', '...')
 * const data = await storage.getItem('plannerData')
 */

import { ok, err } from './result'
import { PLANNER_STORAGE_KEYS } from './constants'
import type { Result, Tagged } from './result'

const DB_NAME = 'danteplanner'
/** Object store holding every persisted row; exported for direct cursor access. */
export const STORAGE_STORE_NAME = 'planner'
const DB_VERSION = 2

/**
 * The v2 key a v1 planner key maps to, or null for a key that is not one.
 *
 * The one-part `deviceId` singleton falls out here under both this parser and
 * the read-time one, so it survives the migration untouched.
 */
function flatKeyFor(key: string): string | null {
  const parts = key.split(':')
  if (parts.length !== 4) return null
  if (parts[0] !== PLANNER_STORAGE_KEYS.PLANNER || parts[1] !== PLANNER_STORAGE_KEYS.MD) return null
  return `${PLANNER_STORAGE_KEYS.PLANNER}:${parts[3]}`
}

/** Epoch millis of a row's `metadata.lastModifiedAt`; 0 when it carries none readable. */
function lastModifiedAtOf(value: string): number {
  try {
    const millis = Date.parse(JSON.parse(value)?.metadata?.lastModifiedAt)
    return Number.isNaN(millis) ? 0 : millis
  } catch {
    return 0
  }
}

/** One v1 row considered for migration. */
interface MigrationRow {
  key: string
  value: string
  modifiedAt: number
}

/**
 * Rewrite `planner:md:{deviceId}:{plannerId}` rows as `planner:{plannerId}`.
 *
 * Two devices can hold the same planner id, so a collision keeps whichever row
 * was modified last. A source row is dropped only when nothing it holds can be
 * lost: either the flat row now carries its exact bytes, or a strictly newer
 * row won. An exact tie between rows that differ keeps the loser, because this
 * runs once and an arbitrary destructive pick could not be undone.
 *
 * Runs inside the `versionchange` transaction: every request below is queued on
 * it, so the upgrade cannot complete until the last delete has run. Every
 * failure is logged with its cause — the migration has exactly one chance to
 * run, so a silent failure is one nobody could ever diagnose.
 */
export function migrateToFlatKeys(transaction: IDBTransaction): void {
  const store = transaction.objectStore(STORAGE_STORE_NAME)
  const groups = new Map<string, MigrationRow[]>()
  let unverified = 0
  let retained = 0

  const report = (what: string, cause: unknown) => {
    console.error(`IndexedDB migration: ${what}`, cause)
  }

  transaction.addEventListener('abort', () => {
    report('the upgrade transaction aborted, so no key was migrated', transaction.error)
  })
  transaction.addEventListener('complete', () => {
    if (unverified > 0 || retained > 0) {
      report(
        `finished with ${unverified} unverified copies and ${retained} source rows kept`,
        transaction.error,
      )
    }
  })

  const cursorRequest = store.openCursor()
  cursorRequest.onerror = () => report('reading the existing rows failed', cursorRequest.error)
  cursorRequest.onsuccess = () => {
    const cursor = cursorRequest.result
    if (cursor) {
      const key: IDBValidKey = cursor.key
      const value: unknown = cursor.value
      // Every row this store holds is a string under a string key; anything
      // else is not something the old key format could have produced.
      if (typeof key === 'string' && typeof value === 'string') {
        const flatKey = flatKeyFor(key)
        if (flatKey) {
          const row: MigrationRow = { key, value, modifiedAt: lastModifiedAtOf(value) }
          groups.set(flatKey, [...(groups.get(flatKey) ?? []), row])
        }
      }
      cursor.continue()
      return
    }

    for (const [flatKey, rows] of groups) {
      const incumbentRequest = store.get(flatKey)
      incumbentRequest.onerror = () =>
        report(`reading the existing ${flatKey} failed`, incumbentRequest.error)
      incumbentRequest.onsuccess = () => {
        const existing: unknown = incumbentRequest.result

        // A row already at the flat key is a candidate like any other, and it is
        // listed first so it wins a tie: whatever is already being read there
        // must never be replaced by a leftover that is no newer than it.
        const candidates: MigrationRow[] =
          typeof existing === 'string'
            ? [{ key: flatKey, value: existing, modifiedAt: lastModifiedAtOf(existing) }, ...rows]
            : rows

        const winner = candidates.reduce((best, row) =>
          row.modifiedAt > best.modifiedAt ? row : best,
        )

        /** Drop the source rows the winner makes safe to lose. */
        const dropSources = () => {
          for (const row of rows) {
            const identical = row.value === winner.value
            const superseded = winner.modifiedAt > row.modifiedAt
            if (!identical && !superseded) {
              retained += 1
              report(
                `${row.key} ties ${flatKey} on lastModifiedAt but differs in content, so it was kept`,
                row.modifiedAt,
              )
              continue
            }

            const removal = store.delete(row.key)
            removal.onerror = () => report(`deleting ${row.key} failed`, removal.error)
          }
        }

        if (winner.key === flatKey) {
          // The flat row already holds the winning content; there is nothing to
          // copy, only sources to retire.
          dropSources()
          return
        }

        const put = store.put(winner.value, flatKey)
        put.onerror = () => report(`writing ${flatKey} failed`, put.error)
        put.onsuccess = () => {
          const verify = store.get(flatKey)
          verify.onerror = () => report(`verifying ${flatKey} failed`, verify.error)
          verify.onsuccess = () => {
            if (verify.result !== winner.value) {
              unverified += 1
              report(
                `${flatKey} did not read back as written, so its sources were kept`,
                verify.error,
              )
              return
            }
            dropSources()
          }
        }
      }
    }
  }
}

/** Why a read could not be performed. Absence is not a failure — it is `ok(null)`. */
export type StorageReadError = Tagged<'notInBrowser'> | Tagged<'ioError', { cause: unknown }>

const isClient = typeof window !== 'undefined'

let dbPromise: Promise<IDBDatabase> | null = null

function getDB(): Promise<IDBDatabase> {
  if (!isClient) {
    return Promise.reject(new Error('IndexedDB not available on server'))
  }

  if (dbPromise) {
    return dbPromise
  }

  let pending: Promise<IDBDatabase> | null = null

  pending = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)

    /** Whether this request still owns the shared promise. */
    const owns = () => dbPromise === pending

    // Every failure path drops the shared promise, but only while this request
    // still owns it: a stale request's late event must not clear a newer one.
    request.onerror = () => {
      if (owns()) dbPromise = null
      reject(request.error)
    }
    request.onblocked = () => {
      if (owns()) dbPromise = null
      reject(new Error('IndexedDB upgrade blocked by another tab'))
    }
    request.onsuccess = () => {
      const db = request.result
      if (!owns()) {
        // A newer open already replaced this one. Closing keeps the connection
        // from outliving the promise nobody holds any more.
        db.close()
        reject(new Error('IndexedDB connection superseded before it opened'))
        return
      }
      db.onversionchange = () => {
        db.close()
        if (owns()) dbPromise = null
      }
      resolve(db)
    }

    request.onupgradeneeded = (event) => {
      const { oldVersion } = event
      const openRequest = event.target as IDBOpenDBRequest
      const db = openRequest.result

      // The store-existence check is not implied by the version gate: a database
      // left at v1 with no store would otherwise abort every open from here on.
      if (oldVersion < 1 || !db.objectStoreNames.contains(STORAGE_STORE_NAME)) {
        db.createObjectStore(STORAGE_STORE_NAME)
      }
      if (oldVersion < 2) {
        const upgrade = openRequest.transaction
        // Committing v2 with no migration would strand every row under a key
        // nothing reads, so refuse the upgrade instead.
        if (!upgrade) throw new Error('IndexedDB upgrade ran without its transaction')
        migrateToFlatKeys(upgrade)
      }
    }
  })

  dbPromise = pending

  return dbPromise
}

/**
 * Open the shared IndexedDB connection for operations the key/value API cannot
 * express, such as cursor iteration. Resolves null during SSR.
 */
export async function openStorageDb(): Promise<IDBDatabase | null> {
  if (!isClient) return null
  return getDB()
}

/**
 * Run a write on the shared connection, resolving only once its transaction
 * commits.
 *
 * The commit is what makes a write durable: a request can report success and
 * the transaction still abort, which is exactly how a quota failure arrives.
 * Reporting on the request alone would tell the caller its data is saved when
 * the store dropped it.
 */
async function runWrite(
  label: string,
  issue: (store: IDBObjectStore) => IDBRequest,
): Promise<Result<void, StorageReadError>> {
  if (!isClient) return err({ kind: 'notInBrowser' })

  try {
    const db = await getDB()
    await new Promise<void>((resolve, reject) => {
      const transaction = db.transaction(STORAGE_STORE_NAME, 'readwrite')
      const request = issue(transaction.objectStore(STORAGE_STORE_NAME))

      transaction.oncomplete = () => resolve()
      transaction.onabort = () => reject(transaction.error ?? request.error)
      transaction.onerror = () => reject(transaction.error ?? request.error)
      request.onerror = () => reject(request.error)
    })
    return ok(undefined)
  } catch (error) {
    console.error(`IndexedDB.${label} failed`, error)
    return err({ kind: 'ioError', cause: error })
  }
}

export const storage = {
  /**
   * Get item from IndexedDB (SSR-safe).
   *
   * A key the store does not hold is `ok(null)`; only a read that could not be
   * performed is `err`, so a caller that ignores absence does not also ignore
   * a broken database.
   */
  async getItem(key: string): Promise<Result<string | null, StorageReadError>> {
    if (!isClient) return err({ kind: 'notInBrowser' })

    try {
      const db = await getDB()
      const value = await new Promise<string | null>((resolve, reject) => {
        const transaction = db.transaction(STORAGE_STORE_NAME, 'readonly')
        const store = transaction.objectStore(STORAGE_STORE_NAME)
        const request = store.get(key)

        request.onsuccess = () => {
          resolve(request.result ?? null)
        }
        request.onerror = () => reject(request.error)
      })
      return ok(value)
    } catch (error) {
      // Log for debugging in production (Sentry will auto-capture console.error)
      console.error(`IndexedDB.getItem failed for key: ${key}`, error)
      return err({ kind: 'ioError', cause: error })
    }
  },

  /**
   * Set item in IndexedDB (SSR-safe).
   *
   * Reports whether the write committed. A caller told a save succeeded when it
   * did not will happily discard the only copy it still had.
   */
  async setItem(key: string, value: string): Promise<Result<void, StorageReadError>> {
    return runWrite(`setItem for key: ${key}`, (store) => store.put(value, key))
  },

  /** Remove item from IndexedDB (SSR-safe), reporting whether the delete committed. */
  async removeItem(key: string): Promise<Result<void, StorageReadError>> {
    return runWrite(`removeItem for key: ${key}`, (store) => store.delete(key))
  },

  /** Clear all items from IndexedDB (SSR-safe), reporting whether the clear committed. */
  async clear(): Promise<Result<void, StorageReadError>> {
    return runWrite('clear', (store) => store.clear())
  },
}
