/**
 * MD User Planners Data Hook
 *
 * Fetches personal planners for the /planner/md route.
 * - Guests: IndexedDB only via usePlannerSaveAdapter
 * - Authenticated + sync ON: Auto-pull from server when:
 *   - Planner doesn't exist locally (server-only)
 *   - Local is not a draft AND local syncVersion < server syncVersion
 * - Drafts are never overwritten by server pulls
 *
 * Pattern: Split adapters + TanStack Query
 */

import { useMemo, useEffect, useRef, useState } from 'react'
import { useSuspenseQuery, useQuery, queryOptions, useQueryClient } from '@tanstack/react-query'

import { usePlannerSaveAdapter } from './usePlannerSaveAdapter'
import { usePlannerSyncAdapter } from './usePlannerSyncAdapter'
import { useAuthQuery } from '@/shared/auth'
import { useUserSettingsQuery } from '@/pages/settings'
import { useEGOGiftListData } from '@/pages/egoGift'
import { validatePlannerForDraftSave, validatePlannerForPublish } from '../lib/plannerValidation'
import { toUserFriendlyError } from '../lib/plannerValidationErrors'

import { matchesPlannerFilters } from '../lib/plannerContentExtractors'

import type { PlannerSummary, SaveablePlanner, MDPlannerContent } from '../types/PlannerTypes'
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
// Local-vs-server reconciliation
// ============================================================================

/**
 * Decide whether a local planner that the server no longer has should be
 * purged from IndexedDB. Defaults to the safe choice (keep) whenever local
 * state is ambiguous or inconsistent.
 *
 * Purge only when two independent fields agree the row was previously saved
 * to the server (status='saved' AND savedAt is set). All other shapes are
 * preserved — drafts, never-synced rows, and inconsistent local state.
 */
export function shouldPurgeLocal(local: PlannerSummary): boolean {
  if (local.savedAt === null) return false
  if (local.status === 'draft') return false
  return true
}

// ============================================================================
// Hook Options Interface
// ============================================================================

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
  /** Resolve batch conflicts - call after user chooses resolutions */
  resolveConflicts: (resolutions: ConflictResolution[]) => Promise<void>
  /** Whether conflict resolution is in progress */
  isResolvingConflicts: boolean
  /** Validation or sync error from last conflict resolution attempt */
  conflictResolutionError: { key: string; params?: Record<string, string> } | null
}

// ============================================================================
// Constants
// ============================================================================

const PAGE_SIZE = 20

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
  const saveAdapter = usePlannerSaveAdapter()
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
  const [conflictResolutionError, setConflictResolutionError] = useState<{
    key: string
    params?: Record<string, string>
  } | null>(null)

  // EGO Gift spec for affordability validation in conflict resolution
  const { spec: egoGiftSpec, i18n: egoGiftI18n } = useEGOGiftListData()

  // Query: Local planners only (fast initial render)
  const { data: allPlanners } = useSuspenseQuery(
    queryOptions({
      queryKey: userPlannersQueryKeys.list(isAuthenticated),
      queryFn: () => saveAdapter.listLocal(),
      staleTime: 30 * 1000,
    }),
  )

  // Query: Full planners with content (for content-based filtering)
  // Only fetched when content filters are active. For 1-5 plans, IndexedDB read is near-instant.
  const { data: allFullPlanners } = useQuery({
    queryKey: userPlannersQueryKeys.listFull(isAuthenticated),
    queryFn: () => saveAdapter.listLocalFull(),
    staleTime: 30 * 1000,
    enabled: hasContentFilters,
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
        const localPlanners = await saveAdapter.listLocal()
        const localMap = new Map(localPlanners.map((p) => [p.id, p]))
        const serverIds = new Set(serverPlanners.map((p) => p.id))

        // Categorize planners:
        // 1. Auto-pull: Server-only OR (local saved + server newer)
        // 2. Conflict: Local DRAFT + server newer → needs user decision
        // 3. Purge: Local-only AND already-server-saved → deleted on another device
        const plannersToPull: PlannerSummary[] = []
        const conflictServerPlanners: PlannerSummary[] = []
        const plannersToPurge: PlannerSummary[] = []

        for (const sp of serverPlanners) {
          const local = localMap.get(sp.id)
          if (!local) {
            // Server-only, always pull
            plannersToPull.push(sp)
            continue
          }

          const localVersion = local.syncVersion ?? 0
          const serverVersion = sp.syncVersion ?? 0

          if (serverVersion <= localVersion) {
            // Local is up-to-date or newer, skip
            continue
          }

          // Server is newer
          if (local.status === 'draft') {
            // CONFLICT: local draft vs server newer
            conflictServerPlanners.push(sp)
          } else {
            // Local is saved, safe to overwrite
            plannersToPull.push(sp)
          }
        }

        // Reconcile local-only rows: a planner present locally but absent on the
        // server was deleted on another device. Only purge when local state has
        // two independent witnesses that it was previously synced — status='saved'
        // AND savedAt is set. Drafts and never-synced rows are preserved.
        for (const local of localPlanners) {
          if (serverIds.has(local.id)) continue
          if (shouldPurgeLocal(local)) {
            plannersToPurge.push(local)
          }
        }

        // Mark as synced even if nothing to pull
        hasSyncedRef.current = true
        lastSyncKeyRef.current = syncKey

        // Pull non-conflicting planners automatically
        let syncedCount = 0
        for (const serverPlanner of plannersToPull) {
          try {
            const fullPlanner = await syncAdapter.fetchFromServer(serverPlanner.id)
            if (fullPlanner) {
              console.log(`Saving planner ${serverPlanner.id}:`, fullPlanner.metadata)
              const result = await saveAdapter.saveToLocal(fullPlanner)
              if (result.success) {
                syncedCount++
              } else {
                console.error(`Failed to save planner ${serverPlanner.id}:`, result.errorCode)
              }
            }
          } catch (error) {
            console.error(`Failed to fetch planner ${serverPlanner.id}:`, error)
          }
        }

        // Purge local rows the server no longer has. Idempotent IndexedDB delete.
        let purgedCount = 0
        for (const local of plannersToPurge) {
          try {
            await saveAdapter.deleteFromLocal(local.id)
            purgedCount++
          } catch (error) {
            console.error(`Failed to purge local planner ${local.id}:`, error)
          }
        }
        if (purgedCount > 0) {
          console.log(`Reconciled local IndexedDB: purged ${purgedCount} server-deleted planner(s)`)
        }

        // Build conflict items if any conflicts detected
        if (conflictServerPlanners.length > 0) {
          const conflicts: ConflictItem[] = []
          for (const sp of conflictServerPlanners) {
            try {
              const localPlanner = await saveAdapter.loadFromLocal(sp.id)
              const serverPlanner = await syncAdapter.fetchFromServer(sp.id)
              if (localPlanner && serverPlanner) {
                conflicts.push({
                  id: sp.id,
                  localPlanner,
                  serverPlanner,
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
          const updatedLocal = await saveAdapter.listLocal()
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
      } finally {
        setIsSyncing(false)
        syncInProgressRef.current = false
      }
    }

    void runSync()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [syncKey])

  // Memoize filtering to prevent recalculation on unrelated re-renders
  const { paginatedPlanners, totalCount } = useMemo(() => {
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
      const startIndex = page * PAGE_SIZE
      return {
        paginatedPlanners: filtered.slice(startIndex, startIndex + PAGE_SIZE),
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
    const startIndex = page * PAGE_SIZE
    return {
      paginatedPlanners: filtered.slice(startIndex, startIndex + PAGE_SIZE),
      totalCount: filtered.length,
    }
  }, [allPlanners, allFullPlanners, category, search, page, hasContentFilters, contentFilters])

  /**
   * Validate a local planner's content before syncing to server.
   * Mirrors performSave: strict when published, non-strict otherwise.
   * Throws a typed error if invalid, to be caught by resolveConflicts outer catch.
   */
  const validateBeforeSync = (planner: SaveablePlanner) => {
    if (planner.config.type !== 'MIRROR_DUNGEON' || !egoGiftSpec) return
    const content = planner.content as MDPlannerContent
    const { category } = planner.config
    const { title, published } = planner.metadata

    let friendlyError: { key: string; params?: Record<string, string> } | null = null

    if (published) {
      const { isValid, errors } = validatePlannerForPublish(
        title,
        content,
        category,
        egoGiftSpec,
        egoGiftI18n,
      )
      if (!isValid) friendlyError = toUserFriendlyError(errors[0])
    } else {
      friendlyError = validatePlannerForDraftSave(content, category, egoGiftSpec, egoGiftI18n)
    }

    if (friendlyError) {
      throw Object.assign(new Error('validationFailed'), {
        code: 'validationFailed',
        friendlyError,
      })
    }
  }

  /**
   * Resolve batch conflicts based on user choices
   * Pattern: mirrors usePlannerSave.resolveConflict() but for multiple items
   */
  const resolveConflicts = async (resolutions: ConflictResolution[]) => {
    if (resolutions.length === 0) return

    setIsResolvingConflicts(true)
    setConflictResolutionError(null)

    try {
      for (const resolution of resolutions) {
        const conflict = pendingConflicts.find((c) => c.id === resolution.id)
        if (!conflict) continue

        if (resolution.choice === 'overwrite') {
          // Validate before syncing local draft to server
          validateBeforeSync(conflict.localPlanner)
          // Keep local draft, force push to server
          await syncAdapter.syncToServer(conflict.localPlanner, true)
          // Update local with saved status
          const saved: SaveablePlanner = {
            ...conflict.localPlanner,
            metadata: {
              ...conflict.localPlanner.metadata,
              status: 'saved',
              savedAt: new Date().toISOString(),
            },
          }
          await saveAdapter.saveToLocal(saved)
        } else if (resolution.choice === 'discard') {
          // Use server version, discard local draft
          await saveAdapter.saveToLocal(conflict.serverPlanner)
        } else if (resolution.choice === 'both') {
          // Keep both: create copy of local, then use server for original
          const copyId = crypto.randomUUID()
          const deviceId = await saveAdapter.getOrCreateDeviceId()
          const copy: SaveablePlanner = {
            ...conflict.localPlanner,
            metadata: {
              ...conflict.localPlanner.metadata,
              id: copyId,
              title: `${conflict.localPlanner.metadata.title} (Copy)`,
              status: 'saved',
              syncVersion: 1,
              deviceId,
              createdAt: new Date().toISOString(),
              lastModifiedAt: new Date().toISOString(),
              savedAt: new Date().toISOString(),
            },
          }
          // Validate copy before syncing (same content as localPlanner)
          validateBeforeSync(copy)
          await saveAdapter.saveToLocal(copy)
          await syncAdapter.syncToServer(copy)
          // Use server version for original
          await saveAdapter.saveToLocal(conflict.serverPlanner)
        }
      }

      // Only clear conflicts after ALL resolutions succeed
      setPendingConflicts([])
      const updatedLocal = await saveAdapter.listLocal()
      queryClient.setQueryData(userPlannersQueryKeys.list(isAuthenticated), updatedLocal)
      void queryClient.invalidateQueries({
        queryKey: userPlannersQueryKeys.listFull(isAuthenticated),
      })
    } catch (error) {
      const e = error as {
        code?: string
        friendlyError?: { key: string; params?: Record<string, string> }
      }
      if (e.code === 'validationFailed' && e.friendlyError) {
        setConflictResolutionError(e.friendlyError)
      } else {
        console.error('Conflict resolution failed:', error)
      }
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
    conflictResolutionError,
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
 * await adapter.deletePlanner(id);
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
