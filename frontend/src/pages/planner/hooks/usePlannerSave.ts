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

const isClient = typeof window !== 'undefined'

export interface UsePlannerSaveOptions {
  getState: () => PlannerState
  subscribe: (listener: () => void) => () => void
  schemaVersion: number
  contentVersion: number
  plannerType: MDConfig['type']
  initialPlannerId?: string
  initialSyncVersion?: number
  initialSavedAt?: string
  published?: boolean
  onServerReload?: (planner: SaveablePlanner) => boolean
  onKeepBothCreated?: (newPlannerId: string) => void
  syncEnabled?: boolean
}

export interface SaveOptions {
  published?: boolean
  forceSync?: boolean
}

type SaveMode = 'syncIfEnabled' | 'forceSync' | 'forcePush'

interface PerformSaveOptions {
  published?: boolean
  mode: SaveMode
}

export interface PlannerSaveResult {
  plannerId: string
  isSaving: boolean
  error: AppError | null
  resolutionError: AppError | null
  clearError: () => void
  save: (options?: SaveOptions) => Promise<boolean>
  resolveConflict: (choice: ConflictResolutionChoice) => Promise<boolean>
  syncVersion: number
  saveStatus: SaveStatusStore
  isRestricted: boolean
  restrictionReason: string | undefined
}

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

  const { data: user } = useAuthQuery()
  const isAuthenticated = !!user
  const { isRestricted, isBanned, reason: restrictionRawReason } = useRestrictionStatus()

  const [plannerId] = useState<string>(() => initialPlannerId ?? generateUUID())

  const [isSaving, setIsSaving] = useState(false)

  const [saveStatus] = useState(() => createSaveStatusStore(initialSavedAt ?? null))

  const saveHoldsRef = useRef(0)

  const pendingWriteRef = useRef(false)

  const writeFailedRef = useRef(false)

  const getStateRef = useRef(options.getState)
  const writeNowRef = useRef<() => void>(() => {})

  const [mountComparable] = useState(() => stateToComparableString(options.getState()))
  const lastWrittenRef = useRef(mountComparable)
  const baselineSettledRef = useRef(false)

  const createdAtRef = useRef<string | null>(null)

  const syncVersionRef = useRef<number>(initialSyncVersion ?? INITIAL_SYNC_VERSION)

  const [error, setError] = useState<AppError | null>(null)
  const [resolutionError, setResolutionError] = useState<AppError | null>(null)

  const heldPlan = useRef<HeldResolution | null>(null)

  const storage = usePlannerStorage()
  const syncAdapter = usePlannerSyncAdapter()

  const egoGiftSpec = useEGOGiftListSpec()
  const egoGiftI18n = useEGOGiftListI18n()

  const presentedVersion = (): number =>
    Math.max(syncVersionRef.current, initialSyncVersion ?? INITIAL_SYNC_VERSION)

  const adoptAck = (incoming: ServerAck): void => {
    syncVersionRef.current = incoming.syncVersion
  }

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

  const reactToFailure = (failure: AppError) => {
    if (failure.kind === 'restricted') {
      void queryClient.invalidateQueries({ queryKey: authQueryKeys.me })
    }
  }

  const reportFailure = (failure: AppError) => {
    reactToFailure(failure)
    setError(failure)
    setResolutionError(null)
  }

  const performSave = async (
    status: PlannerStatus,
    opts: PerformSaveOptions,
  ): Promise<Result<string, AppError>> => {
    if (!isClient) return err({ kind: 'unknown' })

    const currentState = getState()
    const savedComparable = stateToComparableString(currentState)

    const saveable = buildSaveable(currentState, status, opts.published ?? published)

    const invalid = validateForSave(saveable)
    if (invalid) return err(invalid)

    let didSync = false
    if (isAuthenticated && (syncEnabled || opts.mode !== 'syncIfEnabled')) {
      const synced = await syncToServer(saveable, opts.mode)
      if (!synced.ok) return err(synced.error)
      didSync = true

      adoptAck(synced.value.ack)
      saveable.metadata.syncVersion = synced.value.ack.syncVersion
    }

    const localResult = await storage.saveToLocal(saveable)
    if (!localResult.ok) return localResult

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

  const markWritten = (comparable: string) => {
    lastWrittenRef.current = comparable
    saveStatus.setState({ lastSavedAt: new Date().toISOString() })
  }

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

  const adoptServerSyncVersion = async (): Promise<Result<void, AppError>> => {
    const fetched = await syncAdapter.fetchFromServer(plannerId)
    if (!fetched.ok) return err(fetched.error)

    syncVersionRef.current = Math.max(syncVersionRef.current, fetched.value.ack.syncVersion)
    return ok(undefined)
  }

  const untitled = (): string => t('pages.plannerMD.untitled', 'Untitled')

  const localSide = (): SaveablePlanner => buildSaveable(getState(), 'saved', published)

  const failResolution = (failure: AppError): boolean => {
    reactToFailure(failure)
    setResolutionError(failure)
    if (isSyncConflict(failure)) {
      setError(failure)
    }
    return false
  }

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

      const fetched: { value: AcknowledgedPlanner | null } = { value: null }

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
          if (!isAuthenticated) return ok(null)

          const synced = await syncToServer(planner, force ? 'forcePush' : 'forceSync')
          if (!synced.ok) return err(synced.error)

          if (planner.metadata.id === plannerId) adoptAck(synced.value.ack)
          return ok(acknowledgedCopy(synced.value))
        },
        sanitizeTitle: (title) => title.trim() || untitled(),
      }

      const resolved = await interpretConflictPlan(plan, ops, ctx)
      if (!resolved.ok) return failResolution(resolved.error.error)

      if (fetched.value) {
        adoptAck(fetched.value.ack)
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

      void queryClient.invalidateQueries({ queryKey: plannerQueryKeys.detail(plannerId) })
      void queryClient.invalidateQueries({ queryKey: userPlannersQueryKeys.all })

      const forkedId = forkedPlannerId(plan)
      if (forkedId !== null && onKeepBothCreated) onKeepBothCreated(forkedId)

      heldPlan.current = null
      setError(null)
      return true
    } catch (failure: unknown) {
      return failResolution(classifyAppError(failure))
    } finally {
      endSave()
    }
  }

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

    if (!baselineSettledRef.current) {
      baselineSettledRef.current = true
      lastWrittenRef.current = stateToComparableString(getStateRef.current())
    }

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
