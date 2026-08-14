/**
 * MD User Planners Data Hook
 *
 * Fetches personal planners for the /planner/md route.
 * - Guests: IndexedDB only via usePlannerStorage
 * - Authenticated + sync ON: Auto-pull from server when:
 *   - Planner doesn't exist locally (server-only)
 *   - Local is not a draft AND local syncVersion < server syncVersion
 * - Drafts are never overwritten by server pulls
 *
 * Pattern: Split adapters + TanStack Query
 */

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
import { useUserSettingsQuery } from '@/pages/settings'
import { useEGOGiftListData } from '@/pages/egoGift'
import { validatePlannerForDraftSave, validatePlannerForPublish } from '../lib/plannerValidation'
import { toUserFriendlyError } from '../lib/plannerValidationErrors'
import { planConflictResolution, interpretConflictPlan } from '../lib/conflictChoice'
import { categorizeSync } from '../lib/syncPlan'
import { classifyAppError, validationAppError } from '@/lib/apiErrorClassifier'
import { ok, err } from '@/lib/result'
import { generateUUID } from '@/lib/uuid'
import { PLANNER_LIST, STALE_TIME } from '@/lib/constants'

import { matchesPlannerFilters } from '../lib/plannerContentExtractors'

import { isMDPlanner } from '../types/PlannerTypes'
import type { AppError } from '@/lib/apiErrorClassifier'
import type { ConflictOps, ConflictOutcome } from '../lib/conflictChoice'
import type { PlannerSummary, SaveablePlanner } from '../types/PlannerTypes'
import type { PlannerSearchFilters } from '../types/PlannerSearchTypes'
import type { ConflictItem, ConflictResolution } from '../components/BatchConflictDialog'
import type { MDCategory } from '@/shared/gameData'

// ============================================================================
// Query Keys
// ============================================================================

/**
 * Query key factory for user planner queries
 * Includes auth state to invalidate cache on login/logout
 *
 * Note: syncEnabled is NOT in the key - it only affects background sync,
 * not the query result (IndexedDB data is the same regardless)
 */
export const userPlannersQueryKeys = {
  /** Base key for all user planner queries */
  all: ['userPlanners'] as const,

  /** Key for user's planner list (auth state for cache separation on login/logout) */
  list: (isAuthenticated: boolean) =>
    [...userPlannersQueryKeys.all, 'list', { isAuthenticated }] as const,

  /** Key for user's full planner list with content (for content-based filtering) */
  listFull: (isAuthenticated: boolean) =>
    [...userPlannersQueryKeys.all, 'listFull', { isAuthenticated }] as const,
}

// ============================================================================
// Hook Options Interface
// ============================================================================

/** Planner validators name their keys inside the planner namespace. */
function plannerValidationError(friendly: { key: string; params?: Record<string, string> }) {
  return validationAppError({ key: `planner:${friendly.key}`, params: friendly.params })
}

export interface UseMDUserPlannersDataOptions {
  /** MD category filter (optional) */
  category?: MDCategory
  /** Current page number (0-indexed) */
  page: number
  /** Search query for title filtering (optional) */
  search?: string
  /** Content search filters for local filtering (optional) */
  contentFilters?: PlannerSearchFilters
}

// ============================================================================
// Hook Return Type
// ============================================================================

export interface MDUserPlannersResult {
  /** Filtered planners for current page */
  planners: PlannerSummary[]
  /** Total number of planners (after category filter) */
  totalCount: number
  /** Whether user is authenticated */
  isAuthenticated: boolean
  /** Whether background sync is in progress */
  isSyncing: boolean
  /** Pending conflicts that need user resolution (local draft vs server newer) */
  pendingConflicts: ConflictItem[]
  /** Resolve batch conflicts - call after user chooses resolutions, one outcome per attempt */
  resolveConflicts: (resolutions: ConflictResolution[]) => Promise<ConflictOutcome[]>
  /** Whether conflict resolution is in progress */
  isResolvingConflicts: boolean
}

// ============================================================================
// Main Hook
// ============================================================================

/**
 * Hook that fetches user's personal planners
 * Suspends while loading - wrap in Suspense boundary
 *
 * Data source is automatically selected based on authentication:
 * - Guest: IndexedDB via usePlannerStorage
 * - Authenticated: Server API via usePlannerSync
 *
 * Note: Client-side filtering and pagination (not server-side for local data)
 *
 * @param options - Category filter and pagination options
 * @returns Personal planners with metadata
 *
 * @example
 * ```tsx
 * function MyPlansList() {
 *   const { planners, totalCount, isAuthenticated } = useMDUserPlannersData({
 *     category: '5F',
 *     page: 0,
 *   });
 *
 *   return (
 *     <div>
 *       {isAuthenticated ? 'Cloud synced' : 'Local only'}
 *       <PlannerGrid planners={planners} />
 *     </div>
 *   );
 * }
 * ```
 */
export function useMDUserPlannersData(options: UseMDUserPlannersDataOptions): MDUserPlannersResult {
  const { category, page, search, contentFilters } = options

  // Whether content-based filters are active (requires full planner data)
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

  // Sync state
  const [isSyncing, setIsSyncing] = useState(false)
  const syncInProgressRef = useRef(false)
  const hasSyncedRef = useRef(false)
  const lastSyncKeyRef = useRef('')

  // Conflict resolution state
  const [pendingConflicts, setPendingConflicts] = useState<ConflictItem[]>([])
  const [isResolvingConflicts, setIsResolvingConflicts] = useState(false)

  // EGO Gift spec for affordability validation in conflict resolution
  const { spec: egoGiftSpec, i18n: egoGiftI18n } = useEGOGiftListData()

  // Query: Local planners only (fast initial render)
  const { data: allPlanners } = useSuspenseQuery(
    queryOptions({
      queryKey: userPlannersQueryKeys.list(isAuthenticated),
      queryFn: () => storage.listLocal(),
      staleTime: STALE_TIME.FREQUENT,
      // The source is local storage, so regaining focus says nothing about it.
      refetchOnWindowFocus: false,
    }),
  )

  // Query: Full planners with content (for content-based filtering)
  // Only fetched when content filters are active. For 1-5 plans, IndexedDB read is near-instant.
  const { data: allFullPlanners } = useQuery({
    queryKey: userPlannersQueryKeys.listFull(isAuthenticated),
    queryFn: () => storage.listLocalFull(),
    staleTime: STALE_TIME.FREQUENT,
    enabled: hasContentFilters,
    // The source is local storage, so regaining focus says nothing about it.
    refetchOnWindowFocus: false,
  })

  // Background sync: Pull missing planners from server
  // Uses syncKey to run once per auth+sync state change
  const syncKey = `${isAuthenticated}-${syncEnabled}`

  useEffect(() => {
    if (!isAuthenticated || !syncEnabled) return
    if (syncInProgressRef.current) return

    // Skip if already synced for this state
    if (hasSyncedRef.current && lastSyncKeyRef.current === syncKey) return

    const runSync = async () => {
      syncInProgressRef.current = true
      setIsSyncing(true)

      try {
        // Fetch ALL server planner metadata
        const serverPlanners = await syncAdapter.listFromServer()
        const localPlanners = await storage.listLocal()

        const plan = categorizeSync(serverPlanners, localPlanners)

        // Mark as synced even if nothing to pull
        hasSyncedRef.current = true
        lastSyncKeyRef.current = syncKey

        // Pull non-conflicting planners in chunked round trips. Each chunk is
        // written as it arrives, so a later chunk failing keeps what came before;
        // the response is not positionally aligned, so omitted rows stay local-untouched.
        let syncedCount = 0
        const pullIds = plan.pull.map((p) => p.id)
        if (pullIds.length > 0) {
          try {
            for await (const chunk of plannerApi.batchChunks(pullIds)) {
              for (const response of chunk) {
                try {
                  const result = await storage.saveToLocal(serverResponseToSaveable(response))
                  if (result.ok) {
                    syncedCount++
                  } else {
                    console.error(`Failed to save planner ${response.id}:`, result.error)
                  }
                } catch (error) {
                  console.error(`Failed to save planner ${response.id}:`, error)
                }
              }
            }
          } catch (error) {
            console.error('Stopped fetching planners for sync:', error)
          }
        }

        // Purge local rows the server no longer has. Idempotent IndexedDB delete.
        let purgedCount = 0
        for (const local of plan.purge) {
          try {
            await storage.deleteFromLocal(local.id)
            purgedCount++
          } catch (error) {
            console.error(`Failed to purge local planner ${local.id}:`, error)
          }
        }
        if (purgedCount > 0) {
          console.log(`Reconciled local IndexedDB: purged ${purgedCount} server-deleted planner(s)`)
        }

        // Build conflict items if any conflicts detected
        if (plan.conflict.length > 0) {
          const conflicts: ConflictItem[] = []
          for (const sp of plan.conflict) {
            try {
              const localPlanner = await storage.loadFromLocal(sp.id)
              const serverPlanner = await syncAdapter.fetchFromServer(sp.id)
              if (localPlanner.ok && localPlanner.value && serverPlanner.ok) {
                conflicts.push({
                  id: sp.id,
                  localPlanner: localPlanner.value,
                  serverPlanner: serverPlanner.value.planner,
                })
              }
            } catch (error) {
              console.error(`Failed to load conflict planners for ${sp.id}:`, error)
            }
          }
          if (conflicts.length > 0) {
            setPendingConflicts(conflicts)
          }
        }

        // Directly update cache with synced planners (avoids re-suspension)
        if (syncedCount > 0 || purgedCount > 0) {
          const updatedLocal = await storage.listLocal()
          queryClient.setQueryData(userPlannersQueryKeys.list(isAuthenticated), updatedLocal)
          // Invalidate full planners cache so content filters pick up synced data
          void queryClient.invalidateQueries({
            queryKey: userPlannersQueryKeys.listFull(isAuthenticated),
          })
        }
      } catch (error) {
        console.error('Background sync failed:', error)
        // Mark as synced even on error - no retry
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

    // When content filters are active, filter against full planners using matchesPlannerFilters
    // then map matched IDs back to summaries for consistent return type
    if (hasContentFilters) {
      // While full planners are loading, show empty to avoid flash of unfiltered results
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

    // Client-side filtering by category and search (no content filters)
    const filtered = allPlanners.filter((p) => {
      if (category && p.category !== category) return false
      if (normalizedSearch && !p.title.toLowerCase().includes(normalizedSearch)) return false
      return true
    })

    // Client-side pagination
    const startIndex = page * PLANNER_LIST.PAGE_SIZE
    return {
      paginatedPlanners: filtered.slice(startIndex, startIndex + PLANNER_LIST.PAGE_SIZE),
      totalCount: filtered.length,
    }
  })()

  /**
   * Why a local planner may not be synced, or null when it may be.
   * Mirrors performSave: strict when published, structural checks otherwise.
   */
  const validateBeforeSync = (planner: SaveablePlanner): AppError | null => {
    if (!isMDPlanner(planner) || !egoGiftSpec) return null

    const { content } = planner
    const { category } = planner.config
    const { title, published } = planner.metadata

    if (published) {
      const { isValid, errors } = validatePlannerForPublish(
        title,
        content,
        category,
        egoGiftSpec,
        egoGiftI18n,
      )
      return isValid ? null : plannerValidationError(toUserFriendlyError(errors[0]))
    }

    const friendlyError = validatePlannerForDraftSave(content, category, egoGiftSpec, egoGiftI18n)
    return friendlyError ? plannerValidationError(friendlyError) : null
  }

  /** The effects one conflict's resolution runs against storage and the server. */
  const conflictOps = (conflict: ConflictItem): ConflictOps => ({
    local: () => conflict.localPlanner,
    incoming: async () => ok(conflict.serverPlanner),
    validate: validateBeforeSync,
    saveLocal: storage.saveToLocal,
    deleteLocal: storage.deleteFromLocal,
    sync: async (planner, force) => {
      try {
        return ok(acknowledgedCopy(await syncAdapter.syncToServer(planner, force)))
      } catch (failure: unknown) {
        return err(classifyAppError(failure))
      }
    },
    sanitizeTitle: (title) => title.trim() || t('pages.plannerMD.untitled', 'Untitled'),
  })

  /**
   * Resolve batch conflicts based on user choices.
   *
   * Stops at the first failure: the resolutions after it are the user's to
   * re-submit once they know what went wrong, and only what resolved leaves the
   * pending list.
   */
  const resolveConflicts = async (
    resolutions: ConflictResolution[],
  ): Promise<ConflictOutcome[]> => {
    const [first] = resolutions
    if (!first) return []

    setIsResolvingConflicts(true)

    // The copy is stamped with this device, and the id lives in the same store
    // the copy would be written to.
    const deviceId = await storage.getOrCreateDeviceId()
    if (!deviceId.ok) {
      setIsResolvingConflicts(false)
      return [{ id: first.id, result: err({ step: 'saveLocal', error: { kind: 'unknown' } }) }]
    }

    const outcomes: ConflictOutcome[] = []
    const resolved = new Set<string>()

    for (const resolution of resolutions) {
      const conflict = pendingConflicts.find((c) => c.id === resolution.id)
      if (!conflict) continue

      const ctx = { deviceId: deviceId.value, now: new Date().toISOString(), newId: generateUUID }
      const plan = planConflictResolution(
        resolution.choice,
        { forkSide: 'local', forkTitle: conflict.localPlanner.metadata.title },
        {
          ...ctx,
          copyTitle: (title: string) =>
            t('pages.plannerMD.conflict.copySuffix', '{{title}} (Copy)', { title }),
        },
      )

      const result = await interpretConflictPlan(plan, conflictOps(conflict), ctx)
      outcomes.push({ id: conflict.id, result })
      if (!result.ok) break

      resolved.add(conflict.id)
    }

    setPendingConflicts((pending) => pending.filter((conflict) => !resolved.has(conflict.id)))

    const updatedLocal = await storage.listLocal()
    queryClient.setQueryData(userPlannersQueryKeys.list(isAuthenticated), updatedLocal)
    void queryClient.invalidateQueries({
      queryKey: userPlannersQueryKeys.listFull(isAuthenticated),
    })

    setIsResolvingConflicts(false)
    return outcomes
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

// ============================================================================
// Cache Invalidation Helper
// ============================================================================

/**
 * Hook to get invalidation function for user planners cache
 * Use after planner create/update/delete operations
 *
 * @example
 * ```tsx
 * const invalidate = useInvalidateUserPlanners();
 * await storage.deleteFromLocal(id);
 * invalidate();
 * ```
 */
export function useInvalidateUserPlanners() {
  const queryClient = useQueryClient()

  return () => {
    void queryClient.invalidateQueries({
      queryKey: userPlannersQueryKeys.all,
    })
  }
}
