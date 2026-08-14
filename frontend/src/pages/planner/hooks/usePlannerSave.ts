import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '@/lib/i18n'
import { useAuthQuery, authQueryKeys } from '@/shared/auth'
import { usePlannerStorage } from './usePlannerStorage'
import { usePlannerSyncAdapter } from './usePlannerSyncAdapter'
import { userPlannersQueryKeys } from './useMDUserPlannersData'
import { plannerQueryKeys } from '../lib/plannerQueryKeys'
import { useEGOGiftListData } from '@/pages/egoGift'
import { serializeSets } from '../schemas/PlannerSchemas'
import { isMDPlanner } from '../types/PlannerTypes'
import { queryClient } from '@/lib/queryClient'
import { AUTO_SAVE_DEBOUNCE_MS, INITIAL_SYNC_VERSION } from '@/lib/constants'
import { generateUUID } from '@/lib/uuid'
import { assertNever } from '@/lib/utils'
import { withRollback } from '@/lib/withRollback'
import {
  validatePlannerForDraftSave,
  validatePlannerForPublish,
  validateNoteSizes,
} from '../lib/plannerValidation'
import { toUserFriendlyError } from '../lib/plannerValidationErrors'
import { classifyAppError, validationAppError } from '@/lib/apiErrorClassifier'
import { planConflictResolution } from '../lib/conflictChoice'
import { ok, err } from '@/lib/result'
import type { Result } from '@/lib/result'
import type { ConflictEffect, ConflictForkMetadata } from '../lib/conflictChoice'
import type { AcknowledgedPlanner } from './usePlannerSyncAdapter'
import type { AppError } from '@/lib/apiErrorClassifier'
import type { MDCategory, RRCategory, PlannerType } from '@/shared/gameData'
import type { SinnerEquipment, SkillEAState } from '../types/DeckTypes'
import type { FloorThemeSelection } from '@/pages/themePack'
import type { NoteContent } from '@/shared/noteEditor'
import type {
  SaveablePlanner,
  ConflictResolutionChoice,
  PlannerStatus,
  MDPlannerContent,
  ServerAck,
} from '../types/PlannerTypes'

/** Planner validators name their keys inside the planner namespace. */
function plannerValidationError(friendly: { key: string; params?: Record<string, string> }) {
  return validationAppError({ key: `planner:${friendly.key}`, params: friendly.params })
}

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
  /** Reload the editor from the server copy; returns whether it was adopted. */
  onServerReload?: (planner: SaveablePlanner) => boolean
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

/**
 * How far a save pushes against the server, in widening order.
 *
 * - `syncIfEnabled` uploads only when the user turned sync on.
 * - `forceSync` uploads whatever that setting says.
 * - `forcePush` also bypasses the server's optimistic-lock check.
 */
type SaveMode = 'syncIfEnabled' | 'forceSync' | 'forcePush'

/** Options for one pass through the save pipeline. */
interface PerformSaveOptions {
  /** Drive the publication state instead of inheriting the hook's `published`. */
  published?: boolean
  mode: SaveMode
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
  error: AppError | null
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
  /**
   * Whether a store change has arrived since the last local write, read at call
   * time. Stable across renders, so a listener registered once on mount keeps
   * seeing current dirtiness.
   */
  isDirty: () => boolean
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

  const metadata = {
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
  }

  const content: MDPlannerContent = {
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
  }

  if (input.plannerType === 'MIRROR_DUNGEON') {
    return { metadata, config: { type: input.plannerType, category: state.category }, content }
  }

  // The editor state carries an MD category whatever the planner type is.
  return {
    metadata,
    config: { type: input.plannerType, category: state.category as unknown as RRCategory },
    content,
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

  // The autosave the debounce runs, and the run already under way. Both are refs
  // so the subscription never has to re-subscribe to see the current one.
  const autoSaveRef = useRef<(flushing?: boolean) => Promise<void>>(async () => {})
  const inFlightRef = useRef<Promise<void>>(Promise.resolve())

  // Whether a manual save owns the write right now. The state above cannot answer
  // that after unmount: nothing re-renders this hook again, so every closure keeps
  // whatever it captured while the save was still running.
  const isSavingRef = useRef(false)

  // How many nested owners hold that write.
  const saveHoldsRef = useRef(0)

  // A teardown flush that arrives mid-save, waiting for that save to finish.
  const pendingFlushRef = useRef(false)

  // Set once the subscription is torn down, so no timer outlives the editor.
  const unmountedRef = useRef(false)

  // Read by isDirty, which must stay stable for a listener registered once.
  const getStateRef = useRef(options.getState)

  /** Arm, or re-arm, the debounce that runs the autosave. */
  const armAutoSave = () => {
    // Past teardown there is nobody left to save for, and a timer that re-arms
    // itself would hold the store for the life of the tab.
    if (unmountedRef.current) return

    if (timerRef.current) {
      clearTimeout(timerRef.current)
    }

    timerRef.current = setTimeout(() => {
      timerRef.current = null
      void autoSaveRef.current()
    }, AUTO_SAVE_DEBOUNCE_MS)
  }

  // The planner as it stood when this editor opened. Both baselines start here
  // rather than being filled in later: a window in which no baseline exists is a
  // window in which a flush cannot tell unsaved work from an untouched load, and
  // in which the dirty flags below have nothing to compare against.
  const [mountComparable] = useState(() => stateToComparableString(options.getState()))

  // Previous state for dirty checking
  const previousStateRef = useRef<string>(mountComparable)

  // Whether a store change has arrived since the last local write. Maintained by
  // the subscription rather than by render, so an unload firing between a keystroke
  // and the next render still sees the planner as dirty.
  const dirtyRef = useRef(false)

  // Last synced state for beforeunload warning detection
  const lastSyncedStateRef = useRef<string>(mountComparable)

  // Track the original createdAt timestamp
  const createdAtRef = useRef<string | null>(null)

  // Track sync version for optimistic locking
  const syncVersionRef = useRef<number>(initialSyncVersion ?? INITIAL_SYNC_VERSION)

  const [error, setError] = useState<AppError | null>(null)

  // Split adapters
  const storage = usePlannerStorage()
  const syncAdapter = usePlannerSyncAdapter()

  // EGO Gift data for affordability validation
  const { spec: egoGiftSpec, i18n: egoGiftI18n } = useEGOGiftListData()

  /**
   * The version the next write presents. Forward-only, and it writes nothing.
   *
   * Another surface (publish header, conflict resolution) may advance the server
   * version while this instance stays mounted; its cache write-through re-renders
   * this hook with a newer initialSyncVersion. A lagging prop must never roll back
   * a version this instance already holds, or the next save would present a stale
   * version and conflict (409).
   */
  const presentedVersion = (): number =>
    Math.max(syncVersionRef.current, initialSyncVersion ?? INITIAL_SYNC_VERSION)

  /**
   * Adopt the version the awaited response assigned, wherever a response is in
   * hand: awaiting it is what ties the ack to the request that produced it, and
   * the server's own version check is what rejects a stale concurrent write.
   * The pre-force-push read is the one other writer, and it only moves forward.
   */
  const adoptAck = (incoming: ServerAck): void => {
    syncVersionRef.current = incoming.syncVersion
  }

  /**
   * Why this planner may not be written, or null when it may be.
   *
   * A published planner faces the strict rules (title + theme packs required,
   * full difficulty enforced); a draft faces structural checks only.
   */
  const validateForSave = (
    saveable: SaveablePlanner,
    state: PlannerState,
    publishedNow: boolean,
  ): AppError | null => {
    if (!isMDPlanner(saveable)) return null

    const { content } = saveable

    const noteSizeError = validateNoteSizes(content.sectionNotes)
    if (noteSizeError) return plannerValidationError(noteSizeError)

    if (publishedNow) {
      const { isValid, errors } = validatePlannerForPublish(
        state.title,
        content,
        state.category,
        egoGiftSpec,
        egoGiftI18n,
      )
      return isValid ? null : plannerValidationError(toUserFriendlyError(errors[0]))
    }

    const validationError = validatePlannerForDraftSave(
      content,
      state.category,
      egoGiftSpec,
      egoGiftI18n,
    )
    return validationError ? plannerValidationError(validationError) : null
  }

  /** Upload the planner, reporting a rejected write instead of throwing it. */
  const syncToServer = async (
    planner: SaveablePlanner,
    mode: SaveMode,
  ): Promise<Result<AcknowledgedPlanner, AppError>> => {
    try {
      return ok(await syncAdapter.syncToServer(planner, mode === 'forcePush'))
    } catch (syncFailure: unknown) {
      return err(classifyAppError(syncFailure))
    }
  }

  /** The cache reactions a failed save owns now that classification is pure. */
  const reactToFailure = (failure: AppError) => {
    // A restriction invalidates the cached account so the app-wide banner appears.
    if (failure.kind === 'restricted') {
      void queryClient.invalidateQueries({ queryKey: authQueryKeys.me })
    }
  }

  /** React to a failed save and surface it to the editor. */
  const reportFailure = (failure: AppError) => {
    reactToFailure(failure)
    setError(failure)
  }

  /**
   * Core save logic for manual save
   * - Always saves to IndexedDB via SaveAdapter
   * - If authenticated AND syncEnabled, also syncs to server via SyncAdapter
   */
  const performSave = async (
    status: PlannerStatus,
    opts: PerformSaveOptions,
  ): Promise<Result<string, AppError>> => {
    if (!isClient) return err({ kind: 'unknown' })

    // Set createdAt on first save
    if (createdAtRef.current === null) {
      createdAtRef.current = new Date().toISOString()
    }

    // Get deviceId
    const deviceId = await storage.getOrCreateDeviceId()
    if (!deviceId.ok) return err({ kind: 'unknown' })

    const currentState = getState()
    // The snapshot this save is about to write. Everything after here may await,
    // so the live state can move on; the baseline must describe what was written.
    const savedComparable = stateToComparableString(currentState)
    const isCurrentlyPublished = opts.published ?? published

    const saveable = createSaveablePlanner({
      state: currentState,
      plannerId,
      deviceId: deviceId.value,
      schemaVersion,
      contentVersion,
      plannerType,
      existingCreatedAt: createdAtRef.current,
      existingSyncVersion: presentedVersion(),
      published: isCurrentlyPublished,
      status,
    })

    const invalid = validateForSave(saveable, currentState, isCurrentlyPublished)
    if (invalid) return err(invalid)

    // Sync first, so the local copy carries the version the server just assigned
    let didSync = false
    if (isAuthenticated && (syncEnabled || opts.mode !== 'syncIfEnabled')) {
      const synced = await syncToServer(saveable, opts.mode)
      if (!synced.ok) return err(synced.error)
      didSync = true

      adoptAck(synced.value.ack)
      saveable.metadata.syncVersion = synced.value.ack.syncVersion
    }

    // Save to IndexedDB (with updated syncVersion if synced)
    const localResult = await storage.saveToLocal(saveable)
    if (!localResult.ok) return localResult

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

    return ok(savedComparable)
  }

  /**
   * Adopt `comparable` as the local-write baseline.
   *
   * The baseline is deliberately older than the live state whenever a save
   * overlapped an edit, so dirtiness is what the comparison says rather than a
   * flat false — clearing it there would silence the unload warning over exactly
   * the window where the edit is still unwritten.
   */
  const markLocalBaseline = (comparable: string) => {
    previousStateRef.current = comparable
    dirtyRef.current = stateToComparableString(getStateRef.current()) !== comparable
  }

  /**
   * Adopt the state a save actually wrote as both dirty-check baselines. Reading
   * the live state here instead would adopt edits typed during the save as clean.
   */
  const markSaved = (comparable: string) => {
    markLocalBaseline(comparable)
    lastSyncedStateRef.current = comparable
    setLastSavedAt(new Date().toISOString())
  }

  /**
   * A failed teardown flush has nowhere to surface: the editor is gone, so the
   * error state and its toast reach nobody. Leave the loss in the console with
   * enough to identify which planner lost what.
   */
  const reportLostFlush = (flushing: boolean, failure: SaveError) => {
    if (!flushing) return
    console.error('Planner autosave flush lost after teardown', { plannerId, failure })
  }

  /**
   * Debounced auto-save.
   * Auto-saves ALWAYS go to IndexedDB only (local-first architecture);
   * syncing to the server is manual-save only.
   */
  const runAutoSave = async (flushing: boolean) => {
    // A manual save owns the write. Never just return: that loses every edit made
    // between the save starting and it finishing. A teardown flush cannot wait on
    // a timer, so it hands itself to the save to run when it is done.
    if (isSavingRef.current) {
      if (flushing) {
        pendingFlushRef.current = true
      } else {
        armAutoSave()
      }
      return
    }

    const currentState = getState()
    const currentStateString = stateToComparableString(currentState)

    // Skip if state hasn't changed
    if (currentStateString === previousStateRef.current) {
      dirtyRef.current = false
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
      if (!deviceId.ok) return

      const saveable = createSaveablePlanner({
        state: currentState,
        plannerId,
        deviceId: deviceId.value,
        schemaVersion,
        contentVersion,
        plannerType,
        existingCreatedAt: createdAtRef.current,
        existingSyncVersion: presentedVersion(),
        published,
        status: 'draft',
      })

      // Save to IndexedDB only via SaveAdapter (never server for auto-save)
      const result = await storage.saveToLocal(saveable)
      if (!result.ok) {
        reportFailure(result.error)
        reportLostFlush(flushing, result.error)
        return
      }

      markLocalBaseline(currentStateString)
      setLastSavedAt(new Date().toISOString())
    } catch (autoSaveError: unknown) {
      const failure = classifyAppError(autoSaveError)
      reportFailure(failure)
      reportLostFlush(flushing, failure)
    } finally {
      setIsAutoSaving(false)
    }
  }

  /**
   * Run autosaves one after another. The teardown flush can start while a
   * debounced run is still awaiting storage, and two concurrent writes would
   * race, the older snapshot landing last.
   */
  const autoSave = (flushing = false): Promise<void> => {
    const chained = inFlightRef.current.then(() => runAutoSave(flushing)).catch(() => undefined)
    inFlightRef.current = chained
    return chained
  }

  /**
   * Take ownership of the write for a manual save. Owners nest — a conflict
   * resolution runs saves of its own — so the lock counts holds rather than
   * flipping a flag the inner release would drop on the outer owner's behalf.
   */
  const beginSave = () => {
    saveHoldsRef.current += 1
    isSavingRef.current = true
    setIsSaving(true)
  }

  /**
   * Release one hold, and once the last is gone run a teardown flush that arrived
   * while a save held it. Refs still work on an unmounted fiber, which is the only
   * reason those last edits are still reachable at all.
   */
  const endSave = () => {
    saveHoldsRef.current = Math.max(0, saveHoldsRef.current - 1)
    if (saveHoldsRef.current > 0) return

    isSavingRef.current = false
    setIsSaving(false)

    if (pendingFlushRef.current) {
      pendingFlushRef.current = false
      void autoSaveRef.current(true)
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

    beginSave()

    try {
      const saved = await performSave('saved', {
        published: saveOptions?.published,
        mode: saveOptions?.forceSync ? 'forceSync' : 'syncIfEnabled',
      })
      if (!saved.ok) {
        reportFailure(saved.error)
        return false
      }

      markSaved(saved.value)
      return true
    } catch (saveFailure: unknown) {
      reportFailure(classifyAppError(saveFailure))
      return false
    } finally {
      endSave()
    }
  }

  /** Keep Both: fork local changes to a new planner, then run the rest of the plan. */
  const forkLocalChanges = async (
    metadata: ConflictForkMetadata,
    rest: () => Promise<Result<void, AppError>>,
  ): Promise<Result<void, AppError>> => {
    const currentState = getState()
    const newPlanner = createSaveablePlanner({
      state: { ...currentState, title: metadata.title },
      plannerId: metadata.id,
      deviceId: metadata.deviceId,
      schemaVersion,
      contentVersion,
      plannerType,
      existingCreatedAt: metadata.createdAt,
      existingSyncVersion: metadata.syncVersion,
      published: false,
      status: metadata.status,
    })

    // The copy carries the same content as currentState, so the draft rules apply.
    if (isMDPlanner(newPlanner)) {
      const { content } = newPlanner
      const noteSizeError = validateNoteSizes(content.sectionNotes)
      if (noteSizeError) return err(plannerValidationError(noteSizeError))
      if (egoGiftSpec) {
        const validationError = validatePlannerForDraftSave(
          content,
          currentState.category,
          egoGiftSpec,
          egoGiftI18n,
        )
        if (validationError) return err(plannerValidationError(validationError))
      }
    }

    const outcome = await withRollback<AppError>({
      create: () => storage.saveToLocal(newPlanner),
      rollback: () => storage.deleteFromLocal(metadata.id),
      rest: async () => {
        // The user pressed save intentionally, so the copy syncs regardless of syncEnabled.
        if (isAuthenticated) {
          const synced = await syncToServer(newPlanner, 'forceSync')
          if (!synced.ok) return err(synced.error)
        }

        const remaining = await rest()
        if (!remaining.ok) return remaining

        if (onKeepBothCreated) {
          onKeepBothCreated(metadata.id)
        }
        return ok(undefined)
      },
    })

    switch (outcome.kind) {
      case 'completed':
        return ok(undefined)
      case 'undone':
        return err(outcome.error)
      case 'undoFailed':
        // The copy survives the failure, so the editor reports the original cause
        // while the orphan stays visible in the planner list.
        console.error('Rollback of the forked planner failed:', outcome.rollbackError)
        return err(outcome.error)
      default:
        return assertNever(outcome)
    }
  }

  /**
   * Take the server's current sync version without touching local content.
   *
   * Local content stays as it is here, so the version must only ever move
   * forward: adopting a lower one would present a version the local copy has
   * already passed, and the force push that follows would overwrite from behind.
   */
  const adoptServerSyncVersion = async (): Promise<Result<void, AppError>> => {
    const fetched = await syncAdapter.fetchFromServer(plannerId)
    if (!fetched.ok) return err(fetched.error)

    syncVersionRef.current = Math.max(syncVersionRef.current, fetched.value.ack.syncVersion)
    return ok(undefined)
  }

  /** Discard local changes: adopt the server version as both the local copy and editor state. */
  const revertToServerVersion = async (): Promise<Result<void, AppError>> => {
    // A resolution that proceeds without the server copy either overwrites the
    // server from a stale version or reports a discard that never happened.
    const fetched = await syncAdapter.fetchFromServer(plannerId)
    if (!fetched.ok) return err(fetched.error)

    const serverPlanner = fetched.value.planner

    // Adopted only once the copy is durable: a failed write would otherwise leave
    // the version at the server's while the content is still the old local one.
    const localResult = await storage.saveToLocal(serverPlanner)
    if (!localResult.ok) return localResult
    adoptAck(fetched.value.ack)

    // A reload that refused the copy leaves the editor holding the old local
    // content. Reporting success there would announce a discard that never
    // happened and navigate away from the conflict it claimed to resolve.
    if (onServerReload) {
      if (!onServerReload(serverPlanner)) return err({ kind: 'unknown' })
      markSaved(stateToComparableString(getState()))
    }
    return ok(undefined)
  }

  /** Run a resolution plan in order; a fork rolls back when a later effect fails. */
  const runEffects = async (
    effects: ConflictEffect[],
    serverVersion: number | null,
  ): Promise<Result<void, AppError>> => {
    const [effect, ...remaining] = effects
    if (!effect) return ok(undefined)

    switch (effect.kind) {
      case 'keepLocal': {
        // A conflict that reported no server version leaves the local syncVersion
        // unanchored, so read the server's back before writing over it.
        if (serverVersion == null) {
          const adopted = await adoptServerSyncVersion()
          if (!adopted.ok) return adopted
        }
        const saved = await performSave('saved', { mode: 'forcePush' })
        if (!saved.ok) return saved
        markSaved(saved.value)
        break
      }
      case 'forkCopy':
        return forkLocalChanges(effect.metadata, () => runEffects(remaining, serverVersion))
      case 'adoptIncoming': {
        const reverted = await revertToServerVersion()
        if (!reverted.ok) return reverted
        break
      }
      default:
        return assertNever(effect)
    }

    return runEffects(remaining, serverVersion)
  }

  /**
   * Report a failed resolution.
   *
   * The conflict is still unresolved, so the conflict error stays put and the
   * dialog with it. Only a newer conflict displaces it.
   */
  const failResolution = (failure: AppError): boolean => {
    reactToFailure(failure)
    if (failure.kind === 'conflict') {
      setError(failure)
    }
    return false
  }

  /**
   * Resolve a conflict
   * @returns true if resolution succeeded, false if it failed
   */
  const resolveConflict = async (choice: ConflictResolutionChoice): Promise<boolean> => {
    if (error?.kind !== 'conflict') return false
    const { serverVersion } = error

    beginSave()

    // Prevents a race where the timer fires with stale state before React re-renders
    cancelPendingAutoSave()

    try {
      const deviceId = await storage.getOrCreateDeviceId()
      if (!deviceId.ok) throw new Error('Failed to get device ID', { cause: deviceId.error })
      const plan = planConflictResolution(
        choice,
        {
          forkSide: 'local',
          forkTitle: getState().title || t('pages.plannerMD.untitled', 'Untitled'),
        },
        {
          deviceId: deviceId.value,
          now: new Date().toISOString(),
          newId: generateUUID,
          copyTitle: (title) =>
            t('pages.plannerMD.conflict.copySuffix', '{{title}} (Copy)', { title }),
        },
      )

      const resolved = await runEffects(plan, serverVersion)
      if (!resolved.ok) return failResolution(resolved.error)

      // Clear conflict state only on success
      setError(null)
      return true
    } catch (resolutionError: unknown) {
      return failResolution(classifyAppError(resolutionError))
    } finally {
      endSave()
    }
  }

  /**
   * Clear error state
   */
  const clearError = () => {
    setError(null)
  }

  // The subscription must survive re-renders, so the effect reads the auto-save
  // and the state getter through refs instead of depending on closures that
  // change every render.
  useEffect(() => {
    autoSaveRef.current = autoSave
    getStateRef.current = getState
  })

  // Debounced auto-save driven by store subscription rather than a state
  // dependency, so the parent does not re-render on every state change.
  useEffect(() => {
    if (!isClient) return

    unmountedRef.current = false

    const unsubscribe = subscribe(() => {
      dirtyRef.current = true
      armAutoSave()
    })

    return () => {
      unmountedRef.current = true
      unsubscribe()
      if (timerRef.current) {
        clearTimeout(timerRef.current)
        timerRef.current = null
      }

      // A cancelled timer is silent data loss on unmount and on tab close. React
      // destroys a parent's effects before its children's, so a descendant editor
      // has not yet handed over its last keystrokes; a microtask runs after every
      // destroy in the commit, and the flush reads the store rather than the
      // subscription this cleanup just dropped.
      queueMicrotask(() => void autoSaveRef.current(true))
    }
  }, [subscribe])

  // The ref is a fast path only: it arms on any store notification, including
  // writes that change nothing persistable. Confirm before claiming dirtiness —
  // the caller is an unload handler, so the comparison runs rarely.
  const isDirty = useCallback(() => {
    if (!dirtyRef.current) return false
    return stateToComparableString(getStateRef.current()) !== previousStateRef.current
  }, [])

  // Dirty flags compare the live state against the two save baselines, both of
  // which the subscription installs at mount.
  const currentComparable = stateToComparableString(getState())
  const hasUnsyncedChanges = currentComparable !== lastSyncedStateRef.current
  const hasLocalUnsavedChanges = currentComparable !== previousStateRef.current

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
    syncVersion: presentedVersion(),
    hasUnsyncedChanges,
    hasLocalUnsavedChanges,
    isDirty,
    lastSavedAt,
    isRestricted,
    restrictionReason,
  }
}
