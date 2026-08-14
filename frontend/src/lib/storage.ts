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

/**
 * Rewrite `planner:md:{deviceId}:{plannerId}` rows as `planner:{plannerId}`.
 *
 * Two devices can hold the same planner id, so a collision keeps whichever row
 * was modified last. Each source is deleted only after its copy reads back at
 * the new key, so an interrupted upgrade loses nothing.
 *
 * Runs inside the `versionchange` transaction: every request below is queued on
 * it, so the upgrade cannot complete until the last delete has run.
 */
export function migrateToFlatKeys(transaction: IDBTransaction): void {
  const store = transaction.objectStore(STORAGE_STORE_NAME)
  const winners = new Map<string, { value: string; modifiedAt: number }>()
  const sources = new Map<string, string[]>()

  const cursorRequest = store.openCursor()
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
          const modifiedAt = lastModifiedAtOf(value)
          const held = winners.get(flatKey)
          if (!held || modifiedAt > held.modifiedAt) winners.set(flatKey, { value, modifiedAt })
          sources.set(flatKey, [...(sources.get(flatKey) ?? []), key])
        }
      }
      cursor.continue()
      return
    }

    for (const [flatKey, winner] of winners) {
      const put = store.put(winner.value, flatKey)
      put.onsuccess = () => {
        const verify = store.get(flatKey)
        verify.onsuccess = () => {
          if (verify.result !== winner.value) return
          for (const sourceKey of sources.get(flatKey) ?? []) store.delete(sourceKey)
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

  dbPromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)

    // Every failure path drops the shared promise: a rejected promise that
    // stays cached turns one transient open error into every later read.
    request.onerror = () => {
      dbPromise = null
      reject(request.error)
    }
    request.onblocked = () => {
      dbPromise = null
      reject(new Error('IndexedDB upgrade blocked by another tab'))
    }
    request.onsuccess = () => {
      request.result.onversionchange = () => {
        request.result.close()
        dbPromise = null
      }
      resolve(request.result)
    }

    request.onupgradeneeded = (event) => {
      const { oldVersion } = event
      const openRequest = event.target as IDBOpenDBRequest
      const db = openRequest.result

      if (oldVersion < 1) db.createObjectStore(STORAGE_STORE_NAME)
      if (oldVersion < 2 && openRequest.transaction) {
        migrateToFlatKeys(openRequest.transaction)
      }
    }
  })

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
   * Set item in IndexedDB (SSR-safe)
   * Silently fails during SSR
   */
  async setItem(key: string, value: string): Promise<void> {
    if (!isClient) return

    try {
      const db = await getDB()
      return new Promise((resolve, reject) => {
        const transaction = db.transaction(STORAGE_STORE_NAME, 'readwrite')
        const store = transaction.objectStore(STORAGE_STORE_NAME)
        const request = store.put(value, key)

        request.onsuccess = () => resolve()
        request.onerror = () => reject(request.error)
      })
    } catch (error) {
      console.error(`IndexedDB.setItem failed for key: ${key}`, error)
    }
  },

  /**
   * Remove item from IndexedDB (SSR-safe)
   * Silently fails during SSR
   */
  async removeItem(key: string): Promise<void> {
    if (!isClient) return

    try {
      const db = await getDB()
      return new Promise((resolve, reject) => {
        const transaction = db.transaction(STORAGE_STORE_NAME, 'readwrite')
        const store = transaction.objectStore(STORAGE_STORE_NAME)
        const request = store.delete(key)

        request.onsuccess = () => resolve()
        request.onerror = () => reject(request.error)
      })
    } catch (error) {
      console.error(`IndexedDB.removeItem failed for key: ${key}`, error)
    }
  },

  /**
   * Clear all items from IndexedDB (SSR-safe)
   * Silently fails during SSR
   */
  async clear(): Promise<void> {
    if (!isClient) return

    try {
      const db = await getDB()
      return new Promise((resolve, reject) => {
        const transaction = db.transaction(STORAGE_STORE_NAME, 'readwrite')
        const store = transaction.objectStore(STORAGE_STORE_NAME)
        const request = store.clear()

        request.onsuccess = () => resolve()
        request.onerror = () => reject(request.error)
      })
    } catch (error) {
      console.error('IndexedDB.clear failed', error)
    }
  },
}
