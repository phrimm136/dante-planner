import { useEffect, useRef, useState, useCallback } from 'react'
import { usePlannerStorage } from './usePlannerStorage'
import { usePlannerStorageAdapter } from './usePlannerStorageAdapter'
import { serializeSets } from '@/schemas/PlannerSchemas'
import { ConflictError } from '@/lib/api'
import { AUTO_SAVE_DEBOUNCE_MS } from '@/lib/constants'
import type { MDCategory, PlannerType } from '@/lib/constants'
import type { SinnerEquipment, SkillEAState } from '@/types/DeckTypes'
import type { FloorThemeSelection } from '@/types/ThemePackTypes'
import type { NoteContent } from '@/types/NoteEditorTypes'
import type { SaveablePlanner, ConflictState, ConflictResolutionChoice, PlannerConfig, PlannerContent } from '@/types/PlannerTypes'

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
 * Planner state interface matching PlannerMDNewPage state structure
 * Uses Set types for in-memory representation
 */
export interface PlannerState {
  /** Planner title */
  title: string
  /** MD category (5F, 10F, 15F) */
  category: MDCategory
  /** Selected planner keywords */
  selectedKeywords: Set<string>
  /** Selected start buff IDs */
  selectedBuffIds: Set<number>
  /** Currently selected gift keyword filter */
  selectedGiftKeyword: string | null
  /** Selected start gift IDs */
  selectedGiftIds: Set<string>
  /** Observation gift IDs */
  observationGiftIds: Set<string>
  /** Comprehensive gift IDs with enhancement encoding */
  comprehensiveGiftIds: Set<string>
  /** Equipment configuration per sinner */
  equipment: Record<string, SinnerEquipment>
  /** Deployment order as array of sinner indices */
  deploymentOrder: number[]
  /** Skill EA state per sinner */
  skillEAState: Record<string, SkillEAState>
  /** Floor theme selections (has Set inside) */
  floorSelections: FloorThemeSelection[]
  /** Section notes keyed by section identifier */
  sectionNotes: Record<string, NoteContent>
}

/**
 * Save error codes for i18n translation
 * Components should use these codes to display localized error messages
 */
export type SaveErrorCode =
  | 'conflict'
  | 'saveFailed'
  | 'quotaExceeded'
  | null

/**
 * Options for usePlannerSave hook
 */
export interface UsePlannerSaveOptions {
  /** Current planner state */
  state: PlannerState
  /** Schema version for data format */
  schemaVersion: number
  /** Game content version */
  contentVersion: number
  /** Type of planner */
  plannerType: PlannerType
  /** Optional existing planner ID (for editing) */
  initialPlannerId?: string
  /** Optional initial sync version (for editing) */
  initialSyncVersion?: number
  /** Callback when server version is reloaded (on discard) */
  onServerReload?: (planner: SaveablePlanner) => void
}

/**
 * Return type for usePlannerSave hook
 */
export interface PlannerSaveResult {
  /** Current planner ID (creates new if none) */
  plannerId: string
  /** Whether auto-save is in progress */
  isAutoSaving: boolean
  /** Whether manual save is in progress */
  isSaving: boolean
  /** Error code if last save failed (for i18n translation) */
  errorCode: SaveErrorCode
  /** Conflict info when errorCode === 'conflict' */
  conflictState: ConflictState | null
  /** Clear the current error */
  clearError: () => void
  /** Trigger manual save, returns true if succeeded */
  save: () => Promise<boolean>
  /** Resolve a conflict (overwrite local or discard and reload), returns true if succeeded */
  resolveConflict: (choice: ConflictResolutionChoice) => Promise<boolean>
  /** Current sync version (for debugging) */
  syncVersion: number
  /** Whether there are unsynced changes (for beforeunload warning) */
  hasUnsyncedChanges: boolean
  /** Last synced timestamp (ISO 8601, null if never synced) */
  lastSyncedAt: string | null
}

/**
 * Serialize PlannerState to SaveablePlanner format
 */
function createSaveablePlanner(
  state: PlannerState,
  plannerId: string,
  deviceId: string,
  schemaVersion: number,
  contentVersion: number,
  plannerType: PlannerType,
  existingCreatedAt: string | null,
  existingSyncVersion: number,
  status: 'draft' | 'saved' = 'draft'
): SaveablePlanner {
  const now = new Date().toISOString()

  // Convert Sets to arrays using serializeSets
  const serialized = serializeSets({
    selectedKeywords: state.selectedKeywords,
    selectedBuffIds: state.selectedBuffIds,
    selectedGiftIds: state.selectedGiftIds,
    observationGiftIds: state.observationGiftIds,
    comprehensiveGiftIds: state.comprehensiveGiftIds,
    floorSelections: state.floorSelections,
  })

  // Convert NoteContent to SerializableNoteContent
  const serializableNotes: Record<string, { content: typeof state.sectionNotes[string]['content'] }> = {}
  for (const [key, note] of Object.entries(state.sectionNotes)) {
    serializableNotes[key] = { content: note.content }
  }

  return {
    metadata: {
      id: plannerId,
      status,
      schemaVersion,
      contentVersion,
      plannerType,
      syncVersion: existingSyncVersion,
      createdAt: existingCreatedAt ?? now,
      lastModifiedAt: now,
      savedAt: status === 'saved' ? now : null,
      userId: null,
      deviceId,
    },
    config: {
      type: plannerType,
      category: state.category,
    } as PlannerConfig,
    content: {
      type: plannerType,
      title: state.title,
      selectedKeywords: serialized.selectedKeywords,
      selectedBuffIds: serialized.selectedBuffIds,
      selectedGiftKeyword: state.selectedGiftKeyword,
      selectedGiftIds: serialized.selectedGiftIds,
      observationGiftIds: serialized.observationGiftIds,
      comprehensiveGiftIds: serialized.comprehensiveGiftIds,
      equipment: state.equipment,
      deploymentOrder: state.deploymentOrder,
      skillEAState: state.skillEAState,
      floorSelections: serialized.floorSelections,
      sectionNotes: serializableNotes,
    } as PlannerContent,
  }
}

/**
 * Deep comparison for dirty state detection
 */
function stateToComparableString(state: PlannerState): string {
  const serialized = serializeSets({
    selectedKeywords: state.selectedKeywords,
    selectedBuffIds: state.selectedBuffIds,
    selectedGiftIds: state.selectedGiftIds,
    observationGiftIds: state.observationGiftIds,
    comprehensiveGiftIds: state.comprehensiveGiftIds,
    floorSelections: state.floorSelections,
  })

  return JSON.stringify({
    title: state.title,
    category: state.category,
    selectedGiftKeyword: state.selectedGiftKeyword,
    equipment: state.equipment,
    deploymentOrder: state.deploymentOrder,
    skillEAState: state.skillEAState,
    sectionNotes: state.sectionNotes,
    ...serialized,
  })
}

/**
 * Unified hook for saving planner state (auto-save + manual save)
 *
 * Features:
 * - Auto-save with 2s debounce after state changes
 * - Manual save() function with proper syncVersion tracking
 * - Conflict detection with typed ConflictError
 * - Resolution via resolveConflict('overwrite' | 'discard')
 *
 * @example
 * ```tsx
 * function PlannerPage() {
 *   const [state, setState] = useState<PlannerState>(initialState)
 *
 *   const {
 *     plannerId,
 *     isAutoSaving,
 *     isSaving,
 *     errorCode,
 *     conflictState,
 *     save,
 *     resolveConflict,
 *   } = usePlannerSave({
 *     state,
 *     schemaVersion: 1,
 *     contentVersion: 6,
 *     plannerType: 'MIRROR_DUNGEON',
 *     onServerReload: (planner) => {
 *       // Update local state from server version
 *       setState(deserializePlanner(planner))
 *     },
 *   })
 *
 *   return (
 *     <div>
 *       {isAutoSaving && <span>Saving...</span>}
 *       <button onClick={save} disabled={isSaving}>Save</button>
 *       {errorCode === 'conflict' && (
 *         <ConflictDialog
 *           onOverwrite={() => resolveConflict('overwrite')}
 *           onDiscard={() => resolveConflict('discard')}
 *         />
 *       )}
 *     </div>
 *   )
 * }
 * ```
 */
export function usePlannerSave(options: UsePlannerSaveOptions): PlannerSaveResult {
  const {
    state,
    schemaVersion,
    contentVersion,
    plannerType,
    initialPlannerId,
    initialSyncVersion,
    onServerReload,
  } = options

  // Planner ID - create once and persist
  const [plannerId] = useState<string>(() => initialPlannerId ?? generateUUID())

  // Saving state indicators
  const [isAutoSaving, setIsAutoSaving] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [lastSyncedAt, setLastSyncedAt] = useState<string | null>(null)

  // Debounce timer ref
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Previous state for dirty checking
  const previousStateRef = useRef<string>('')

  // Last synced state for beforeunload warning detection
  const lastSyncedStateRef = useRef<string>('')

  // Flag to prevent save on first render
  const isInitialRenderRef = useRef(true)

  // Track the original createdAt timestamp
  const createdAtRef = useRef<string | null>(null)

  // Track sync version for optimistic locking
  const syncVersionRef = useRef<number>(initialSyncVersion ?? 1)

  // Error state
  const [errorCode, setErrorCode] = useState<SaveErrorCode>(null)
  const [conflictState, setConflictState] = useState<ConflictState | null>(null)

  // Storage adapter
  const adapter = usePlannerStorageAdapter()
  const localStorage = usePlannerStorage()

  /**
   * Core save logic used by both auto-save and manual save
   */
  const performSave = useCallback(async (status: 'draft' | 'saved'): Promise<boolean> => {
    if (!isClient) return false

    // Set createdAt on first save
    if (createdAtRef.current === null) {
      createdAtRef.current = new Date().toISOString()
    }

    // Get deviceId for guest mode
    let deviceId = ''
    if (!adapter.isAuthenticated) {
      deviceId = await localStorage.getOrCreateDeviceId()
      if (!deviceId) return false
    }

    const saveable = createSaveablePlanner(
      state,
      plannerId,
      deviceId,
      schemaVersion,
      contentVersion,
      plannerType,
      createdAtRef.current,
      syncVersionRef.current,
      status
    )

    // Save via adapter
    const saved = await adapter.savePlanner(saveable)

    // Update sync version from response
    if (adapter.isAuthenticated && saved.metadata.syncVersion) {
      syncVersionRef.current = saved.metadata.syncVersion
    }

    // Guest mode: track draft
    if (!adapter.isAuthenticated) {
      await localStorage.setCurrentDraftId(plannerId)
    }

    return true
  }, [state, plannerId, adapter, localStorage, schemaVersion, contentVersion, plannerType])

  /**
   * Handle save errors with typed detection
   */
  const handleSaveError = useCallback((error: unknown) => {
    if (error instanceof ConflictError) {
      setErrorCode('conflict')
      setConflictState({
        serverVersion: error.serverVersion,
        detectedAt: new Date().toISOString(),
      })
      return
    }

    // Check for storage error codes (from guest mode adapter)
    if (error instanceof Error) {
      const errorWithCode = error as Error & { code?: string }

      if (errorWithCode.code === 'quotaExceeded' ||
          error.message.includes('QuotaExceeded') ||
          error.message.includes('quota')) {
        setErrorCode('quotaExceeded')
        console.error('Save failed (quota exceeded):', error.message)
        return
      }

      if (errorWithCode.code === 'validationFailed') {
        // Log detailed validation error for debugging
        console.error('Save failed (validation):', error.message)
        setErrorCode('saveFailed')
        return
      }
    }

    // Generic error fallback
    setErrorCode('saveFailed')
    console.error('Save failed:', error)
  }, [])

  /**
   * Debounced auto-save function
   * Auto-saves ALWAYS go to IndexedDB only (local-first architecture)
   */
  const debouncedSave = useCallback(async () => {
    // CRITICAL: Prevent race condition with manual save
    if (isSaving) return

    const currentStateString = stateToComparableString(state)

    // Skip if state hasn't changed
    if (currentStateString === previousStateRef.current) {
      return
    }

    setIsAutoSaving(true)

    try {
      if (!isClient) return

      // Set createdAt on first save
      if (createdAtRef.current === null) {
        createdAtRef.current = new Date().toISOString()
      }

      // Get deviceId
      const deviceId = await localStorage.getOrCreateDeviceId()
      if (!deviceId) return

      // Create saveable planner
      const saveable = createSaveablePlanner(
        state,
        plannerId,
        deviceId,
        schemaVersion,
        contentVersion,
        plannerType,
        createdAtRef.current,
        syncVersionRef.current,
        'draft'
      )

      // Save directly to IndexedDB (bypass adapter)
      const result = await localStorage.savePlanner(saveable)
      if (!result.success) {
        const error = new Error(`Auto-save failed: ${result.errorCode}`)
        ;(error as Error & { code: string }).code = result.errorCode ?? 'saveFailed'
        throw error
      }

      // Track current draft
      await localStorage.setCurrentDraftId(plannerId)

      previousStateRef.current = currentStateString
    } catch (error: unknown) {
      handleSaveError(error)
    } finally {
      setIsAutoSaving(false)
    }
  }, [state, isSaving, localStorage, plannerId, schemaVersion, contentVersion, plannerType, handleSaveError])

  /**
   * Manual save function
   * @returns true if save succeeded, false if it failed
   */
  const save = useCallback(async (): Promise<boolean> => {
    if (!isClient) return false

    setIsSaving(true)

    try {
      await performSave('saved')
      const currentState = stateToComparableString(state)
      previousStateRef.current = currentState
      lastSyncedStateRef.current = currentState
      setLastSyncedAt(new Date().toISOString())
      return true
    } catch (error: unknown) {
      handleSaveError(error)
      return false
    } finally {
      setIsSaving(false)
    }
  }, [state, performSave, handleSaveError])

  /**
   * Resolve a conflict
   * - 'overwrite': Force-save with incremented syncVersion
   * - 'discard': Reload from server and update local state
   * @returns true if resolution succeeded, false if it failed
   */
  const resolveConflict = useCallback(async (choice: ConflictResolutionChoice): Promise<boolean> => {
    if (!conflictState) return false

    setIsSaving(true)

    try {
      if (choice === 'overwrite') {
        // Temporarily increment version for this save attempt
        const originalVersion = syncVersionRef.current
        const attemptVersion = conflictState.serverVersion + 1

        try {
          syncVersionRef.current = attemptVersion
          await performSave('saved')
          previousStateRef.current = stateToComparableString(state)
          // Success - version stays incremented
        } catch (saveError) {
          // Failure - restore original version to prevent desync
          syncVersionRef.current = originalVersion
          throw saveError // Re-throw to be caught by outer catch
        }
      } else {
        // Discard: reload from server
        const serverPlanner = await adapter.loadPlanner(plannerId)
        if (serverPlanner && onServerReload) {
          syncVersionRef.current = serverPlanner.metadata.syncVersion
          onServerReload(serverPlanner)
        }
      }

      // Clear conflict state only on success
      setErrorCode(null)
      setConflictState(null)
      return true
    } catch (error: unknown) {
      handleSaveError(error)
      return false
    } finally {
      setIsSaving(false)
    }
  }, [conflictState, state, performSave, adapter, plannerId, onServerReload, handleSaveError])

  /**
   * Clear error state
   */
  const clearError = useCallback(() => {
    setErrorCode(null)
    setConflictState(null)
  }, [])

  // Initialize lastSyncedStateRef for edit mode (prevents false "unsynced" on first render)
  useEffect(() => {
    if (!isClient) return

    if (isInitialRenderRef.current) {
      const initialState = stateToComparableString(state)
      previousStateRef.current = initialState

      // If editing existing planner with savedAt, initialize as synced
      if (initialPlannerId && initialSyncVersion && initialSyncVersion >= 1) {
        lastSyncedStateRef.current = initialState
      }

      isInitialRenderRef.current = false
    }
  }, [initialPlannerId, initialSyncVersion, state])

  // Effect for debounced auto-save
  useEffect(() => {
    if (!isClient) return
    if (isInitialRenderRef.current) return

    // Clear existing timer
    if (timerRef.current) {
      clearTimeout(timerRef.current)
    }

    // Set new debounce timer
    timerRef.current = setTimeout(() => {
      debouncedSave()
    }, AUTO_SAVE_DEBOUNCE_MS)

    // Cleanup
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current)
      }
    }
  }, [state, debouncedSave])

  // Check if there are unsynced changes
  const hasUnsyncedChanges = stateToComparableString(state) !== lastSyncedStateRef.current

  return {
    plannerId,
    isAutoSaving,
    isSaving,
    errorCode,
    conflictState,
    clearError,
    save,
    resolveConflict,
    syncVersion: syncVersionRef.current,
    hasUnsyncedChanges,
    lastSyncedAt,
  }
}
