import { useEffect, useRef, useState, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuthQuery } from './useAuthQuery'
import { usePlannerSaveAdapter } from './usePlannerSaveAdapter'
import { usePlannerSyncAdapter } from './usePlannerSyncAdapter'
import { useEGOGiftListData } from './useEGOGiftListData'
import { serializeSets } from '@/schemas/PlannerSchemas'
import { ConflictError } from '@/lib/api'
import { AUTO_SAVE_DEBOUNCE_MS } from '@/lib/constants'
import { validatePlannerUserFriendly } from '@/lib/plannerHelpers'
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
  /**
   * Getter function to retrieve current planner state imperatively.
   * Using a getter instead of state directly prevents parent component
   * from subscribing to all state changes.
   */
  getState: () => PlannerState
  /**
   * Subscribe function from Zustand store for detecting state changes.
   * Used to trigger auto-save debounce without causing component re-renders.
   */
  subscribe: (listener: () => void) => () => void
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
  /** Optional initial savedAt timestamp (for editing, to show sync status) */
  initialSavedAt?: string
  /** Current published state (from component) */
  published?: boolean
  /** Callback when server version is reloaded (on discard) */
  onServerReload?: (planner: SaveablePlanner) => void
  /** Callback when "Keep Both" creates a new planner (for navigation) */
  onKeepBothCreated?: (newPlannerId: string) => void
  /** Whether sync to server is enabled (from user settings). Defaults to false if not set. */
  syncEnabled?: boolean
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
  /** I18n key for user-friendly validation errors (e.g., 'pages.plannerMD.publish.missingTitle') */
  errorI18nKey: string | null
  /** I18n params for user-friendly validation errors (e.g., { gifts: 'Gift Name 1, Gift Name 2' }) */
  errorI18nParams: Record<string, string> | null
  /** Conflict info when errorCode === 'conflict' */
  conflictState: ConflictState | null
  /** Clear the current error */
  clearError: () => void
  /** Trigger manual save, returns true if succeeded. Pass published override for togglePublish. */
  save: (options?: { published?: boolean }) => Promise<boolean>
  /** Resolve a conflict (overwrite local or discard and reload), returns true if succeeded */
  resolveConflict: (choice: ConflictResolutionChoice) => Promise<boolean>
  /** Current sync version (for debugging) */
  syncVersion: number
  /** Whether there are changes not yet synced to server */
  hasUnsyncedChanges: boolean
  /** Whether there are changes not yet auto-saved to IndexedDB (for beforeunload warning) */
  hasLocalUnsavedChanges: boolean
  /** Last synced timestamp (ISO 8601, null if never synced) */
  lastSavedAt: string | null
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
  published: boolean,
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
      title: state.title,
      status,
      schemaVersion,
      contentVersion,
      plannerType,
      syncVersion: existingSyncVersion,
      createdAt: existingCreatedAt ?? now,
      lastModifiedAt: now,
      savedAt: status === 'saved' ? now : null,
      published,
      deviceId,
    },
    config: {
      type: plannerType,
      category: state.category,
    } as PlannerConfig,
    content: {
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
  const { t } = useTranslation('planner')
  const {
    getState,
    subscribe,
    schemaVersion,
    contentVersion,
    plannerType,
    initialPlannerId,
    initialSyncVersion,
    initialSavedAt,
    published = false,
    onServerReload,
    onKeepBothCreated,
    syncEnabled = false, // Default to false - user must explicitly enable sync
  } = options

  // Auth state
  const { data: user } = useAuthQuery()
  const isAuthenticated = !!user

  // Planner ID - create once and persist
  const [plannerId] = useState<string>(() => initialPlannerId ?? generateUUID())

  // Saving state indicators
  const [isAutoSaving, setIsAutoSaving] = useState(false)
  const [isSaving, setIsSaving] = useState(false)
  const [lastSavedAt, setLastSavedAt] = useState<string | null>(initialSavedAt ?? null)

  // Debounce timer ref
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Previous state for dirty checking - empty string means "not yet initialized"
  const previousStateRef = useRef<string>('')

  // Last synced state for beforeunload warning detection
  const lastSyncedStateRef = useRef<string>('')

  // Track the original createdAt timestamp
  const createdAtRef = useRef<string | null>(null)

  // Track sync version for optimistic locking
  const syncVersionRef = useRef<number>(initialSyncVersion ?? 1)

  // Error state
  const [errorCode, setErrorCode] = useState<SaveErrorCode>(null)
  const [errorI18nKey, setErrorI18nKey] = useState<string | null>(null)
  const [errorI18nParams, setErrorI18nParams] = useState<Record<string, string> | null>(null)
  const [conflictState, setConflictState] = useState<ConflictState | null>(null)

  // Split adapters
  const saveAdapter = usePlannerSaveAdapter()
  const syncAdapter = usePlannerSyncAdapter()

  // EGO Gift data for affordability validation
  const { spec: egoGiftSpec, i18n: egoGiftI18n } = useEGOGiftListData()

  /**
   * Core save logic for manual save
   * - Always saves to IndexedDB via SaveAdapter
   * - If authenticated AND syncEnabled, also syncs to server via SyncAdapter
   */
  const performSave = useCallback(async (status: 'draft' | 'saved', force?: boolean, publishedOverride?: boolean): Promise<boolean> => {
    if (!isClient) return false

    // Set createdAt on first save
    if (createdAtRef.current === null) {
      createdAtRef.current = new Date().toISOString()
    }

    // Get deviceId
    const deviceId = await saveAdapter.getOrCreateDeviceId()
    if (!deviceId) return false

    const currentState = getState()
    const isCurrentlyPublished = publishedOverride ?? published

    const saveable = createSaveablePlanner(
      currentState,
      plannerId,
      deviceId,
      schemaVersion,
      contentVersion,
      plannerType,
      createdAtRef.current,
      syncVersionRef.current,
      isCurrentlyPublished,
      status
    )

    // User-friendly validation for published planners (MD only)
    if (isCurrentlyPublished && plannerType === 'MIRROR_DUNGEON') {
      const validationError = validatePlannerUserFriendly(
        currentState.title,
        currentState.floorSelections,
        currentState.category,
        egoGiftSpec,
        egoGiftI18n
      )
      if (validationError) {
        // Serialize error info as JSON for later parsing
        const errorMessage = JSON.stringify({ key: validationError.key, params: validationError.params })
        const error = new Error(errorMessage)
        ;(error as Error & { code: string; i18nKey: string; i18nParams?: Record<string, string> }).code = 'userFriendlyValidation'
        ;(error as Error & { code: string; i18nKey: string; i18nParams?: Record<string, string> }).i18nKey = validationError.key
        ;(error as Error & { code: string; i18nKey: string; i18nParams?: Record<string, string> }).i18nParams = validationError.params
        throw error
      }
    }

    // If authenticated AND syncEnabled, sync to server first to get new syncVersion
    if (isAuthenticated && syncEnabled) {
      const synced = await syncAdapter.syncToServer(saveable, force)

      // Update sync version from server response
      if (synced.metadata.syncVersion) {
        syncVersionRef.current = synced.metadata.syncVersion
        saveable.metadata.syncVersion = synced.metadata.syncVersion
      }
    }

    // Save to IndexedDB (with updated syncVersion if synced)
    const localResult = await saveAdapter.saveToLocal(saveable)
    if (!localResult.success) {
      const error = new Error(`Local save failed: ${localResult.errorCode}`)
      ;(error as Error & { code: string }).code = localResult.errorCode ?? 'saveFailed'
      throw error
    }

    return true
  }, [getState, plannerId, saveAdapter, syncAdapter, isAuthenticated, syncEnabled, schemaVersion, contentVersion, plannerType, published])

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
      const errorWithCode = error as Error & { code?: string; i18nKey?: string; i18nParams?: Record<string, string> }

      if (errorWithCode.code === 'quotaExceeded' ||
          error.message.includes('QuotaExceeded') ||
          error.message.includes('quota')) {
        setErrorCode('quotaExceeded')
        console.error('Save failed (quota exceeded):', error.message)
        return
      }

      if (errorWithCode.code === 'userFriendlyValidation') {
        // User-friendly validation error - extract key and params
        console.error('Save failed (user validation):', error.message)
        setErrorCode('saveFailed')
        setErrorI18nKey(errorWithCode.i18nKey ?? null)
        setErrorI18nParams(errorWithCode.i18nParams ?? null)
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
   * Never syncs to server - that's only for manual save
   */
  const debouncedSave = useCallback(async () => {
    // CRITICAL: Prevent race condition with manual save
    if (isSaving) return

    const currentState = getState()
    const currentStateString = stateToComparableString(currentState)

    // First run: initialize baseline and skip save (handles planner loading in edit mode)
    if (previousStateRef.current === '') {
      previousStateRef.current = currentStateString
      lastSyncedStateRef.current = currentStateString
      return
    }

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
      const deviceId = await saveAdapter.getOrCreateDeviceId()
      if (!deviceId) return

      // Create saveable planner
      const saveable = createSaveablePlanner(
        currentState,
        plannerId,
        deviceId,
        schemaVersion,
        contentVersion,
        plannerType,
        createdAtRef.current,
        syncVersionRef.current,
        published,
        'draft'
      )

      // Save to IndexedDB only via SaveAdapter (never server for auto-save)
      const result = await saveAdapter.saveToLocal(saveable)
      if (!result.success) {
        const error = new Error(`Auto-save failed: ${result.errorCode}`)
        ;(error as Error & { code: string }).code = result.errorCode ?? 'saveFailed'
        throw error
      }

      previousStateRef.current = currentStateString
      setLastSavedAt(new Date().toISOString())
    } catch (error: unknown) {
      handleSaveError(error)
    } finally {
      setIsAutoSaving(false)
    }
  }, [getState, isSaving, saveAdapter, plannerId, schemaVersion, contentVersion, plannerType, published, handleSaveError])

  /**
   * Manual save function
   * @returns true if save succeeded, false if it failed
   */
  const save = useCallback(async (options?: { published?: boolean }): Promise<boolean> => {
    if (!isClient) return false

    // Clear pending auto-save timer BEFORE any operations
    // Prevents race condition where auto-save overwrites with stale syncVersion
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }

    setIsSaving(true)

    try {
      await performSave('saved', false, options?.published)
      const currentStateString = stateToComparableString(getState())
      const now = new Date().toISOString()
      previousStateRef.current = currentStateString
      lastSyncedStateRef.current = currentStateString
      setLastSavedAt(now)
      return true
    } catch (error: unknown) {
      handleSaveError(error)
      return false
    } finally {
      setIsSaving(false)
    }
  }, [getState, performSave, handleSaveError])

  /**
   * Resolve a conflict
   * - 'overwrite': Force-save with force=true flag
   * - 'discard': Reload from server and update local state
   * @returns true if resolution succeeded, false if it failed
   */
  const resolveConflict = useCallback(async (choice: ConflictResolutionChoice): Promise<boolean> => {
    if (!conflictState) return false

    setIsSaving(true)

    // Clear pending auto-save timer BEFORE any state modifications
    // Prevents race condition where timer fires with stale state before React re-renders
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }

    try {
      if (choice === 'overwrite') {
        // Use force=true to bypass version check
        await performSave('saved', true)
        const currentStateString = stateToComparableString(getState())
        previousStateRef.current = currentStateString
        lastSyncedStateRef.current = currentStateString
        setLastSavedAt(new Date().toISOString())
      } else if (choice === 'both') {
        // Keep Both: fork local changes to new planner, revert original to server
        const newPlannerId = generateUUID()
        const deviceId = await saveAdapter.getOrCreateDeviceId()

        // 1. Create a copy with modified title containing local changes
        const currentState = getState()
        const baseTitle = currentState.title || t('pages.plannerMD.untitled', 'Untitled')
        const copyTitle = t('pages.plannerMD.conflict.copySuffix', '{{title}} (Copy)', { title: baseTitle })
        const copyState = { ...currentState, title: copyTitle }
        const newPlanner = createSaveablePlanner(
          copyState,
          newPlannerId,
          deviceId,
          schemaVersion,
          contentVersion,
          plannerType,
          null, // new planner, no existing createdAt
          1, // initial syncVersion
          false, // new copy is not published
          'saved' // mark as saved since we're syncing immediately
        )

        // Track whether copy was saved for cleanup on failure
        let copySavedToLocal = false

        try {
          // 2. Save new planner to local storage
          await saveAdapter.saveToLocal(newPlanner)
          copySavedToLocal = true

          // 3. Sync the new planner to server immediately (user pressed save intentionally)
          if (isAuthenticated && syncEnabled) {
            await syncAdapter.syncToServer(newPlanner)
          }

          // 4. Revert original planner to server version (same as discard)
          const serverPlanner = await syncAdapter.fetchFromServer(plannerId)
          if (serverPlanner) {
            syncVersionRef.current = serverPlanner.metadata.syncVersion
            await saveAdapter.saveToLocal(serverPlanner)

            if (onServerReload) {
              onServerReload(serverPlanner)
            }
          }

          // 5. Navigate to the new planner
          if (onKeepBothCreated) {
            onKeepBothCreated(newPlannerId)
          }
        } catch (keepBothError) {
          // Cleanup: attempt to delete the copy if it was saved
          if (copySavedToLocal) {
            try {
              await saveAdapter.deleteFromLocal(newPlannerId)
            } catch (cleanupError) {
              console.error('Failed to cleanup copy after keepBoth error:', cleanupError)
            }
          }
          throw keepBothError
        }
      } else {
        // Discard: reload from server
        const serverPlanner = await syncAdapter.fetchFromServer(plannerId)
        if (serverPlanner) {
          syncVersionRef.current = serverPlanner.metadata.syncVersion

          // Also update local copy with server version
          await saveAdapter.saveToLocal(serverPlanner)

          if (onServerReload) {
            onServerReload(serverPlanner)
          }
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
  }, [conflictState, getState, performSave, syncAdapter, saveAdapter, plannerId, onServerReload, onKeepBothCreated, handleSaveError, schemaVersion, contentVersion, plannerType, t, isAuthenticated, syncEnabled])

  /**
   * Clear error state
   */
  const clearError = useCallback(() => {
    setErrorCode(null)
    setErrorI18nKey(null)
    setErrorI18nParams(null)
    setConflictState(null)
  }, [])

  // Effect for debounced auto-save - uses store subscription instead of state dependency
  // This prevents the parent component from re-rendering on every state change
  useEffect(() => {
    if (!isClient) return

    // Subscribe to store changes and trigger debounced save
    const unsubscribe = subscribe(() => {
      // Clear existing timer
      if (timerRef.current) {
        clearTimeout(timerRef.current)
      }

      // Set new debounce timer
      timerRef.current = setTimeout(() => {
        debouncedSave()
      }, AUTO_SAVE_DEBOUNCE_MS)
    })

    // Cleanup
    return () => {
      unsubscribe()
      if (timerRef.current) {
        clearTimeout(timerRef.current)
      }
    }
  }, [subscribe, debouncedSave])

  // Check if there are unsynced changes (not synced to server)
  // Return false if not yet initialized (no baseline to compare against)
  // Note: These use getState() so they reflect current state when accessed
  const currentState = getState()
  const hasUnsyncedChanges = lastSyncedStateRef.current !== '' &&
    stateToComparableString(currentState) !== lastSyncedStateRef.current

  // Check if there are unsaved local changes (not yet auto-saved to IndexedDB)
  // Return false if not yet initialized (no baseline to compare against)
  const hasLocalUnsavedChanges = previousStateRef.current !== '' &&
    stateToComparableString(currentState) !== previousStateRef.current

  return {
    plannerId,
    isAutoSaving,
    isSaving,
    errorCode,
    errorI18nKey,
    errorI18nParams,
    conflictState,
    clearError,
    save,
    resolveConflict,
    syncVersion: syncVersionRef.current,
    hasUnsyncedChanges,
    hasLocalUnsavedChanges,
    lastSavedAt,
  }
}
