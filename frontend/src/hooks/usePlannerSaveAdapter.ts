import { usePlannerStorage } from './usePlannerStorage'
import type { SaveablePlanner, PlannerSummary } from '@/types/PlannerTypes'
import type { SaveResult, StorageOperationOptions } from './usePlannerStorage'

/**
 * Return type for usePlannerSaveAdapter hook
 * Provides IndexedDB-only operations for local persistence
 */
export interface PlannerSaveAdapterOperations {
  /** Save planner to IndexedDB */
  saveToLocal: (planner: SaveablePlanner, options?: StorageOperationOptions) => Promise<SaveResult>
  /** Load planner from IndexedDB by ID */
  loadFromLocal: (id: string, options?: StorageOperationOptions) => Promise<SaveablePlanner | null>
  /** Delete planner from IndexedDB by ID */
  deleteFromLocal: (id: string) => Promise<void>
  /** List all local planners */
  listLocal: () => Promise<PlannerSummary[]>
  /** Get or create device ID */
  getOrCreateDeviceId: () => Promise<string>
}

/**
 * Adapter hook for IndexedDB-only planner operations
 *
 * This adapter wraps usePlannerStorage and provides a focused interface
 * for local persistence. Auto-save uses this adapter exclusively.
 *
 * @example
 * ```tsx
 * function PlannerEditor() {
 *   const saveAdapter = usePlannerSaveAdapter()
 *
 *   const handleAutoSave = async (planner: SaveablePlanner) => {
 *     const result = await saveAdapter.saveToLocal(planner)
 *     if (!result.success) {
 *       console.error('Auto-save failed:', result.errorCode)
 *     }
 *   }
 * }
 * ```
 */
export function usePlannerSaveAdapter(): PlannerSaveAdapterOperations {
  const storage = usePlannerStorage()

  /**
   * Save planner to IndexedDB
   * @returns SaveResult with success/failure status and error code
   */
  const saveToLocal = async (
    planner: SaveablePlanner,
    options?: StorageOperationOptions
  ): Promise<SaveResult> => {
    return storage.savePlanner(planner, options)
  }

  /**
   * Load planner from IndexedDB by ID
   * @returns Validated planner or null if not found/invalid
   */
  const loadFromLocal = async (
    id: string,
    options?: StorageOperationOptions
  ): Promise<SaveablePlanner | null> => {
    return storage.loadPlanner(id, options)
  }

  /**
   * Delete planner from IndexedDB by ID
   */
  const deleteFromLocal = async (id: string): Promise<void> => {
    return storage.deletePlanner(id)
  }

  /**
   * List all local planners as summaries
   * @returns Array of PlannerSummary sorted by lastModifiedAt (newest first)
   */
  const listLocal = async (): Promise<PlannerSummary[]> => {
    return storage.listPlanners()
  }

  /**
   * Get or create device ID for local storage namespacing
   */
  const getOrCreateDeviceId = async (): Promise<string> => {
    return storage.getOrCreateDeviceId()
  }

  return {
    saveToLocal,
    loadFromLocal,
    deleteFromLocal,
    listLocal,
    getOrCreateDeviceId,
  }
}
