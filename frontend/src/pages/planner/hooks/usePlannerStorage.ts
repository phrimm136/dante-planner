import { storage, openStorageDb, STORAGE_STORE_NAME } from '@/lib/storage'
import { PLANNER_STORAGE_KEYS } from '@/lib/constants'
import { generateUUID } from '@/lib/uuid'
import { ok, err } from '@/lib/result'
import { validateDataOrNull } from '@/lib/validation'
import { migrateKeywords } from '@/shared/gameData'
import { SaveablePlannerSchema, toSaveablePlanner } from '../schemas/PlannerSchemas'
import { classifyAppError } from '@/lib/apiErrorClassifier'
import { isMDPlanner } from '../types/PlannerTypes'
import type { Result } from '@/lib/result'
import type { StorageReadError } from '@/lib/storage'
import type { AppError } from '@/lib/apiErrorClassifier'
import type { SaveablePlanner, PlannerSummary } from '../types/PlannerTypes'

/**
 * SSR safety check
 */
const isClient = typeof window !== 'undefined'

/**
 * Storage key builders for planner data
 * Key format: planner:{plannerId}
 */
export const storageKeys = {
  /** Planner row key: planner:{plannerId} */
  planner: (plannerId: string) => `${PLANNER_STORAGE_KEYS.PLANNER}:${plannerId}`,
  /** Device ID key */
  deviceId: () => PLANNER_STORAGE_KEYS.DEVICE_ID,
}

/**
 * Parses a storage key to extract components
 * @param key - Storage key in format planner:{plannerId}
 * @returns Parsed components or null if invalid
 */
function parseStorageKey(key: string): { prefix: string; plannerId: string } | null {
  const parts = key.split(':')
  if (parts.length !== 2) return null
  return {
    prefix: parts[0],
    plannerId: parts[1],
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
  /** Get device ID from storage or create new UUID; a read that broke reports why */
  getOrCreateDeviceId: () => Promise<Result<string, StorageReadError>>
  /** Save planner to IndexedDB with proper key based on status, reporting why it failed */
  saveToLocal: (
    planner: SaveablePlanner,
    options?: StorageOperationOptions,
  ) => Promise<Result<void, AppError>>
  /** Load and validate a planner; absent reads succeed with null, broken ones report a code */
  loadFromLocal: (id: string, options?: StorageOperationOptions) => Promise<LoadResult>
  /** List all planners as summaries, sorted by lastModifiedAt (newest first) */
  listLocal: () => Promise<PlannerSummary[]>
  /** List all planners with full content, sorted by lastModifiedAt (newest first) */
  listLocalFull: () => Promise<SaveablePlanner[]>
  /** Delete a planner by ID, reporting why the delete failed */
  deleteFromLocal: (id: string) => Promise<Result<void, AppError>>
  /** Clear corrupted planner data by ID */
  clearCorruptedLocal: (id: string) => Promise<Result<void, AppError>>
}

// Promise cache for getOrCreateDeviceId to prevent race conditions
let deviceIdPromise: Promise<Result<string, StorageReadError>> | null = null

/**
 * Hook that provides planner storage operations using IndexedDB
 *
 * All operations are SSR-safe and use Zod validation for loaded data.
 * Keys follow the format: {prefix}:{plannerId}
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
    const getOrCreateDeviceId = async (): Promise<Result<string, StorageReadError>> => {
      if (!isClient) return err({ kind: 'notInBrowser' })

      // Return cached promise if already in progress (prevents race condition)
      if (deviceIdPromise) return deviceIdPromise

      deviceIdPromise = (async () => {
        try {
          const existingId = await storage.getItem(storageKeys.deviceId())
          // A read that broke says nothing about whether an id exists. Minting
          // one here would orphan every row written under the previous id.
          if (!existingId.ok) return existingId
          if (existingId.value) {
            return ok(existingId.value)
          }

          const newId = generateUUID()
          const written = await storage.setItem(storageKeys.deviceId(), newId)
          // An id that did not persist would be minted again on the next read,
          // scattering rows across a new identity every time.
          if (!written.ok) return written
          return ok(newId)
        } finally {
          // Clear promise cache after resolution to allow re-fetch if storage is cleared externally
          deviceIdPromise = null
        }
      })()

      return deviceIdPromise
    }

    /**
     * Save planner to IndexedDB
     * Uses unified key: planner:{plannerId}
     * Validates planner data with Zod before saving
     * @returns the save error the caller reports to the user, or nothing on success
     */
    const saveToLocal = async (
      planner: SaveablePlanner,
      options?: StorageOperationOptions,
    ): Promise<Result<void, AppError>> => {
      if (!isClient) {
        return err({ kind: 'unknown' })
      }

      // Validate planner data before saving
      const validated = validateDataOrNull(
        planner,
        SaveablePlannerSchema,
        `planner save / ${planner.metadata?.id}`,
      )
      if (!validated) {
        options?.onError?.('validationFailed')
        return err({ kind: 'unknown' })
      }

      try {
        const key = storageKeys.planner(planner.metadata.id)

        const written = await storage.setItem(key, JSON.stringify(validated))
        if (!written.ok) {
          const saveError = classifyAppError(
            written.error.kind === 'ioError' ? written.error.cause : written.error,
          )
          options?.onError?.(saveError.kind === 'quota' ? 'quotaExceeded' : 'saveFailed')
          return err(saveError)
        }
        return ok(undefined)
      } catch (error) {
        const saveError = classifyAppError(error)

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
        const read = await storage.getItem(storageKeys.planner(id))
        if (!read.ok) {
          console.error('Failed to read planner from storage:', read.error)
          options?.onError?.('loadFailed')
          return err('loadFailed')
        }
        rawData = read.value
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

      const validated = validateDataOrNull(parsed, SaveablePlannerSchema, `planner load / ${id}`)
      if (!validated) {
        options?.onError?.('validationFailed')
        return err('validationFailed')
      }

      const planner = toSaveablePlanner(validated.metadata, validated.config, validated.content)
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

            // Only include planner rows; the deviceId singleton parses to null
            if (parsed?.prefix === PLANNER_STORAGE_KEYS.PLANNER) {
              try {
                const data = JSON.parse(cursor.value)
                const validated = validateDataOrNull(
                  data,
                  SaveablePlannerSchema,
                  `planner list / ${parsed.plannerId}`,
                )

                if (validated) {
                  const planner = toSaveablePlanner(
                    validated.metadata,
                    validated.config,
                    validated.content,
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

            if (parsed?.prefix === PLANNER_STORAGE_KEYS.PLANNER) {
              try {
                const data = JSON.parse(cursor.value)
                const validated = validateDataOrNull(
                  data,
                  SaveablePlannerSchema,
                  `planner listFull / ${parsed.plannerId}`,
                )

                if (validated) {
                  const planner = toSaveablePlanner(
                    validated.metadata,
                    validated.config,
                    validated.content,
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
    const deleteFromLocal = async (id: string): Promise<Result<void, AppError>> => {
      if (!isClient) return err({ kind: 'unknown' })

      try {
        const removed = await storage.removeItem(storageKeys.planner(id))
        if (!removed.ok) {
          console.error('Failed to delete planner:', removed.error)
          return err(
            classifyAppError(
              removed.error.kind === 'ioError' ? removed.error.cause : removed.error,
            ),
          )
        }
        return ok(undefined)
      } catch (error) {
        console.error('Failed to delete planner:', error)
        return err(classifyAppError(error))
      }
    }

    /**
     * Clear corrupted planner data by ID
     * Used when validation fails on load to clean up invalid data
     */
    async function clearCorruptedLocal(id: string): Promise<Result<void, AppError>> {
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
