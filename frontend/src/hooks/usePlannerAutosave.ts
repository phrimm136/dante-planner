import { useEffect, useRef, useState, useCallback } from 'react'
import { usePlannerStorage } from './usePlannerStorage'
import { usePlannerStorageAdapter } from './usePlannerStorageAdapter'
import { serializeSets } from '@/schemas/PlannerSchemas'
import { AUTO_SAVE_DEBOUNCE_MS, PLANNER_SCHEMA_VERSION } from '@/lib/constants'
import type { MDCategory } from '@/lib/constants'
import type { SinnerEquipment, SkillEAState } from '@/types/DeckTypes'
import type { FloorThemeSelection } from '@/types/ThemePackTypes'
import type { NoteContent } from '@/types/NoteEditorTypes'
import type { SaveablePlanner } from '@/types/PlannerTypes'

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
 * Autosave error codes for i18n translation
 * Components should use these codes to display localized error messages
 */
export type AutosaveErrorCode =
  | 'conflict'
  | 'saveFailed'
  | null

/**
 * Return type for usePlannerAutosave hook
 */
export interface PlannerAutosaveResult {
  /** Current planner ID (creates new if none) */
  plannerId: string
  /** Whether autosave is in progress */
  isAutoSaving: boolean
  /** Error code if last save failed (for i18n translation) */
  errorCode: AutosaveErrorCode
  /** Clear the current error */
  clearError: () => void
}

/**
 * Serialize PlannerState to SaveablePlanner format
 * @param existingCreatedAt - Original createdAt to preserve, or null for new planners
 * @param existingSyncVersion - Current sync version for optimistic locking
 */
function createSaveablePlanner(
  state: PlannerState,
  plannerId: string,
  deviceId: string,
  existingCreatedAt: string | null,
  existingSyncVersion: number = 1
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

  // Convert NoteContent to SerializableNoteContent (same structure, just type assertion)
  const serializableNotes: Record<string, { content: typeof state.sectionNotes[string]['content'] }> = {}
  for (const [key, note] of Object.entries(state.sectionNotes)) {
    serializableNotes[key] = { content: note.content }
  }

  return {
    metadata: {
      id: plannerId,
      status: 'draft',
      version: PLANNER_SCHEMA_VERSION,
      syncVersion: existingSyncVersion,
      createdAt: existingCreatedAt ?? now, // Preserve original or set new
      lastModifiedAt: now,
      savedAt: null,
      userId: null,
      deviceId,
    },
    content: {
      title: state.title,
      category: state.category,
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
    },
  }
}

/**
 * Deep comparison for dirty state detection
 * Compares JSON stringified versions of state
 */
function stateToComparableString(state: PlannerState): string {
  // Convert to serializable format for comparison
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
 * Hook for auto-saving planner state as draft
 *
 * Debounces state changes and saves after AUTO_SAVE_DEBOUNCE_MS (2000ms).
 * Routes to server (authenticated) or IndexedDB (guest) via adapter.
 * Tracks dirty state to avoid unnecessary saves.
 * Creates new UUID if no planner ID exists.
 * Handles 409 conflict errors by showing toast and refreshing.
 *
 * @param state - Current planner state (with Set types)
 * @param initialPlannerId - Optional existing planner ID (for editing existing planners)
 * @param initialSyncVersion - Optional initial sync version (for editing existing planners)
 * @returns { plannerId, isAutoSaving }
 *
 * @example
 * ```tsx
 * function PlannerPage() {
 *   const [state, setState] = useState<PlannerState>(initialState)
 *   const { plannerId, isAutoSaving } = usePlannerAutosave(state)
 *
 *   return (
 *     <div>
 *       {isAutoSaving && <span>Saving...</span>}
 *       <PlannerForm state={state} onChange={setState} />
 *     </div>
 *   )
 * }
 * ```
 */
export function usePlannerAutosave(
  state: PlannerState,
  initialPlannerId?: string,
  initialSyncVersion?: number
): PlannerAutosaveResult {
  // Planner ID - create once and persist
  const [plannerId] = useState<string>(() => initialPlannerId ?? generateUUID())

  // Saving state indicator
  const [isAutoSaving, setIsAutoSaving] = useState(false)

  // Debounce timer ref
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // Previous state for dirty checking
  const previousStateRef = useRef<string>('')

  // Flag to prevent save on first render
  const isInitialRenderRef = useRef(true)

  // Track the original createdAt timestamp (set once on first save)
  const createdAtRef = useRef<string | null>(null)

  // Track sync version for optimistic locking (server mode)
  const syncVersionRef = useRef<number>(initialSyncVersion ?? 1)

  // Error state for i18n-compatible error handling
  const [errorCode, setErrorCode] = useState<AutosaveErrorCode>(null)

  // Storage adapter (routes to server or IndexedDB based on auth)
  const adapter = usePlannerStorageAdapter()

  // Local storage for guest-specific operations (deviceId, draft limits)
  const localStorage = usePlannerStorage()

  // Debounced save function
  const debouncedSave = useCallback(async () => {
    if (!isClient) return

    const currentStateString = stateToComparableString(state)

    // Skip if state hasn't changed
    if (currentStateString === previousStateRef.current) {
      return
    }

    setIsAutoSaving(true)

    try {
      // Set createdAt on first save attempt (not after success)
      if (createdAtRef.current === null) {
        createdAtRef.current = new Date().toISOString()
      }

      // For guest mode, we need deviceId. For server mode, backend handles it.
      let deviceId = ''
      if (!adapter.isAuthenticated) {
        deviceId = await localStorage.getOrCreateDeviceId()
        if (!deviceId) {
          setIsAutoSaving(false)
          return
        }
      }

      const saveable = createSaveablePlanner(
        state,
        plannerId,
        deviceId,
        createdAtRef.current,
        syncVersionRef.current
      )

      // Save via adapter (routes to server or IndexedDB)
      const saved = await adapter.savePlanner(saveable)

      // Update sync version from server response (if authenticated)
      if (adapter.isAuthenticated && saved.metadata.syncVersion) {
        syncVersionRef.current = saved.metadata.syncVersion
      }

      // Guest mode: track current draft and enforce limits
      if (!adapter.isAuthenticated) {
        await localStorage.setCurrentDraftId(plannerId)
        await localStorage.enforceGuestDraftLimit()
      }

      // Update previous state after successful save
      previousStateRef.current = currentStateString
    } catch (error: unknown) {
      // Handle 409 conflict error (version mismatch)
      if (error instanceof Error && error.message.includes('409')) {
        setErrorCode('conflict')
      } else {
        setErrorCode('saveFailed')
        console.error('Auto-save failed:', error)
      }
    } finally {
      setIsAutoSaving(false)
    }
  }, [state, plannerId, adapter, localStorage])

  // Effect for debounced auto-save
  useEffect(() => {
    if (!isClient) return

    // Skip first render to avoid saving initial state immediately
    if (isInitialRenderRef.current) {
      isInitialRenderRef.current = false
      // Initialize previous state on first render
      previousStateRef.current = stateToComparableString(state)
      return
    }

    // Clear existing timer
    if (timerRef.current) {
      clearTimeout(timerRef.current)
    }

    // Set new debounce timer
    timerRef.current = setTimeout(() => {
      debouncedSave()
    }, AUTO_SAVE_DEBOUNCE_MS)

    // Cleanup on unmount or state change
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current)
      }
    }
  }, [state, debouncedSave])

  /**
   * Clear the current error (for dismissing error notifications)
   */
  const clearError = useCallback(() => {
    setErrorCode(null)
  }, [])

  return {
    plannerId,
    isAutoSaving,
    errorCode,
    clearError,
  }
}
