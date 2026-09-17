import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '@/lib/i18n'
import { useAuthQuery, authQueryKeys } from '@/shared/auth'
import { useRestrictionStatus } from '@/shared/moderation'
import { usePlannerStorage } from './usePlannerStorage'
import { usePlannerSyncAdapter } from './usePlannerSyncAdapter'
import { userPlannersQueryKeys } from './useMDUserPlannersData'
import { plannerQueryKeys } from '../lib/plannerQueryKeys'
import { useEGOGiftListSpec, useEGOGiftListI18n } from '@/pages/egoGift'
import { isMDPlanner } from '../types/PlannerTypes'
import { queryClient } from '@/lib/queryClient'
import { INITIAL_SYNC_VERSION } from '@/lib/constants'
import { openStorageDb } from '@/lib/storage'
import { createWriteThrough } from '../lib/writeThrough'
import { createSaveStatusStore } from '../stores/saveStatus'
import type { SaveStatusStore } from '../stores/saveStatus'
import { generateUUID } from '@/lib/uuid'
import {
  validatePlannerForDraftSave,
  validatePlannerForPublish,
  validateNoteSizes,
} from '../lib/plannerValidation'
import { plannerValidationError, toUserFriendlyError } from '../lib/plannerValidationErrors'
import { classifyAppError, isSyncConflict } from '@/lib/apiErrorClassifier'
import { planConflictResolution, interpretConflictPlan } from '../lib/conflictChoice'
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
  /** Timestamp the last-saved label starts from (for editing) */
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
  /** What the write path last reported, for the status label to subscribe to. */
  saveStatus: SaveStatusStore
  /** Whether user is restricted (banned or timed out) - disables sync button */
  isRestricted: boolean
  /** Reason for restriction (ban or timeout reason) */
  restrictionReason: string | undefined
}

/**
 * Unified hook for saving planner state (local write-through + manual save)
 *
 * Features:
 * - Every store change is written to IndexedDB inside the task that made it
 * - Manual save() function with proper syncVersion tracking
 * - Conflict detection with typed ConflictError
 * - Resolution via resolveConflict('overwrite' | 'discard' | 'both')
 *
 * @example
 * ```tsx
 * function PlannerPage() {
 *   const { isSaving, error, save, resolveConflict } = usePlannerSave({
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
 *       <button onClick={() => save()} disabled={isSaving}>Save</button>
 *       {isSyncConflict(error) && (
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

  const [isSaving, setIsSaving] = useState(false)

  // A store rather than state, so a write landing does not render the editor
  // that owns this hook.
  const [saveStatus] = useState(() => createSaveStatusStore(initialSavedAt ?? null))

  // Owners of the write nest: a conflict resolution runs saves of its own.
  const saveHoldsRef = useRef(0)

  // A store change that arrived while a save held the write.
  const pendingWriteRef = useRef(false)

  // Set while a write failure stands, so it is reported once rather than per keystroke.
  const writeFailedRef = useRef(false)

  // Read by the write path, which must stay stable for a subscription made once.
  const getStateRef = useRef(options.getState)
  const writeNowRef = useRef<() => void>(() => {})

  // The planner as last written, so a store change that alters nothing
  // persistable is not written. Taken at first render, replaced once the editors
  // have handed over their loaded content.
  const [mountComparable] = useState(() => stateToComparableString(options.getState()))
  const lastWrittenRef = useRef(mountComparable)
  const baselineSettledRef = useRef(false)

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
  const egoGiftSpec = useEGOGiftListSpec()
  const egoGiftI18n = useEGOGiftListI18n()

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
  ): SaveablePlanner => {
    createdAtRef.current ??= new Date().toISOString()

    return createSaveablePlanner({
      state,
      plannerId,
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
      const { errors } = validatePlannerForPublish(
        saveable.metadata.title,
        content,
        category,
        egoGiftSpec,
        egoGiftI18n,
      )
      const [firstError] = errors
      return firstError ? plannerValidationError(toUserFriendlyError(firstError)) : null
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

    const currentState = getState()
    // The snapshot this save is about to write. Everything after here may await,
    // so the live state can move on; the baseline must describe what was written.
    const savedComparable = stateToComparableString(currentState)

    const saveable = buildSaveable(currentState, status, opts.published ?? published)

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

  /** Adopt the state a write landed as the local baseline. */
  const markWritten = (comparable: string) => {
    lastWrittenRef.current = comparable
    saveStatus.setState({ lastSavedAt: new Date().toISOString() })
  }

  /**
   * A manual save owns the write while it runs: an edit made meanwhile waits and
   * is written when the save releases, so the save's snapshot never lands after
   * something newer.
   */
  const writeNow = (): void => {
    if (saveHoldsRef.current > 0) {
      pendingWriteRef.current = true
      return
    }
    pendingWriteRef.current = false

    const state = getStateRef.current()
    const comparable = stateToComparableString(state)
    if (comparable === lastWrittenRef.current) return

    void storage.saveToLocal(buildSaveable(state, 'draft', published)).then((result) => {
      if (result.ok) {
        writeFailedRef.current = false
        markWritten(comparable)
        return
      }
      if (!writeFailedRef.current) reportFailure(result.error)
      writeFailedRef.current = true
    })
  }

  const beginSave = () => {
    saveHoldsRef.current += 1
    setIsSaving(true)
  }

  // Refs still work on an unmounted fiber, which is what keeps an edit made
  // during a save reachable after the editor is gone.
  const endSave = () => {
    saveHoldsRef.current = Math.max(0, saveHoldsRef.current - 1)
    if (saveHoldsRef.current > 0) return

    setIsSaving(false)
    if (pendingWriteRef.current) writeNowRef.current()
  }

  /**
   * Manual save function
   * @returns true if save succeeded, false if it failed
   */
  const save = async (saveOptions?: SaveOptions): Promise<boolean> => {
    if (!isClient) return false

    beginSave()

    try {
      const saved = await performSave('saved', {
        ...(saveOptions?.published !== undefined && { published: saveOptions.published }),
        mode: saveOptions?.forceSync ? 'forceSync' : 'syncIfEnabled',
      })
      if (!saved.ok) {
        reportFailure(saved.error)
        return false
      }

      markWritten(saved.value)
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
  const localSide = (): SaveablePlanner => buildSaveable(getState(), 'saved', published)

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
    if (isSyncConflict(failure)) {
      setError(failure)
    }
    return false
  }

  /**
   * Resolve a conflict
   * @returns true if resolution succeeded, false if it failed
   */
  const resolveConflict = async (choice: ConflictResolutionChoice): Promise<boolean> => {
    if (!isSyncConflict(error)) return false
    const { serverVersion } = error

    beginSave()
    setResolutionError(null)

    try {
      const ctx = { now: new Date().toISOString(), newId: generateUUID }
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
          handed.comparable = stateToComparableString(getState())
          return ok(localSide())
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
        deleteRemote: syncAdapter.deleteFromServer,
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
          markWritten(stateToComparableString(getState()))
        }
      }
      if (keepsLocal(plan) && handed.comparable !== null) {
        markWritten(handed.comparable)
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

  useEffect(() => {
    writeNowRef.current = writeNow
    getStateRef.current = getState
  })

  useEffect(() => {
    if (!isClient) return

    // Each editor hands Tiptap's reparse of its stored note over in its own mount
    // effect, before this one runs; that reparse is a load, not an edit.
    if (!baselineSettledRef.current) {
      baselineSettledRef.current = true
      lastWrittenRef.current = stateToComparableString(getStateRef.current())
    }

    // The first edit must find the connection open: a write issued before then
    // waits on the open, and a discard in that gap loses it.
    void openStorageDb().catch((failure: unknown) => {
      console.error('Planner storage could not open', failure)
    })

    return createWriteThrough(subscribe, () => writeNowRef.current())
  }, [subscribe])

  const restrictionReason = !isRestricted
    ? undefined
    : restrictionRawReason ||
      i18n.t(isBanned ? 'moderation.bannedNoReason' : 'moderation.timedOutNoReason', {
        ns: 'common',
      })

  return {
    plannerId,
    isSaving,
    error,
    resolutionError,
    clearError,
    save,
    resolveConflict,
    syncVersion: presentedVersion(),
    saveStatus,
    isRestricted,
    restrictionReason,
  }
}
