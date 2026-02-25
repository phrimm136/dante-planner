import { useState, useEffect } from 'react'

import { useMDUserPlannersData } from '@/hooks/useMDUserPlannersData'
import { useUserSettingsQuery } from '@/hooks/useUserSettings'
import { CARD_GRID, calculatePlannerPages } from '@/lib/constants'

import { PersonalPlannerCard } from './PersonalPlannerCard'
import { PlannerListPagination } from './PlannerListPagination'
import { PlannerEmptyState } from './PlannerEmptyState'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'
import { BatchConflictDialog } from '@/components/dialogs/BatchConflictDialog'

import type { MDCategory } from '@/lib/constants'
import type { PlannerSummary } from '@/types/PlannerTypes'

export interface PersonalPlannerListProps {
  /** MD category filter (optional) */
  category?: MDCategory
  /** Current page number (0-indexed) */
  page: number
  /** Search query for title filtering (optional) */
  search?: string
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
  })
  // TODO: Add UI indicator when isSyncing is true
  void isSyncing

  const { data: userSettings } = useUserSettingsQuery()
  const syncEnabled = userSettings?.syncEnabled

  // Progressive rendering: start with 10 cards, add more incrementally
  const [displayCount, setDisplayCount] = useState(10)

  // Reset display count when planners change
  useEffect(() => {
    setDisplayCount(10)
  }, [planners])

  // Progressively render more cards (10 per frame)
  useEffect(() => {
    if (displayCount < planners.length) {
      const rafId = requestAnimationFrame(() => {
        setDisplayCount((prev) => Math.min(prev + 10, planners.length))
      })
      return () => cancelAnimationFrame(rafId)
    }
  }, [displayCount, planners.length])

  // Determine if any filters are active (for empty state messaging)
  const hasActiveFilters = !!category || !!search

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
