import { useMDUserPlannersData } from '../../hooks/useMDUserPlannersData'
import { useUserSettingsQuery } from '@/pages/settings'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { CARD_GRID, PROGRESSIVE_REVEAL, calculatePlannerPages } from '@/lib/constants'

import { PersonalPlannerCard } from './PersonalPlannerCard'
import { PlannerListPagination } from './PlannerListPagination'
import { PlannerEmptyState } from './PlannerEmptyState'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import { BatchConflictDialog } from '../BatchConflictDialog'

import type { MDCategory } from '@/shared/gameData'
import type { PlannerSummary } from '../../types/PlannerTypes'
import type { PlannerSearchFilters } from '../../types/PlannerSearchTypes'

export interface PersonalPlannerListProps {
  /** MD category filter (optional) */
  category?: MDCategory
  /** Current page number (0-indexed) */
  page: number
  /** Search query for title filtering (optional) */
  search?: string
  /** Content search filters for local filtering (optional) */
  contentFilters?: PlannerSearchFilters
  /** Callback when page changes */
  onPageChange: (page: number) => void
}

/**
 * Personal planner list component with pagination
 * Displays user's personal planners (IndexedDB for guests, server for auth)
 *
 * Features:
 * - Progressive rendering (10 cards at a time)
 * - Batch conflict resolution dialog
 * - Pagination support
 * - Empty state handling
 *
 * Usage: List page (/planner/md) and detail page bottom section
 */
export function PersonalPlannerList({
  category,
  page,
  search,
  contentFilters,
  onPageChange,
}: PersonalPlannerListProps) {
  const {
    planners,
    totalCount,
    isAuthenticated,
    isSyncing,
    pendingConflicts,
    resolveConflicts,
    isResolvingConflicts,
    conflictResolutionError,
  } = useMDUserPlannersData({
    category,
    page,
    search: search || undefined,
    contentFilters,
  })
  // TODO: Add UI indicator when isSyncing is true
  void isSyncing

  const { data: userSettings } = useUserSettingsQuery()
  const syncEnabled = userSettings?.syncEnabled

  // Progressive rendering: start with one batch, add a batch per frame
  const displayCount = useProgressiveCount({
    total: planners.length,
    step: PROGRESSIVE_REVEAL.CARD_BATCH,
    initial: PROGRESSIVE_REVEAL.CARD_BATCH,
    resetKey: planners,
  })

  // Determine if any filters are active (for empty state messaging)
  const hasActiveFilters = !!category || !!search || !!(
    contentFilters &&
    (contentFilters.title !== null ||
      contentFilters.keywords.length > 0 ||
      contentFilters.identityIds.length > 0 ||
      contentFilters.egoIds.length > 0 ||
      contentFilters.giftIds.length > 0 ||
      contentFilters.themePackIds.length > 0)
  )

  // Calculate pagination
  const totalPages = calculatePlannerPages(totalCount)

  // Handle empty state
  if (planners.length === 0) {
    return <PlannerEmptyState view="my-plans" isFiltered={hasActiveFilters} />
  }

  return (
    <>
      {/* Batch conflict dialog for sync conflicts (local draft vs server newer) */}
      <BatchConflictDialog
        open={pendingConflicts.length > 0}
        conflicts={pendingConflicts}
        onResolve={resolveConflicts}
        isResolving={isResolvingConflicts}
        error={conflictResolutionError}
      />

      <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.PLANNER}>
        {planners.slice(0, displayCount).map((planner: PlannerSummary) => (
          <PersonalPlannerCard
            key={planner.id}
            planner={planner}
            isAuthenticated={isAuthenticated}
            syncEnabled={syncEnabled}
          />
        ))}
      </ResponsiveCardGrid>

      {totalPages > 1 && (
        <div className="mt-6">
          <PlannerListPagination
            currentPage={page}
            totalPages={totalPages}
            onPageChange={onPageChange}
          />
        </div>
      )}
    </>
  )
}
