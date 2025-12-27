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

const DB_NAME = 'danteplanner';
const STORE_NAME = 'planner';
const DB_VERSION = 1;

const isClient = typeof window !== 'undefined';

let dbPromise: Promise<IDBDatabase> | null = null;

function getDB(): Promise<IDBDatabase> {
  if (!isClient) {
    return Promise.reject(new Error('IndexedDB not available on server'));
  }

  if (dbPromise) {
    return dbPromise;
  }

  dbPromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result);

    request.onupgradeneeded = (event) => {
      const db = (event.target as IDBOpenDBRequest).result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME);
      }
    };
  });

  return dbPromise;
}

export const storage = {
  /**
   * Get item from IndexedDB (SSR-safe)
   * @returns null if key doesn't exist or during SSR
   */
  async getItem(key: string): Promise<string | null> {
    if (!isClient) return null;

    try {
      const db = await getDB();
      return new Promise((resolve, reject) => {
        const transaction = db.transaction(STORE_NAME, 'readonly');
        const store = transaction.objectStore(STORE_NAME);
        const request = store.get(key);

        request.onsuccess = () => {
          resolve(request.result ?? null);
        };
        request.onerror = () => reject(request.error);
      });
    } catch (error) {
      // Log for debugging in production (Sentry will auto-capture console.error)
      console.error(`IndexedDB.getItem failed for key: ${key}`, error);
      return null;
    }
  },

  /**
   * Set item in IndexedDB (SSR-safe)
   * Silently fails during SSR
   */
  async setItem(key: string, value: string): Promise<void> {
    if (!isClient) return;

    try {
      const db = await getDB();
      return new Promise((resolve, reject) => {
        const transaction = db.transaction(STORE_NAME, 'readwrite');
        const store = transaction.objectStore(STORE_NAME);
        const request = store.put(value, key);

        request.onsuccess = () => resolve();
        request.onerror = () => reject(request.error);
      });
    } catch (error) {
      console.error(`IndexedDB.setItem failed for key: ${key}`, error);
    }
  },

  /**
   * Remove item from IndexedDB (SSR-safe)
   * Silently fails during SSR
   */
  async removeItem(key: string): Promise<void> {
    if (!isClient) return;

    try {
      const db = await getDB();
      return new Promise((resolve, reject) => {
        const transaction = db.transaction(STORE_NAME, 'readwrite');
        const store = transaction.objectStore(STORE_NAME);
        const request = store.delete(key);

        request.onsuccess = () => resolve();
        request.onerror = () => reject(request.error);
      });
    } catch (error) {
      console.error(`IndexedDB.removeItem failed for key: ${key}`, error);
    }
  },

  /**
   * Clear all items from IndexedDB (SSR-safe)
   * Silently fails during SSR
   */
  async clear(): Promise<void> {
    if (!isClient) return;

    try {
      const db = await getDB();
      return new Promise((resolve, reject) => {
        const transaction = db.transaction(STORE_NAME, 'readwrite');
        const store = transaction.objectStore(STORE_NAME);
        const request = store.clear();

        request.onsuccess = () => resolve();
        request.onerror = () => reject(request.error);
      });
    } catch (error) {
      console.error('IndexedDB.clear failed', error);
    }
  },
};
