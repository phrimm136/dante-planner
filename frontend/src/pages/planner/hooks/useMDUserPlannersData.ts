import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSuspenseQuery, useQuery, queryOptions, useQueryClient } from '@tanstack/react-query'

import { usePlannerStorage } from './usePlannerStorage'
import {
  usePlannerSyncAdapter,
  serverResponseToSaveable,
  acknowledgedCopy,
} from './usePlannerSyncAdapter'
import { plannerApi } from '../lib/plannerApi'
import { useAuthQuery } from '@/shared/auth'
import { useUserSettingsQuery } from '@/shared/userSettings'
import { useEGOGiftListSpec, useEGOGiftListI18n } from '@/pages/egoGift'
import { usePlannerIdRegistry } from './usePlannerIdRegistry'
import { validatePlannerForDraftSave, validatePlannerForPublish } from '../lib/plannerValidation'
import { plannerValidationError, toUserFriendlyError } from '../lib/plannerValidationErrors'
import { planConflictResolution, interpretConflictPlan } from '../lib/conflictChoice'
import {
  categorizeSync,
  collectSyncConflicts,
  pullServerPlanners,
  purgeLocalPlanners,
  settleTombstones,
} from '../lib/syncPlan'
import { classifyAppError } from '@/lib/apiErrorClassifier'
import { ok, err } from '@/lib/result'
import { generateUUID } from '@/lib/uuid'
import { PLANNER_LIST, STALE_TIME } from '@/lib/constants'

import { matchesPlannerFilters } from '../lib/plannerContentExtractors'

import { isMDPlanner } from '../types/PlannerTypes'
import type { AppError } from '@/lib/apiErrorClassifier'
import type { ConflictEffect, ConflictOps, ConflictOutcome } from '../lib/conflictChoice'
import type { SyncOps } from '../lib/syncPlan'
import type { PlannerSummary, SaveablePlanner } from '../types/PlannerTypes'
import type { PlannerSearchFilters } from '../types/PlannerSearchTypes'
import type { ConflictItem, ConflictResolution } from '../components/BatchConflictDialog'
import type { ConflictResolutionChoice } from '../types/PlannerTypes'
import type { MDCategory } from '@/shared/gameData'

export const userPlannersQueryKeys = {
  all: ['userPlanners'] as const,

  list: (isAuthenticated: boolean) =>
    [...userPlannersQueryKeys.all, 'list', { isAuthenticated }] as const,

  listFull: (isAuthenticated: boolean) =>
    [...userPlannersQueryKeys.all, 'listFull', { isAuthenticated }] as const,
}

export interface UseMDUserPlannersDataOptions {
  category?: MDCategory
  page: number
  search?: string
  contentFilters?: PlannerSearchFilters
}

export interface MDUserPlannersResult {
  planners: PlannerSummary[]
  totalCount: number
  isAuthenticated: boolean
  isSyncing: boolean
  pendingConflicts: ConflictItem[]
  resolveConflicts: (resolutions: ConflictResolution[]) => Promise<ConflictOutcome[]>
  isResolvingConflicts: boolean
}

export function useMDUserPlannersData(options: UseMDUserPlannersDataOptions): MDUserPlannersResult {
  const { category, page, search, contentFilters } = options

  const hasContentFilters = !!(
    contentFilters &&
    (contentFilters.keywords.length > 0 ||
      contentFilters.identityIds.length > 0 ||
      contentFilters.egoIds.length > 0 ||
      contentFilters.giftIds.length > 0 ||
      contentFilters.themePackIds.length > 0)
  )
  const { t } = useTranslation('planner')
  const storage = usePlannerStorage()
  const syncAdapter = usePlannerSyncAdapter()
  const queryClient = useQueryClient()
  const { data: user } = useAuthQuery()
  const { data: settings } = useUserSettingsQuery()
  const isAuthenticated = !!user
  const syncEnabled = settings?.syncEnabled === true

  const [isSyncing, setIsSyncing] = useState(false)
  const syncInProgressRef = useRef(false)
  const hasSyncedRef = useRef(false)
  const lastSyncKeyRef = useRef('')

  const [pendingConflicts, setPendingConflicts] = useState<ConflictItem[]>([])
  const [isResolvingConflicts, setIsResolvingConflicts] = useState(false)

  const heldPlans = useRef(
    new Map<string, { choice: ConflictResolutionChoice; plan: ConflictEffect[] }>(),
  )

  const egoGiftSpec = useEGOGiftListSpec()
  const egoGiftI18n = useEGOGiftListI18n()
  const idRegistryFor = usePlannerIdRegistry()

  const { data: allPlanners } = useSuspenseQuery(
    queryOptions({
      queryKey: userPlannersQueryKeys.list(isAuthenticated),
      queryFn: () => storage.listLocal(),
      staleTime: STALE_TIME.FREQUENT,
      refetchOnWindowFocus: false,
    }),
  )

  const { data: allFullPlanners } = useQuery({
    queryKey: userPlannersQueryKeys.listFull(isAuthenticated),
    queryFn: () => storage.listLocalFull(),
    staleTime: STALE_TIME.FREQUENT,
    enabled: hasContentFilters,
    refetchOnWindowFocus: false,
  })

  const syncKey = `${isAuthenticated}-${syncEnabled}`

  useEffect(() => {
    if (!isAuthenticated || !syncEnabled) return
    if (syncInProgressRef.current) return

    if (hasSyncedRef.current && lastSyncKeyRef.current === syncKey) return

    const runSync = async () => {
      syncInProgressRef.current = true
      setIsSyncing(true)

      const ops: SyncOps = {
        fetchChunks: (ids) => plannerApi.batchChunks(ids),
        toSaveable: serverResponseToSaveable,
        saveLocal: storage.saveToLocal,
        deleteLocal: storage.deleteFromLocal,
        loadLocal: storage.loadFromLocal,
        fetchServer: syncAdapter.fetchFromServer,
        deleteServer: syncAdapter.deleteFromServer,
        clearTombstone: storage.clearTombstone,
      }

      try {
        const [serverPlanners, localPlanners, tombstones] = await Promise.all([
          syncAdapter.listFromServer(),
          storage.listLocal(),
          storage.listTombstones(),
        ])

        const plan = categorizeSync(serverPlanners, localPlanners, tombstones)

        hasSyncedRef.current = true
        lastSyncKeyRef.current = syncKey

        const [, syncedCount] = await Promise.all([
          settleTombstones(plan, ops),
          pullServerPlanners(
            plan.pull.map((p) => p.id),
            ops,
          ),
        ])
        const purgedCount = await purgeLocalPlanners(plan.purge, ops)

        if (plan.conflict.length > 0) {
          const conflicts = await collectSyncConflicts(plan.conflict, ops)
          if (conflicts.length > 0) {
            heldPlans.current.clear()
            setPendingConflicts(conflicts)
          }
        }

        if (syncedCount > 0 || purgedCount > 0) {
          const updatedLocal = await storage.listLocal()
          queryClient.setQueryData(userPlannersQueryKeys.list(isAuthenticated), updatedLocal)
          void queryClient.invalidateQueries({
            queryKey: userPlannersQueryKeys.listFull(isAuthenticated),
          })
        }
      } catch (error) {
        console.error('Background sync failed:', error)
        hasSyncedRef.current = true
        lastSyncKeyRef.current = syncKey
      }

      setIsSyncing(false)
      syncInProgressRef.current = false
    }

    void runSync()
  }, [syncKey, syncAdapter, storage, queryClient, syncEnabled, isAuthenticated])

  const { paginatedPlanners, totalCount } = (() => {
    const normalizedSearch = search?.toLowerCase().trim()

    if (hasContentFilters) {
      if (!allFullPlanners) {
        return { paginatedPlanners: [], totalCount: 0 }
      }

      const matchedIds = new Set(
        allFullPlanners
          .filter((p) => {
            if (category && p.config.category !== category) return false
            return matchesPlannerFilters(p, contentFilters!)
          })
          .map((p) => p.metadata.id),
      )

      const filtered = allPlanners.filter((p) => matchedIds.has(p.id))
      const startIndex = page * PLANNER_LIST.PAGE_SIZE
      return {
        paginatedPlanners: filtered.slice(startIndex, startIndex + PLANNER_LIST.PAGE_SIZE),
        totalCount: filtered.length,
      }
    }

    const filtered = allPlanners.filter((p) => {
      if (category && p.category !== category) return false
      if (normalizedSearch && !p.title.toLowerCase().includes(normalizedSearch)) return false
      return true
    })

    const startIndex = page * PLANNER_LIST.PAGE_SIZE
    return {
      paginatedPlanners: filtered.slice(startIndex, startIndex + PLANNER_LIST.PAGE_SIZE),
      totalCount: filtered.length,
    }
  })()

  const validateBeforeSync = (planner: SaveablePlanner): AppError | null => {
    if (!isMDPlanner(planner)) return null
    if (!egoGiftSpec) return { kind: 'retryable' }

    const { content } = planner
    const { category } = planner.config
    const { title, published } = planner.metadata
    const registry = idRegistryFor(planner.metadata.contentVersion)

    if (published) {
      const { errors } = validatePlannerForPublish(
        title,
        content,
        category,
        egoGiftSpec,
        egoGiftI18n,
        registry,
      )
      const [firstError] = errors
      return firstError ? plannerValidationError(toUserFriendlyError(firstError)) : null
    }

    const friendlyError = validatePlannerForDraftSave(
      content,
      category,
      egoGiftSpec,
      egoGiftI18n,
      registry,
    )
    return friendlyError ? plannerValidationError(friendlyError) : null
  }

  const conflictOps = (conflict: ConflictItem): ConflictOps => ({
    local: async () => {
      const loaded = await storage.loadFromLocal(conflict.id)
      if (!loaded.ok) return err({ kind: 'unknown' })
      return loaded.value ? ok(loaded.value) : err({ kind: 'notFound' })
    },
    incoming: async () => {
      const fetched = await syncAdapter.fetchFromServer(conflict.id)
      return fetched.ok ? ok(fetched.value.planner) : err(fetched.error)
    },
    validate: validateBeforeSync,
    saveLocal: storage.saveToLocal,
    deleteLocal: storage.deleteFromLocal,
    deleteRemote: syncAdapter.deleteFromServer,
    sync: async (planner, force) => {
      try {
        return ok(acknowledgedCopy(await syncAdapter.syncToServer(planner, force)))
      } catch (failure: unknown) {
        return err(classifyAppError(failure))
      }
    },
    sanitizeTitle: (title) => title.trim() || t('pages.plannerMD.untitled', 'Untitled'),
  })

  const resolveConflicts = async (
    resolutions: ConflictResolution[],
  ): Promise<ConflictOutcome[]> => {
    const [first] = resolutions
    if (!first) return []

    setIsResolvingConflicts(true)

    try {
      const outcomes: ConflictOutcome[] = []
      const resolved = new Set<string>()

      for (const resolution of resolutions) {
        const conflict = pendingConflicts.find((c) => c.id === resolution.id)
        if (!conflict) continue

        const ctx = { now: new Date().toISOString(), newId: generateUUID }
        const held = heldPlans.current.get(conflict.id)
        const plan =
          held?.choice === resolution.choice
            ? held.plan
            : planConflictResolution(
                resolution.choice,
                { forkSide: 'local', forkTitle: conflict.localPlanner.metadata.title },
                {
                  ...ctx,
                  copyTitle: (title: string) =>
                    t('pages.plannerMD.conflict.copySuffix', '{{title}} (Copy)', { title }),
                },
              )
        heldPlans.current.set(conflict.id, { choice: resolution.choice, plan })

        const result = await interpretConflictPlan(plan, conflictOps(conflict), ctx)
        outcomes.push({ id: conflict.id, result })
        if (!result.ok) break

        resolved.add(conflict.id)
      }

      setPendingConflicts((pending) => pending.filter((conflict) => !resolved.has(conflict.id)))
      for (const id of resolved) heldPlans.current.delete(id)

      const updatedLocal = await storage.listLocal()
      queryClient.setQueryData(userPlannersQueryKeys.list(isAuthenticated), updatedLocal)
      void queryClient.invalidateQueries({
        queryKey: userPlannersQueryKeys.listFull(isAuthenticated),
      })

      return outcomes
    } finally {
      setIsResolvingConflicts(false)
    }
  }

  return {
    planners: paginatedPlanners,
    totalCount,
    isAuthenticated,
    isSyncing,
    pendingConflicts,
    resolveConflicts,
    isResolvingConflicts,
  }
}
