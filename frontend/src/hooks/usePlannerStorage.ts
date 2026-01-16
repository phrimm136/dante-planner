import { storage } from '@/lib/storage'
import { PLANNER_STORAGE_KEYS } from '@/lib/constants'
import { SaveablePlannerSchema } from '@/schemas/PlannerSchemas'
import type { SaveablePlanner, PlannerSummary } from '@/types/PlannerTypes'

/**
 * SSR safety check
 */
const isClient = typeof window !== 'undefined'

/**
 * Generates a UUID v4
 * Uses crypto.randomUUID if available, falls back to manual generation
 */
function generateUUID(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  // Fallback for environments without crypto.randomUUID
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

/**
 * Storage key builders for planner data
 * Key format: planner:{type}:{deviceId}:{plannerId}
 */
const storageKeys = {
  /** Mirror Dungeon planner key: planner:md:{deviceId}:{plannerId} */
  md: (deviceId: string, plannerId: string) =>
    `${PLANNER_STORAGE_KEYS.PLANNER}:${PLANNER_STORAGE_KEYS.MD}:${deviceId}:${plannerId}`,
  /** Device ID key */
  deviceId: () => PLANNER_STORAGE_KEYS.DEVICE_ID,
}

/**
 * Parses a storage key to extract components
 * @param key - Storage key in format planner:{type}:{deviceId}:{plannerId}
 * @returns Parsed components or null if invalid
 */
function parseStorageKey(key: string): { prefix: string; type: string; deviceId: string; plannerId: string } | null {
  const parts = key.split(':')
  if (parts.length !== 4) return null
  return {
    prefix: parts[0],
    type: parts[1],
    deviceId: parts[2],
    plannerId: parts[3],
  }
}

/**
 * Options for storage operations with error handling
 */
export interface StorageOperationOptions {
  /** Callback for error notification with error code (for i18n translation) */
  onError?: (errorCode: StorageErrorCode) => void
}

/**
 * Error codes for storage operations
 * Page components use these to display translated error messages
 */
export type StorageErrorCode =
  | 'quotaExceeded'
  | 'saveFailed'
  | 'loadFailed'
  | 'validationFailed'
  | 'corruptedData'
  | 'notInBrowser'

/**
 * Result of a save operation
 */
export interface SaveResult {
  /** Whether the save succeeded */
  success: boolean
  /** Error code if save failed (for i18n translation) */
  errorCode?: StorageErrorCode
}

/**
 * Planner storage operations for IndexedDB
 * Provides CRUD operations with Zod validation and guest draft limits
 */
export interface PlannerStorageOperations {
  /** Get device ID from storage or create new UUID */
  getOrCreateDeviceId: () => Promise<string>
  /** Save planner to IndexedDB with proper key based on status. Returns success/failure result. */
  savePlanner: (planner: SaveablePlanner, options?: StorageOperationOptions) => Promise<SaveResult>
  /** Load and validate planner, returns null if not found or invalid */
  loadPlanner: (id: string, options?: StorageOperationOptions) => Promise<SaveablePlanner | null>
  /** List all planners as summaries, sorted by lastModifiedAt (newest first) */
  listPlanners: () => Promise<PlannerSummary[]>
  /** Delete a planner by ID */
  deletePlanner: (id: string) => Promise<void>
  /** Clear corrupted planner data by ID */
  clearCorruptedPlanner: (id: string) => Promise<void>
}

// Promise cache for getOrCreateDeviceId to prevent race conditions
let deviceIdPromise: Promise<string> | null = null

/**
 * Hook that provides planner storage operations using IndexedDB
 *
 * All operations are SSR-safe and use Zod validation for loaded data.
 * Keys follow the format: {prefix}:{deviceId}:{plannerId}
 *
 * @example
 * ```tsx
 * function PlannerPage() {
 *   const storage = usePlannerStorage()
 *
 *   const handleSave = async (planner: SaveablePlanner) => {
 *     await storage.savePlanner(planner)
 *   }
 *
 *   const handleLoad = async (id: string) => {
 *     const planner = await storage.loadPlanner(id)
 *     if (planner) {
 *       // Use planner data
 *     }
 *   }
 * }
 * ```
 */
export function usePlannerStorage(): PlannerStorageOperations {
  /**
   * Get device ID from storage or create a new one
   * Device ID is used for namespacing storage keys
   * Uses promise caching to prevent race conditions with concurrent calls
   */
  const getOrCreateDeviceId = async (): Promise<string> => {
    if (!isClient) return ''

    // Return cached promise if already in progress (prevents race condition)
    if (deviceIdPromise) return deviceIdPromise

    deviceIdPromise = (async () => {
      try {
        const existingId = await storage.getItem(storageKeys.deviceId())
        if (existingId) {
          return existingId
        }

        const newId = generateUUID()
        await storage.setItem(storageKeys.deviceId(), newId)
        return newId
      } finally {
        // Clear promise cache after resolution to allow re-fetch if storage is cleared externally
        deviceIdPromise = null
      }
    })()

    return deviceIdPromise
  }

  /**
   * Save planner to IndexedDB
   * Uses unified key: planner:md:{deviceId}:{plannerId}
   * Validates planner data with Zod before saving
   * @returns SaveResult with success/failure status and error code
   */
  const savePlanner = async (
    planner: SaveablePlanner,
    options?: StorageOperationOptions
  ): Promise<SaveResult> => {
    if (!isClient) {
      return { success: false, errorCode: 'notInBrowser' }
    }

    // Validate planner data before saving
    const validation = SaveablePlannerSchema.safeParse(planner)
    if (!validation.success) {
      console.error('Planner validation failed before save:')
      console.error('  Planner ID:', planner.metadata?.id)
      console.error('  Validation errors:', JSON.stringify(validation.error.issues, null, 2))
      validation.error.issues.forEach((err, idx) => {
        console.error(`  [${idx}] Path: ${err.path.join('.')}, Code: ${err.code}, Message: ${err.message}`)
      })
      options?.onError?.('validationFailed')
      return { success: false, errorCode: 'validationFailed' }
    }

    try {
      const deviceId = await getOrCreateDeviceId()
      const key = storageKeys.md(deviceId, planner.metadata.id)

      await storage.setItem(key, JSON.stringify(validation.data))
      return { success: true }
    } catch (error) {
      const isQuotaError =
        error instanceof DOMException &&
        (error.name === 'QuotaExceededError' || error.code === 22)

      const errorCode: StorageErrorCode = isQuotaError ? 'quotaExceeded' : 'saveFailed'

      console.error('Failed to save planner:', error)
      options?.onError?.(errorCode)

      return { success: false, errorCode }
    }
  }

  /**
   * Load planner by ID with Zod validation
   * Searches both draft and saved prefixes
   * @returns Validated planner or null if not found/invalid
   */
  const loadPlanner = async (
    id: string,
    options?: StorageOperationOptions
  ): Promise<SaveablePlanner | null> => {
    if (!isClient) return null

    const deviceId = await getOrCreateDeviceId()
    const key = storageKeys.md(deviceId, id)
    const rawData = await storage.getItem(key)

    if (!rawData) return null

    try {
      const parsed = JSON.parse(rawData)
      const result = SaveablePlannerSchema.safeParse(parsed)

      if (!result.success) {
        console.error('Planner validation failed:', result.error)
        options?.onError?.('validationFailed')
        return null
      }

      // Type assertion needed because:
      // 1. Zod schema uses z.record(z.string(), z.unknown()) for content flexibility
      // 2. TypeScript expects specific PlannerContent type
      // Zod validation ensures the structure is correct at runtime
      return result.data as unknown as SaveablePlanner
    } catch (error) {
      console.error('Failed to parse planner data (corrupted JSON):', error)
      options?.onError?.('corruptedData')
      return null
    }
  }

  /**
   * List all planners as summaries
   * Iterates through IndexedDB to find all planner entries
   * @returns Array of PlannerSummary sorted by lastModifiedAt (newest first)
   */
  const listPlanners = async (): Promise<PlannerSummary[]> => {
    if (!isClient) return []

    const deviceId = await getOrCreateDeviceId()

    // Access IndexedDB directly to iterate keys
    // storage utility doesn't expose key iteration, so we need direct access
    const db = await openDB()
    if (!db) return []

    const transaction = db.transaction('planner', 'readonly')
    const store = transaction.objectStore('planner')

    return new Promise((resolve) => {
      const request = store.openCursor()
      const results: PlannerSummary[] = []

      request.onsuccess = (event) => {
        const cursor = (event.target as IDBRequest<IDBCursorWithValue>).result
        if (cursor) {
          const key = cursor.key as string
          const parsed = parseStorageKey(key)

          // Only include MD planners for this device
          if (
            parsed?.deviceId === deviceId &&
            parsed.prefix === PLANNER_STORAGE_KEYS.PLANNER &&
            parsed.type === PLANNER_STORAGE_KEYS.MD
          ) {
            try {
              const data = JSON.parse(cursor.value)
              const validation = SaveablePlannerSchema.safeParse(data)

              if (validation.success) {
                // Type assertion needed because Zod schema uses flexible content type
                const planner = validation.data as unknown as SaveablePlanner
                results.push({
                  id: planner.metadata.id,
                  title: planner.metadata.title,
                  plannerType: planner.config.type,
                  category: planner.config.category,
                  status: planner.metadata.status,
                  lastModifiedAt: planner.metadata.lastModifiedAt,
                  savedAt: planner.metadata.savedAt,
                  syncVersion: planner.metadata.syncVersion,
                })
              }
            } catch {
              // Skip invalid entries
            }
          }
          cursor.continue()
        } else {
          // Sort by lastModifiedAt descending (newest first)
          results.sort(
            (a, b) =>
              new Date(b.lastModifiedAt).getTime() - new Date(a.lastModifiedAt).getTime()
          )
          resolve(results)
        }
      }

      request.onerror = () => {
        console.error('Failed to list planners')
        resolve([])
      }
    })
  }

  /**
   * Delete a planner by ID
   */
  const deletePlanner = async (id: string): Promise<void> => {
    if (!isClient) return

    const deviceId = await getOrCreateDeviceId()
    await storage.removeItem(storageKeys.md(deviceId, id))
  }

  /**
   * Clear corrupted planner data by ID
   * Used when validation fails on load to clean up invalid data
   */
  async function clearCorruptedPlanner(id: string): Promise<void> {
    await deletePlanner(id)
  }

  return {
    getOrCreateDeviceId,
    savePlanner,
    loadPlanner,
    listPlanners,
    deletePlanner,
    clearCorruptedPlanner,
  }
}

/**
 * Helper to open IndexedDB for direct cursor operations
 * Mirrors the storage.ts implementation
 */
const DB_NAME = 'danteplanner'
const STORE_NAME = 'planner'
const DB_VERSION = 1

let dbPromise: Promise<IDBDatabase> | null = null

function openDB(): Promise<IDBDatabase | null> {
  if (!isClient) return Promise.resolve(null)

  if (dbPromise) return dbPromise

  dbPromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)

    request.onerror = () => { reject(request.error); }
    request.onsuccess = () => { resolve(request.result); }

    request.onupgradeneeded = (event) => {
      const db = (event.target as IDBOpenDBRequest).result
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME)
      }
    }
  })

  return dbPromise
}
