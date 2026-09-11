import { storage, openStorageDb, STORAGE_STORE_NAME } from '@/lib/storage'
import { PLANNER_STORAGE_KEYS } from '@/lib/constants'
import { ok, err } from '@/lib/result'
import { validateDataOrNull } from '@/lib/validation'
import { migrateKeywords } from '@/shared/gameData'
import {
  LocalTombstoneSchema,
  SaveablePlannerSchema,
  toSaveablePlanner,
} from '../schemas/PlannerSchemas'
import { classifyAppError } from '@/lib/apiErrorClassifier'
import { isMDPlanner } from '../types/PlannerTypes'
import type { Result } from '@/lib/result'
import type { StorageReadError } from '@/lib/storage'
import type { ZodType } from 'zod'
import type { AppError } from '@/lib/apiErrorClassifier'
import type { SaveablePlanner, PlannerSummary, LocalTombstone } from '../types/PlannerTypes'

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
  /** Tombstone row key: tombstone:{plannerId} */
  tombstone: (plannerId: string) => `${PLANNER_STORAGE_KEYS.TOMBSTONE}:${plannerId}`,
}

/**
 * Parses a storage key to extract components
 * @param key - Storage key in format planner:{plannerId}
 * @returns Parsed components or null if invalid
 */
function parseStorageKey(key: string): { prefix: string; plannerId: string } | null {
  const [prefix, plannerId, ...rest] = key.split(':')
  if (prefix === undefined || plannerId === undefined || rest.length > 0) return null
  return { prefix, plannerId }
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
  /** Record a deletion the server has not been told about */
  writeTombstone: (tombstone: LocalTombstone) => Promise<Result<void, AppError>>
  /** Every deletion still awaiting the server */
  listTombstones: () => Promise<LocalTombstone[]>
  /** Forget a deletion the server has settled */
  clearTombstone: (id: string) => Promise<Result<void, AppError>>
}

/** The row a planner is stored as, or null when it fails the schema. */
function plannerRow(planner: SaveablePlanner): { key: string; json: string } | null {
  const validated = validateDataOrNull(
    planner,
    SaveablePlannerSchema,
    `planner save / ${planner.metadata?.id}`,
  )
  if (!validated) return null
  return { key: storageKeys.planner(planner.metadata.id), json: JSON.stringify(validated) }
}

/** The error a caller reports for a write the storage layer refused. */
function writeError(failure: StorageReadError): AppError {
  return classifyAppError(failure.kind === 'ioError' ? failure.cause : failure)
}

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
     * Save planner to IndexedDB under planner:{plannerId}. With the connection
     * open the write is issued inside the caller's task.
     * @returns the save error the caller reports to the user, or nothing on success
     */
    const saveToLocal = async (
      planner: SaveablePlanner,
      options?: StorageOperationOptions,
    ): Promise<Result<void, AppError>> => {
      if (!isClient) {
        return err({ kind: 'unknown' })
      }

      const row = plannerRow(planner)
      if (!row) {
        options?.onError?.('validationFailed')
        return err({ kind: 'unknown' })
      }

      try {
        const written = await storage.setItem(row.key, row.json)
        if (!written.ok) {
          const saveError = writeError(written.error)
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

    /** Every row under one key prefix that passes its schema, newest first left to the caller. */
    async function collectRows<T, R>(
      prefix: string,
      schema: ZodType<T>,
      label: string,
      map: (row: T) => R,
    ): Promise<R[]> {
      if (!isClient) return []
      const db = await openStorageDb()
      if (!db) return []

      const store = db.transaction(STORAGE_STORE_NAME, 'readonly').objectStore(STORAGE_STORE_NAME)
      return new Promise((resolve) => {
        const request = store.openCursor()
        const results: R[] = []

        request.onsuccess = (event) => {
          const cursor = (event.target as IDBRequest<IDBCursorWithValue>).result
          if (!cursor) {
            resolve(results)
            return
          }
          const parsed = parseStorageKey(cursor.key as string)
          if (parsed?.prefix === prefix) {
            try {
              const validated = validateDataOrNull(
                JSON.parse(cursor.value),
                schema,
                `${label} / ${parsed.plannerId}`,
              )
              if (validated) results.push(map(validated))
            } catch {
              // Skip invalid entries
            }
          }
          cursor.continue()
        }

        request.onerror = () => {
          console.error(`Failed to list ${label}`)
          resolve([])
        }
      })
    }

    const newestFirst = (a: string, b: string) => new Date(b).getTime() - new Date(a).getTime()

    const listLocal = async (): Promise<PlannerSummary[]> => {
      const rows = await collectRows(
        PLANNER_STORAGE_KEYS.PLANNER,
        SaveablePlannerSchema,
        'planner list',
        (validated): PlannerSummary => {
          const planner = toSaveablePlanner(validated.metadata, validated.config, validated.content)
          const { published } = planner.metadata
          return {
            id: planner.metadata.id,
            title: planner.metadata.title,
            plannerType: planner.config.type,
            category: planner.config.category,
            status: planner.metadata.status,
            lastModifiedAt: planner.metadata.lastModifiedAt,
            syncVersion: planner.metadata.syncVersion,
            ...(published !== undefined && { published }),
            // Keywords are MD-only; RR summaries omit the field entirely.
            ...(isMDPlanner(planner) && {
              selectedKeywords: migrateKeywords(planner.content.selectedKeywords),
            }),
          }
        },
      )
      return rows.sort((a, b) => newestFirst(a.lastModifiedAt, b.lastModifiedAt))
    }

    /** Full rows for content-based filtering; keyword ids are migrated since content is unvalidated here. */
    const listLocalFull = async (): Promise<SaveablePlanner[]> => {
      const rows = await collectRows(
        PLANNER_STORAGE_KEYS.PLANNER,
        SaveablePlannerSchema,
        'planner listFull',
        (validated) =>
          withMigratedKeywords(
            toSaveablePlanner(validated.metadata, validated.config, validated.content),
          ),
      )
      return rows.sort((a, b) => newestFirst(a.metadata.lastModifiedAt, b.metadata.lastModifiedAt))
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
          return err(writeError(removed.error))
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

    const writeTombstone = async (tombstone: LocalTombstone): Promise<Result<void, AppError>> => {
      if (!isClient) return err({ kind: 'unknown' })
      const written = await storage.setItem(
        storageKeys.tombstone(tombstone.id),
        JSON.stringify(tombstone),
      )
      return written.ok ? ok(undefined) : err(writeError(written.error))
    }

    const listTombstones = (): Promise<LocalTombstone[]> =>
      collectRows(PLANNER_STORAGE_KEYS.TOMBSTONE, LocalTombstoneSchema, 'tombstone list', (t) => t)

    const clearTombstone = async (id: string): Promise<Result<void, AppError>> => {
      if (!isClient) return err({ kind: 'unknown' })
      const removed = await storage.removeItem(storageKeys.tombstone(id))
      return removed.ok ? ok(undefined) : err(writeError(removed.error))
    }

    return {
      saveToLocal,
      loadFromLocal,
      listLocal,
      listLocalFull,
      deleteFromLocal,
      clearCorruptedLocal,
      writeTombstone,
      listTombstones,
      clearTombstone,
    }
  })() // Empty deps: functions only use module-level variables
}
