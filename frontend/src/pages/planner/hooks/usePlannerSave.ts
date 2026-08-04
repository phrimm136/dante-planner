import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '@/lib/i18n'
import { useAuthQuery } from '@/shared/auth'
import { usePlannerStorage } from './usePlannerStorage'
import { usePlannerSyncAdapter } from './usePlannerSyncAdapter'
import { userPlannersQueryKeys } from './useMDUserPlannersData'
import { plannerQueryKeys } from '../lib/plannerQueryKeys'
import { useEGOGiftListData } from '@/pages/egoGift'
import { serializeSets } from '../schemas/PlannerSchemas'
import { queryClient } from '@/lib/queryClient'
import { AUTO_SAVE_DEBOUNCE_MS } from '@/lib/constants'
import { generateUUID } from '@/lib/uuid'
import { assertNever } from '@/lib/utils'
import { withRollback } from '@/lib/withRollback'
import {
  validatePlannerForDraftSave,
  validatePlannerForPublish,
  validateNoteSizes,
} from '../lib/plannerValidation'
import { toUserFriendlyError } from '../lib/plannerValidationErrors'
import {
  classifiedSaveError,
  classifySaveError,
  userFriendlyValidationError,
} from '../lib/plannerSaveErrors'
import type { SaveError } from '../lib/plannerSaveErrors'
import type { MDCategory, PlannerType } from '@/shared/gameData'
import type { SinnerEquipment, SkillEAState } from '../types/DeckTypes'
import type { FloorThemeSelection } from '@/pages/themePack'
import type { NoteContent } from '@/shared/noteEditor'
import type {
  SaveablePlanner,
  ConflictResolutionChoice,
  PlannerEditorConfig,
  PlannerContent,
  PlannerStatus,
  MDPlannerContent,
} from '../types/PlannerTypes'

export type { SaveError } from '../lib/plannerSaveErrors'

/**
 * SSR safety check
 */
const isClient = typeof window !== 'undefined'

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

/** Options for a single manual save. */
export interface SaveOptions {
  /** Drive the publication state instead of inheriting the hook's `published`. */
  published?: boolean
  /** Upload even when auto-sync is disabled. */
  forceSync?: boolean
}

/** Options for one pass through the save pipeline. */
interface PerformSaveOptions extends SaveOptions {
  /** Bypass the server's optimistic-lock check. */
  force?: boolean
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
  /** Why the last save failed, or null when it did not */
  error: SaveError | null
  /** Clear the current error */
  clearError: () => void
  /** Trigger manual save, returns true if succeeded. */
  save: (options?: SaveOptions) => Promise<boolean>
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
  /** Whether user is restricted (banned or timed out) - disables sync button */
  isRestricted: boolean
  /** Reason for restriction (ban or timeout reason) */
  restrictionReason: string | undefined
}

/** Everything a `SaveablePlanner` needs that is not derived from the editor state. */
interface SaveablePlannerInput {
  state: PlannerState
  plannerId: string
  deviceId: string
  schemaVersion: number
  contentVersion: number
  plannerType: PlannerType
  /** Original creation timestamp, or null for a planner being created now. */
  existingCreatedAt: string | null
  existingSyncVersion: number
  published: boolean
  status: PlannerStatus
}

/**
 * Serialize PlannerState to SaveablePlanner format
 */
function createSaveablePlanner(input: SaveablePlannerInput): SaveablePlanner {
  const { state } = input
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
  const serializableNotes: Record<
    string,
    { content: (typeof state.sectionNotes)[string]['content'] }
  > = {}
  for (const [key, note] of Object.entries(state.sectionNotes)) {
    serializableNotes[key] = { content: note.content }
  }

  return {
    metadata: {
      id: input.plannerId,
      title: state.title,
      status: input.status,
      schemaVersion: input.schemaVersion,
      contentVersion: input.contentVersion,
      plannerType: input.plannerType,
      syncVersion: input.existingSyncVersion,
      createdAt: input.existingCreatedAt ?? now,
      lastModifiedAt: now,
      savedAt: input.status === 'saved' ? now : null,
      published: input.published,
      deviceId: input.deviceId,
    },
    config: {
      type: input.plannerType,
      category: state.category,
    } as PlannerEditorConfig,
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
 * - Resolution via resolveConflict('overwrite' | 'discard' | 'both')
 *
 * @example
 * ```tsx
 * function PlannerPage() {
 *   const { isAutoSaving, isSaving, error, save, resolveConflict } = usePlannerSave({
 *     getState: () => storeApi.getState().getPlannerState(),
 *     subscribe: storeApi.subscribe,
 *     schemaVersion: 1,
 *     contentVersion: 6,
 *     plannerType: 'MIRROR_DUNGEON',
 *     onServerReload: (planner) => setState(deserializePlanner(planner)),
 *   })
 *
 *   return (
 *     <div>
 *       {isAutoSaving && <span>Saving...</span>}
 *       <button onClick={() => save()} disabled={isSaving}>Save</button>
 *       {error?.kind === 'conflict' && (
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

  const [error, setError] = useState<SaveError | null>(null)

  // Split adapters
  const storage = usePlannerStorage()
  const syncAdapter = usePlannerSyncAdapter()

  // EGO Gift data for affordability validation
  const { spec: egoGiftSpec, i18n: egoGiftI18n } = useEGOGiftListData()

  /**
   * Another surface (publish header, conflict resolution) may advance the server
   * version while this instance stays mounted; its cache write-through re-renders
   * this hook with a newer initialSyncVersion. Adoption is forward-only — a
   * lagging prop must never roll back a version this instance already holds, or
   * the next save would present a stale version and conflict (409).
   */
  const adoptSyncVersion = (): number => {
    if (initialSyncVersion !== undefined && initialSyncVersion > syncVersionRef.current) {
      syncVersionRef.current = initialSyncVersion
    }
    return syncVersionRef.current
  }

  const throwIfInvalid = (saveable: SaveablePlanner, state: PlannerState, strict: boolean) => {
    if (plannerType !== 'MIRROR_DUNGEON') return

    const content = saveable.content as MDPlannerContent

    const noteSizeError = validateNoteSizes(content.sectionNotes)
    if (noteSizeError) throw userFriendlyValidationError(noteSizeError)

    if (strict) {
      // Strict: title + theme packs required, full difficulty enforced
      const { isValid, errors } = validatePlannerForPublish(
        state.title,
        content,
        state.category,
        egoGiftSpec,
        egoGiftI18n,
      )
      if (!isValid) throw userFriendlyValidationError(toUserFriendlyError(errors[0]))
      return
    }

    // Non-strict: structural checks only, title/theme packs optional
    const validationError = validatePlannerForDraftSave(
      content,
      state.category,
      egoGiftSpec,
      egoGiftI18n,
    )
    if (validationError) throw userFriendlyValidationError(validationError)
  }

  /**
   * Core save logic for manual save
   * - Always saves to IndexedDB via SaveAdapter
   * - If authenticated AND syncEnabled, also syncs to server via SyncAdapter
   */
  const performSave = async (
    status: PlannerStatus,
    opts: PerformSaveOptions = {},
  ): Promise<boolean> => {
    if (!isClient) return false

    // Set createdAt on first save
    if (createdAtRef.current === null) {
      createdAtRef.current = new Date().toISOString()
    }

    // Get deviceId
    const deviceId = await storage.getOrCreateDeviceId()
    if (!deviceId) return false

    const currentState = getState()
    const isCurrentlyPublished = opts.published ?? published

    const saveable = createSaveablePlanner({
      state: currentState,
      plannerId,
      deviceId,
      schemaVersion,
      contentVersion,
      plannerType,
      existingCreatedAt: createdAtRef.current,
      existingSyncVersion: adoptSyncVersion(),
      published: isCurrentlyPublished,
      status,
    })

    // Two-tier validation for MD planners (non-strict for draft, strict for published)
    throwIfInvalid(saveable, currentState, isCurrentlyPublished)

    // If authenticated AND (syncEnabled OR forceSync), sync to server first to get new syncVersion
    let didSync = false
    if (isAuthenticated && (syncEnabled || opts.forceSync)) {
      const synced = await syncAdapter.syncToServer(saveable, opts.force)
      didSync = true

      // Update sync version from server response
      if (synced.metadata.syncVersion) {
        syncVersionRef.current = synced.metadata.syncVersion
        saveable.metadata.syncVersion = synced.metadata.syncVersion
      }
    }

    // Save to IndexedDB (with updated syncVersion if synced)
    const localResult = await storage.saveToLocal(saveable)
    if (!localResult.success) {
      throw Object.assign(new Error(`Local save failed: ${localResult.errorCode}`), {
        code: localResult.errorCode ?? 'saveFailed',
      })
    }

    // Write-through: every mounted consumer (publish header, list pages) must
    // see the server-assigned version without relying on an SSE echo — the
    // originating device is excluded from its own events by design.
    if (didSync) {
      void queryClient.invalidateQueries({
        queryKey: plannerQueryKeys.detail(plannerId),
      })
      void queryClient.invalidateQueries({
        queryKey: userPlannersQueryKeys.all,
      })
    }

    return true
  }

  /** Adopt the just-saved state as both dirty-check baselines. */
  const markSaved = () => {
    const comparable = stateToComparableString(getState())
    previousStateRef.current = comparable
    lastSyncedStateRef.current = comparable
    setLastSavedAt(new Date().toISOString())
  }

  /**
   * Debounced auto-save.
   * Auto-saves ALWAYS go to IndexedDB only (local-first architecture);
   * syncing to the server is manual-save only.
   */
  const autoSave = async () => {
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
      const deviceId = await storage.getOrCreateDeviceId()
      if (!deviceId) return

      const saveable = createSaveablePlanner({
        state: currentState,
        plannerId,
        deviceId,
        schemaVersion,
        contentVersion,
        plannerType,
        existingCreatedAt: createdAtRef.current,
        existingSyncVersion: adoptSyncVersion(),
        published,
        status: 'draft',
      })

      // Save to IndexedDB only via SaveAdapter (never server for auto-save)
      const result = await storage.saveToLocal(saveable)
      if (!result.success) {
        throw Object.assign(new Error(`Auto-save failed: ${result.errorCode}`), {
          code: result.errorCode ?? 'saveFailed',
        })
      }

      previousStateRef.current = currentStateString
      setLastSavedAt(new Date().toISOString())
    } catch (autoSaveError: unknown) {
      setError(classifySaveError(autoSaveError))
    } finally {
      setIsAutoSaving(false)
    }
  }

  /** Drop a pending auto-save so it cannot land on top of a manual write. */
  const cancelPendingAutoSave = () => {
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }

  /**
   * Manual save function
   * @returns true if save succeeded, false if it failed
   */
  const save = async (saveOptions?: SaveOptions): Promise<boolean> => {
    if (!isClient) return false

    // Prevents a race where auto-save overwrites with a stale syncVersion
    cancelPendingAutoSave()

    setIsSaving(true)

    try {
      await performSave('saved', {
        published: saveOptions?.published,
        forceSync: saveOptions?.forceSync,
      })
      markSaved()
      return true
    } catch (saveFailure: unknown) {
      setError(classifySaveError(saveFailure))
      return false
    } finally {
      setIsSaving(false)
    }
  }

  /** Keep Both: fork local changes to a new planner, revert the original to the server version. */
  const forkLocalChanges = async () => {
    const newPlannerId = generateUUID()
    const deviceId = await storage.getOrCreateDeviceId()

    const currentState = getState()
    const baseTitle = currentState.title || t('pages.plannerMD.untitled', 'Untitled')
    const copyTitle = t('pages.plannerMD.conflict.copySuffix', '{{title}} (Copy)', {
      title: baseTitle,
    })
    const newPlanner = createSaveablePlanner({
      state: { ...currentState, title: copyTitle },
      plannerId: newPlannerId,
      deviceId,
      schemaVersion,
      contentVersion,
      plannerType,
      existingCreatedAt: null,
      existingSyncVersion: 1,
      published: false,
      status: 'saved',
    })

    // The copy carries the same content as currentState, so the draft rules apply.
    if (plannerType === 'MIRROR_DUNGEON') {
      const content = newPlanner.content as MDPlannerContent
      const noteSizeError = validateNoteSizes(content.sectionNotes)
      if (noteSizeError) throw userFriendlyValidationError(noteSizeError)
      if (egoGiftSpec) {
        const validationError = validatePlannerForDraftSave(
          content,
          currentState.category,
          egoGiftSpec,
          egoGiftI18n,
        )
        if (validationError) throw userFriendlyValidationError(validationError)
      }
    }

    await withRollback({
      create: () => storage.saveToLocal(newPlanner).then(() => undefined),
      rollback: () => storage.deleteFromLocal(newPlannerId).then(() => undefined),
      rest: async () => {
        // The user pressed save intentionally, so the copy syncs regardless of syncEnabled.
        if (isAuthenticated) {
          await syncAdapter.syncToServer(newPlanner)
        }

        await revertToServerVersion()

        if (onKeepBothCreated) {
          onKeepBothCreated(newPlannerId)
        }
      },
    })
  }

  /**
   * The server's copy of this planner.
   *
   * @throws the classified save error when the server copy cannot be read — a
   *         conflict resolution that proceeds without it either overwrites the
   *         server from a stale version or reports a discard that never happened.
   */
  const requireServerVersion = async (): Promise<SaveablePlanner> => {
    const fetched = await syncAdapter.fetchFromServer(plannerId)
    if (!fetched.ok) throw classifiedSaveError(fetched.error)
    return fetched.value
  }

  /** Take the server's current sync version without touching local content. */
  const adoptServerSyncVersion = async () => {
    const serverPlanner = await requireServerVersion()
    syncVersionRef.current = serverPlanner.metadata.syncVersion
  }

  /** Discard local changes: adopt the server version as both the local copy and editor state. */
  const revertToServerVersion = async () => {
    const serverPlanner = await requireServerVersion()

    syncVersionRef.current = serverPlanner.metadata.syncVersion
    const localResult = await storage.saveToLocal(serverPlanner)
    if (!localResult.success) {
      throw Object.assign(new Error(`Local save failed: ${localResult.errorCode}`), {
        code: localResult.errorCode ?? 'saveFailed',
      })
    }

    if (onServerReload) {
      onServerReload(serverPlanner)
    }
  }

  /**
   * Resolve a conflict
   * @returns true if resolution succeeded, false if it failed
   */
  const resolveConflict = async (choice: ConflictResolutionChoice): Promise<boolean> => {
    if (error?.kind !== 'conflict') return false

    setIsSaving(true)

    // Prevents a race where the timer fires with stale state before React re-renders
    cancelPendingAutoSave()

    try {
      switch (choice) {
        case 'overwrite':
          // A conflict that reported no server version leaves the local syncVersion
          // unanchored, so read the server's back before writing over it.
          if (error.state.serverVersion == null) {
            await adoptServerSyncVersion()
          }
          // force bypasses the version check; forceSync uploads even when auto-sync is off
          await performSave('saved', { force: true, forceSync: true })
          markSaved()
          break
        case 'both':
          await forkLocalChanges()
          break
        case 'discard':
          await revertToServerVersion()
          break
        default:
          assertNever(choice)
      }

      // Clear conflict state only on success
      setError(null)
      return true
    } catch (resolutionError: unknown) {
      const classified = classifySaveError(resolutionError)
      // A resolution that failed leaves the conflict unresolved, so the conflict
      // error stays put and the dialog with it. Only a newer conflict displaces it.
      if (classified.kind === 'conflict') {
        setError(classified)
      }
      return false
    } finally {
      setIsSaving(false)
    }
  }

  /**
   * Clear error state
   */
  const clearError = () => {
    setError(null)
  }

  // The subscription must survive re-renders, so the effect reads the auto-save
  // through a ref instead of depending on a closure that changes every render.
  const autoSaveRef = useRef(autoSave)
  useEffect(() => {
    autoSaveRef.current = autoSave
  })

  // Debounced auto-save driven by store subscription rather than a state
  // dependency, so the parent does not re-render on every state change.
  useEffect(() => {
    if (!isClient) return

    const unsubscribe = subscribe(() => {
      if (timerRef.current) {
        clearTimeout(timerRef.current)
      }

      timerRef.current = setTimeout(() => {
        void autoSaveRef.current()
      }, AUTO_SAVE_DEBOUNCE_MS)
    })

    return () => {
      unsubscribe()
      if (timerRef.current) {
        clearTimeout(timerRef.current)
      }
    }
  }, [subscribe])

  // Dirty flags compare the live state against the two save baselines. An
  // uninitialized baseline ('') means there is nothing to compare against yet.
  const currentComparable = stateToComparableString(getState())
  const hasUnsyncedChanges =
    lastSyncedStateRef.current !== '' && currentComparable !== lastSyncedStateRef.current
  const hasLocalUnsavedChanges =
    previousStateRef.current !== '' && currentComparable !== previousStateRef.current

  const isRestricted = user?.isBanned === true || user?.isTimedOut === true
  const restrictionReason = !isRestricted
    ? undefined
    : user?.isBanned
      ? user.banReason || i18n.t('moderation.bannedNoReason', { ns: 'common' })
      : user?.timeoutReason || i18n.t('moderation.timedOutNoReason', { ns: 'common' })

  return {
    plannerId,
    isAutoSaving,
    isSaving,
    error,
    clearError,
    save,
    resolveConflict,
    syncVersion: Math.max(syncVersionRef.current, initialSyncVersion ?? 0),
    hasUnsyncedChanges,
    hasLocalUnsavedChanges,
    lastSavedAt,
    isRestricted,
    restrictionReason,
  }
}
