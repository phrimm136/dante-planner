import { storage, openStorageDb, STORAGE_STORE_NAME } from '@/lib/storage'
import { PLANNER_STORAGE_KEYS } from '@/lib/constants'
import { generateUUID } from '@/lib/uuid'
import { ok, err } from '@/lib/result'
import { migrateKeywords } from '@/shared/gameData'
import { SaveablePlannerSchema, toSaveablePlanner } from '../schemas/PlannerSchemas'
import { classifySaveError } from '../lib/plannerSaveErrors'
import { isMDPlanner } from '../types/PlannerTypes'
import type { Result } from '@/lib/result'
import type { SaveError } from '../lib/plannerSaveErrors'
import type { SaveablePlanner, PlannerSummary } from '../types/PlannerTypes'

/**
 * SSR safety check
 */
const isClient = typeof window !== 'undefined'

/**
 * Storage key builders for planner data
 * Key format: planner:{type}:{deviceId}:{plannerId}
 */
export const storageKeys = {
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
function parseStorageKey(
  key: string,
): { prefix: string; type: string; deviceId: string; plannerId: string } | null {
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
 * A planner whose keyword ids are the current ones.
 *
 * Rebuilt rather than assigned into: the input is parse output shared with the
 * caller, so migrating in place would rewrite a value someone else still holds.
 */
function withMigratedKeywords(planner: SaveablePlanner): SaveablePlanner {
  if (!isMDPlanner(planner)) return planner

  return {
    ...planner,
    content: {
      ...planner.content,
      selectedKeywords: migrateKeywords(planner.content.selectedKeywords),
    },
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
 * Result of a load operation.
 *
 * A successful load of a key that holds nothing carries `null`; a load that
 * could not be performed carries the code that says why.
 */
export type LoadResult = Result<SaveablePlanner | null, StorageErrorCode>

/**
 * Planner storage operations for IndexedDB
 * Provides CRUD operations with Zod validation and guest draft limits
 */
export interface PlannerStorageOperations {
  /** Get device ID from storage or create new UUID */
  getOrCreateDeviceId: () => Promise<string>
  /** Save planner to IndexedDB with proper key based on status, reporting why it failed */
  saveToLocal: (
    planner: SaveablePlanner,
    options?: StorageOperationOptions,
  ) => Promise<Result<void, SaveError>>
  /** Load and validate a planner; absent reads succeed with null, broken ones report a code */
  loadFromLocal: (id: string, options?: StorageOperationOptions) => Promise<LoadResult>
  /** List all planners as summaries, sorted by lastModifiedAt (newest first) */
  listLocal: () => Promise<PlannerSummary[]>
  /** List all planners with full content, sorted by lastModifiedAt (newest first) */
  listLocalFull: () => Promise<SaveablePlanner[]>
  /** Delete a planner by ID, reporting why the delete failed */
  deleteFromLocal: (id: string) => Promise<Result<void, SaveError>>
  /** Clear corrupted planner data by ID */
  clearCorruptedLocal: (id: string) => Promise<Result<void, SaveError>>
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
 *     await storage.saveToLocal(planner)
 *   }
 *
 *   const handleLoad = async (id: string) => {
 *     const result = await storage.loadFromLocal(id)
 *     if (result.ok && result.value) {
 *       // Use planner data
 *     }
 *   }
 * }
 * ```
 */
export function usePlannerStorage(): PlannerStorageOperations {
  // Memoize to return stable function references
  // All functions only use module-level variables, no React state/props
  return (() => {
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
     * @returns the save error the caller reports to the user, or nothing on success
     */
    const saveToLocal = async (
      planner: SaveablePlanner,
      options?: StorageOperationOptions,
    ): Promise<Result<void, SaveError>> => {
      if (!isClient) {
        return err({ kind: 'unknown' })
      }

      // Validate planner data before saving
      const validation = SaveablePlannerSchema.safeParse(planner)
      if (!validation.success) {
        console.error('Planner validation failed before save:')
        console.error('  Planner ID:', planner.metadata?.id)
        console.error('  Validation errors:', JSON.stringify(validation.error.issues, null, 2))
        validation.error.issues.forEach((err, idx) => {
          console.error(
            `  [${idx}] Path: ${err.path.join('.')}, Code: ${err.code}, Message: ${err.message}`,
          )
        })
        options?.onError?.('validationFailed')
        return err({ kind: 'unknown' })
      }

      try {
        const deviceId = await getOrCreateDeviceId()
        const key = storageKeys.md(deviceId, planner.metadata.id)

        await storage.setItem(key, JSON.stringify(validation.data))
        return ok(undefined)
      } catch (error) {
        const saveError = classifySaveError(error)

        console.error('Failed to save planner:', error)
        options?.onError?.(saveError.kind === 'quota' ? 'quotaExceeded' : 'saveFailed')

        return err(saveError)
      }
    }

    /**
     * Load planner by ID with Zod validation
     * @returns the validated planner, null when the key holds nothing, or the code
     *          that says why the read could not be performed
     */
    const loadFromLocal = async (
      id: string,
      options?: StorageOperationOptions,
    ): Promise<LoadResult> => {
      if (!isClient) return err('notInBrowser')

      let rawData: string | null
      try {
        const deviceId = await getOrCreateDeviceId()
        rawData = await storage.getItem(storageKeys.md(deviceId, id))
      } catch (error) {
        console.error('Failed to read planner from storage:', error)
        options?.onError?.('loadFailed')
        return err('loadFailed')
      }

      if (!rawData) return ok(null)

      let parsed: unknown
      try {
        parsed = JSON.parse(rawData)
      } catch (error) {
        console.error('Failed to parse planner data (corrupted JSON):', error)
        options?.onError?.('corruptedData')
        return err('corruptedData')
      }

      const result = SaveablePlannerSchema.safeParse(parsed)
      if (!result.success) {
        console.error('Planner validation failed:', result.error)
        options?.onError?.('validationFailed')
        return err('validationFailed')
      }

      const planner = toSaveablePlanner(
        result.data.metadata,
        result.data.config,
        result.data.content,
      )
      // Migrate renamed keyword ids so the detail/view page renders current icons.
      return ok(withMigratedKeywords(planner))
    }

    /**
     * List all planners as summaries
     * Iterates through IndexedDB to find all planner entries
     * @returns Array of PlannerSummary sorted by lastModifiedAt (newest first)
     */
    const listLocal = async (): Promise<PlannerSummary[]> => {
      if (!isClient) return []

      const deviceId = await getOrCreateDeviceId()

      // Access IndexedDB directly to iterate keys
      // storage utility doesn't expose key iteration, so we need direct access
      const db = await openStorageDb()
      if (!db) return []

      const transaction = db.transaction(STORAGE_STORE_NAME, 'readonly')
      const store = transaction.objectStore(STORAGE_STORE_NAME)

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
                  const planner = toSaveablePlanner(
                    validation.data.metadata,
                    validation.data.config,
                    validation.data.content,
                  )

                  // Extract keywords for MD planners only (RR has no keywords)
                  const selectedKeywords = isMDPlanner(planner)
                    ? migrateKeywords(planner.content.selectedKeywords)
                    : undefined

                  results.push({
                    id: planner.metadata.id,
                    title: planner.metadata.title,
                    plannerType: planner.config.type,
                    category: planner.config.category,
                    status: planner.metadata.status,
                    lastModifiedAt: planner.metadata.lastModifiedAt,
                    savedAt: planner.metadata.savedAt,
                    syncVersion: planner.metadata.syncVersion,
                    published: planner.metadata.published,
                    selectedKeywords,
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
              (a, b) => new Date(b.lastModifiedAt).getTime() - new Date(a.lastModifiedAt).getTime(),
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
     * List all planners with full content, sorted by lastModifiedAt (newest first).
     * Used for content-based filtering (personal plan search).
     * Same cursor logic as listLocal but returns full SaveablePlanner objects.
     */
    const listLocalFull = async (): Promise<SaveablePlanner[]> => {
      if (!isClient) return []

      const deviceId = await getOrCreateDeviceId()

      const db = await openStorageDb()
      if (!db) return []

      const transaction = db.transaction(STORAGE_STORE_NAME, 'readonly')
      const store = transaction.objectStore(STORAGE_STORE_NAME)

      return new Promise((resolve) => {
        const request = store.openCursor()
        const results: SaveablePlanner[] = []

        request.onsuccess = (event) => {
          const cursor = (event.target as IDBRequest<IDBCursorWithValue>).result
          if (cursor) {
            const key = cursor.key as string
            const parsed = parseStorageKey(key)

            if (
              parsed?.deviceId === deviceId &&
              parsed.prefix === PLANNER_STORAGE_KEYS.PLANNER &&
              parsed.type === PLANNER_STORAGE_KEYS.MD
            ) {
              try {
                const data = JSON.parse(cursor.value)
                const validation = SaveablePlannerSchema.safeParse(data)

                if (validation.success) {
                  const planner = toSaveablePlanner(
                    validation.data.metadata,
                    validation.data.config,
                    validation.data.content,
                  )
                  // Migrate renamed keyword ids so downstream sync-validation and
                  // keyword filtering see current ids (content is unvalidated here).
                  results.push(withMigratedKeywords(planner))
                }
              } catch {
                // Skip invalid entries
              }
            }
            cursor.continue()
          } else {
            results.sort(
              (a, b) =>
                new Date(b.metadata.lastModifiedAt).getTime() -
                new Date(a.metadata.lastModifiedAt).getTime(),
            )
            resolve(results)
          }
        }

        request.onerror = () => {
          console.error('Failed to list full planners')
          resolve([])
        }
      })
    }

    /**
     * Delete a planner by ID
     * @returns the delete error the caller reports to the user, or nothing on success
     */
    const deleteFromLocal = async (id: string): Promise<Result<void, SaveError>> => {
      if (!isClient) return err({ kind: 'unknown' })

      try {
        const deviceId = await getOrCreateDeviceId()
        await storage.removeItem(storageKeys.md(deviceId, id))
        return ok(undefined)
      } catch (error) {
        console.error('Failed to delete planner:', error)
        return err(classifySaveError(error))
      }
    }

    /**
     * Clear corrupted planner data by ID
     * Used when validation fails on load to clean up invalid data
     */
    async function clearCorruptedLocal(id: string): Promise<Result<void, SaveError>> {
      return deleteFromLocal(id)
    }

    return {
      getOrCreateDeviceId,
      saveToLocal,
      loadFromLocal,
      listLocal,
      listLocalFull,
      deleteFromLocal,
      clearCorruptedLocal,
    }
  })() // Empty deps: functions only use module-level variables
}
