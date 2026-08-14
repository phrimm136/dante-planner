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
import type { Result, Tagged } from './result'

const DB_NAME = 'danteplanner'
/** Object store holding every persisted row; exported for direct cursor access. */
export const STORAGE_STORE_NAME = 'planner'
const DB_VERSION = 1

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
      const db = (event.target as IDBOpenDBRequest).result
      if (!db.objectStoreNames.contains(STORAGE_STORE_NAME)) {
        db.createObjectStore(STORAGE_STORE_NAME)
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
