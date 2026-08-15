import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '@/lib/i18n'
import { useAuthQuery, authQueryKeys } from '@/shared/auth'
import { useRestrictionStatus } from '@/shared/moderation'
import { usePlannerStorage } from './usePlannerStorage'
import { usePlannerSyncAdapter } from './usePlannerSyncAdapter'
import { userPlannersQueryKeys } from './useMDUserPlannersData'
import { plannerQueryKeys } from '../lib/plannerQueryKeys'
import { useEGOGiftListData } from '@/pages/egoGift'
import { isMDPlanner } from '../types/PlannerTypes'
import { queryClient } from '@/lib/queryClient'
import { AUTO_SAVE_DEBOUNCE_MS, INITIAL_SYNC_VERSION } from '@/lib/constants'
import { generateUUID } from '@/lib/uuid'
import {
  validatePlannerForDraftSave,
  validatePlannerForPublish,
  validateNoteSizes,
} from '../lib/plannerValidation'
import { plannerValidationError, toUserFriendlyError } from '../lib/plannerValidationErrors'
import { classifyAppError } from '@/lib/apiErrorClassifier'
import {
  planConflictResolution,
  interpretConflictPlan,
  reportedDelete,
} from '../lib/conflictChoice'
import {
  forkedPlannerId,
  keepsLocal,
  needsServerAnchor,
  resolutionPlan,
} from '../lib/editorConflictPlan'
import { createSaveablePlanner, stateToComparableString } from '../lib/saveablePlanner'
import { acknowledgedCopy } from './usePlannerSyncAdapter'
import { ok, err } from '@/lib/result'
import type { Result } from '@/lib/result'
import type { ConflictOps } from '../lib/conflictChoice'
import type { HeldResolution } from '../lib/editorConflictPlan'
import type { PlannerState } from '../lib/saveablePlanner'
import type { AcknowledgedPlanner } from './usePlannerSyncAdapter'
import type { AppError } from '@/lib/apiErrorClassifier'
import type {
  SaveablePlanner,
  ConflictResolutionChoice,
  MDConfig,
  PlannerStatus,
  ServerAck,
} from '../types/PlannerTypes'

export type { PlannerState } from '../lib/saveablePlanner'

/**
 * SSR safety check
 */
const isClient = typeof window !== 'undefined'

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
  /** Type of planner. This editor holds Mirror Dungeon state, and saves it as that. */
  plannerType: MDConfig['type']
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
  /** Why the last resolution attempt failed. The conflict itself stays in `error`. */
  resolutionError: AppError | null
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
  const { isRestricted, isBanned, reason: restrictionRawReason } = useRestrictionStatus()

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

  // The planner as it stood at first render, so the dirty flags below have
  // something to compare against before any effect has run. The subscription
  // replaces it once the editors have handed over their loaded content.
  const [mountComparable] = useState(() => stateToComparableString(options.getState()))

  // Whether that replacement has already happened.
  const baselineSettledRef = useRef(false)

  // Previous state for dirty checking
  const previousStateRef = useRef<string>(mountComparable)

  // Whether a store change has arrived since the last local write. Maintained by
  // the subscription rather than by render, so an unload firing between a keystroke
  // and the next render still sees the planner as dirty.
  const dirtyRef = useRef(false)

  // Last synced state for beforeunload warning detection
  const lastSyncedStateRef = useRef<string>(mountComparable)

  // The live planner's comparable, and whether the store has moved since it was
  // computed. Serializing the whole planner is what the dirty flags cost, and
  // they are read on every render while the store changes far less often.
  const comparableRef = useRef<string>(mountComparable)
  const comparableStaleRef = useRef(false)

  /**
   * The comparable of the planner as it stands now.
   *
   * Passing `state` recomputes unconditionally. The cache tracks staleness from
   * the store subscription, and the subscription is gone by the time a teardown
   * flush runs: React destroys this effect before the descendant editors that
   * hand over their last note, so the final write of a planner's life arrives
   * unobserved. Deciding from the cache there drops it as a no-op.
   *
   * The cached path serves the two dirty flags, which are read every render and
   * hold the same assumption the `isDirty` fast path already makes.
   */
  const readComparable = (state?: PlannerState): string => {
    if (state !== undefined) {
      comparableRef.current = stateToComparableString(state)
      comparableStaleRef.current = false
      return comparableRef.current
    }
    if (comparableStaleRef.current) {
      comparableRef.current = stateToComparableString(getStateRef.current())
      comparableStaleRef.current = false
    }
    return comparableRef.current
  }

  // Track the original createdAt timestamp
  const createdAtRef = useRef<string | null>(null)

  // Track sync version for optimistic locking
  const syncVersionRef = useRef<number>(initialSyncVersion ?? INITIAL_SYNC_VERSION)

  const [error, setError] = useState<AppError | null>(null)
  const [resolutionError, setResolutionError] = useState<AppError | null>(null)

  // The plan built for the conflict now on screen, held across retries.
  const heldPlan = useRef<HeldResolution | null>(null)

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
   * The planner a write is about to persist.
   *
   * Every writer differs on three things only: the state it writes, whose
   * publication it carries, and the status it lands under. A null `createdAt`
   * would stamp a fresh creation time onto a planner that already exists, so the
   * first write to reach here is the one that fixes it.
   */
  const buildSaveable = (
    state: PlannerState,
    status: PlannerStatus,
    isPublished: boolean,
    deviceId: string,
  ): SaveablePlanner => {
    createdAtRef.current ??= new Date().toISOString()

    return createSaveablePlanner({
      state,
      plannerId,
      deviceId,
      schemaVersion,
      contentVersion,
      plannerType,
      existingCreatedAt: createdAtRef.current,
      existingSyncVersion: presentedVersion(),
      published: isPublished,
      status,
    })
  }

  /**
   * Why this planner may not be written, or null when it may be.
   *
   * A published planner faces the strict rules (title + theme packs required,
   * full difficulty enforced); a draft faces structural checks only.
   */
  const validateForSave = (saveable: SaveablePlanner): AppError | null => {
    if (!isMDPlanner(saveable)) return null

    const { content } = saveable
    const { category } = saveable.config

    const noteSizeError = validateNoteSizes(content.sectionNotes)
    if (noteSizeError) return plannerValidationError(noteSizeError)

    if (saveable.metadata.published) {
      const { isValid, errors } = validatePlannerForPublish(
        saveable.metadata.title,
        content,
        category,
        egoGiftSpec,
        egoGiftI18n,
      )
      return isValid ? null : plannerValidationError(toUserFriendlyError(errors[0]))
    }

    const validationError = validatePlannerForDraftSave(content, category, egoGiftSpec, egoGiftI18n)
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
    // The incoming failure is the current one; a previous resolution's reason
    // describes a conflict that is no longer the one on screen.
    setResolutionError(null)
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

    // Get deviceId
    const deviceId = await storage.getOrCreateDeviceId()
    if (!deviceId.ok) return err({ kind: 'unknown' })

    const currentState = getState()
    // The snapshot this save is about to write. Everything after here may await,
    // so the live state can move on; the baseline must describe what was written.
    const savedComparable = readComparable(currentState)

    const saveable = buildSaveable(
      currentState,
      status,
      opts.published ?? published,
      deviceId.value,
    )

    const invalid = validateForSave(saveable)
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
    dirtyRef.current = readComparable(getStateRef.current()) !== comparable
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
  const reportLostFlush = (flushing: boolean, failure: AppError) => {
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
    const currentStateString = readComparable(currentState)

    // Skip if state hasn't changed
    if (currentStateString === previousStateRef.current) {
      dirtyRef.current = false
      return
    }

    setIsAutoSaving(true)

    try {
      if (!isClient) return

      // Get deviceId
      const deviceId = await storage.getOrCreateDeviceId()
      if (!deviceId.ok) return

      const saveable = buildSaveable(currentState, 'draft', published, deviceId.value)

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

  /** The title an untitled planner is copied and displayed under. */
  const untitled = (): string => t('pages.plannerMD.untitled', 'Untitled')

  /** The local side of the conflict, as the editor holds it right now. */
  const localSide = (deviceId: string): SaveablePlanner =>
    buildSaveable(getState(), 'saved', published, deviceId)

  /**
   * Report a failed resolution.
   *
   * The conflict is still unresolved, so the conflict error stays put and the
   * dialog with it. Only a newer conflict displaces it, which is why the reason
   * the attempt failed needs a slot of its own.
   */
  const failResolution = (failure: AppError): boolean => {
    reactToFailure(failure)
    setResolutionError(failure)
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
    setResolutionError(null)

    // Prevents a race where the timer fires with stale state before React re-renders
    cancelPendingAutoSave()

    try {
      const deviceId = await storage.getOrCreateDeviceId()
      if (!deviceId.ok) return failResolution({ kind: 'unknown' })

      const ctx = { deviceId: deviceId.value, now: new Date().toISOString(), newId: generateUUID }
      const plan = resolutionPlan(heldPlan.current, error, choice, () =>
        planConflictResolution(
          choice,
          { forkSide: 'local', forkTitle: getState().title || untitled() },
          {
            ...ctx,
            copyTitle: (title) =>
              t('pages.plannerMD.conflict.copySuffix', '{{title}} (Copy)', { title }),
          },
        ),
      )
      heldPlan.current = { conflict: error, choice, plan }

      if (needsServerAnchor(serverVersion, plan)) {
        const adopted = await adoptServerSyncVersion()
        if (!adopted.ok) return failResolution(adopted.error)
      }

      // Held rather than applied on arrival: the server's version may only be
      // adopted once its content is durable locally.
      const fetched: { value: AcknowledgedPlanner | null } = { value: null }

      // The comparable of the exact state handed to the interpreter: a note
      // delivery can land mid-resolution, and the baseline must describe what
      // was pushed, not what the store holds afterwards.
      const handed: { comparable: string | null } = { comparable: null }
      const ops: ConflictOps = {
        local: async () => {
          handed.comparable = readComparable(getState())
          return ok(localSide(deviceId.value))
        },
        incoming: async () => {
          const incoming = await syncAdapter.fetchFromServer(plannerId)
          if (!incoming.ok) return err(incoming.error)
          fetched.value = incoming.value
          return ok(incoming.value.planner)
        },
        validate: validateForSave,
        saveLocal: storage.saveToLocal,
        deleteLocal: storage.deleteFromLocal,
        deleteRemote: reportedDelete((id) => syncAdapter.deleteFromServer(id)),
        sync: async (planner, force) => {
          // The user chose this resolution, so it uploads whatever the sync
          // setting says; a signed-out editor resolves locally and uploads
          // nothing, which is what `null` says to the rollback.
          if (!isAuthenticated) return ok(null)

          const synced = await syncToServer(planner, force ? 'forcePush' : 'forceSync')
          if (!synced.ok) return err(synced.error)

          // Only the conflicting planner's own ack moves the version this editor presents.
          if (planner.metadata.id === plannerId) adoptAck(synced.value.ack)
          return ok(acknowledgedCopy(synced.value))
        },
        sanitizeTitle: (title) => title.trim() || untitled(),
      }

      const resolved = await interpretConflictPlan(plan, ops, ctx)
      if (!resolved.ok) return failResolution(resolved.error.error)

      if (fetched.value) {
        adoptAck(fetched.value.ack)
        // A reload that refused the copy leaves the editor holding the old local
        // content; reporting success would announce a discard that never happened.
        if (onServerReload) {
          if (!onServerReload(fetched.value.planner)) {
            return failResolution({ kind: 'unknown' })
          }
          markSaved(readComparable(getState()))
        }
      }
      if (keepsLocal(plan) && handed.comparable !== null) {
        markSaved(handed.comparable)
      }

      // Write-through: every mounted consumer (publish header, list pages) must
      // see the version the resolution left on the server.
      void queryClient.invalidateQueries({ queryKey: plannerQueryKeys.detail(plannerId) })
      void queryClient.invalidateQueries({ queryKey: userPlannersQueryKeys.all })

      const forkedId = forkedPlannerId(plan)
      if (forkedId !== null && onKeepBothCreated) onKeepBothCreated(forkedId)

      // Clear conflict state only on success
      heldPlan.current = null
      setError(null)
      return true
    } catch (failure: unknown) {
      return failResolution(classifyAppError(failure))
    } finally {
      endSave()
    }
  }

  /**
   * Clear error state
   */
  const clearError = () => {
    heldPlan.current = null
    setError(null)
    setResolutionError(null)
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

    // Adopt the store as it stands now, not as it stood at render. Each editor
    // hands Tiptap's reparse of its stored note over in its own mount effect, and
    // those run before this one — a note stored without content arrives as `''`
    // and reparses into an empty document, which is a load, not an edit. Compare
    // against the render-time capture and every such planner opens dirty and gets
    // written back as a draft.
    if (!baselineSettledRef.current) {
      baselineSettledRef.current = true
      const settled = readComparable(getStateRef.current())
      previousStateRef.current = settled
      lastSyncedStateRef.current = settled
      dirtyRef.current = false
    }

    const unsubscribe = subscribe(() => {
      dirtyRef.current = true
      comparableStaleRef.current = true
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
    return readComparable() !== previousStateRef.current
  }, [])

  // Dirty flags compare the live state against the two save baselines, both of
  // which the subscription installs at mount. The baselines advance separately —
  // an autosave moves the local one only — so each flag holds its own comparison
  // over the one shared reading of the planner.
  const currentComparable = readComparable()
  const hasUnsyncedChanges = currentComparable !== lastSyncedStateRef.current
  const hasLocalUnsavedChanges = currentComparable !== previousStateRef.current

  const restrictionReason = !isRestricted
    ? undefined
    : restrictionRawReason ||
      i18n.t(isBanned ? 'moderation.bannedNoReason' : 'moderation.timedOutNoReason', {
        ns: 'common',
      })

  return {
    plannerId,
    isAutoSaving,
    isSaving,
    error,
    resolutionError,
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
