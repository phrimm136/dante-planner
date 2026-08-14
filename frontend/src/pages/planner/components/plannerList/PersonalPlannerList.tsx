import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { useMDUserPlannersData } from '../../hooks/useMDUserPlannersData'
import { useUserSettingsQuery } from '@/pages/settings'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { CARD_GRID, PROGRESSIVE_REVEAL, calculatePlannerPages } from '@/lib/constants'

import { PersonalPlannerCard } from './PersonalPlannerCard'
import { PlannerListPagination } from './PlannerListPagination'
import { PlannerEmptyState } from './PlannerEmptyState'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'
import { Button } from '@/components/ui/button'
import { BatchConflictDialog } from '../BatchConflictDialog'

import type { ConflictResolution } from '../BatchConflictDialog'
import type { ConflictOutcome } from '../../lib/conflictChoice'
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
  } = useMDUserPlannersData({
    category,
    page,
    search: search || undefined,
    contentFilters,
  })

  const { t } = useTranslation('planner')

  // What the last submission did to each item, shown against its own row.
  const [outcomes, setOutcomes] = useState<ConflictOutcome[]>([])

  // Dismissal parks the batch: the conflicts are still pending, and the reopen
  // affordance below is what keeps them reachable.
  const [dismissed, setDismissed] = useState(false)

  /**
   * Which batch the dialog is mounted for.
   *
   * It advances only when a batch arrives, never as one shrinks: keying on the
   * surviving ids would remount on every partial success and reset the choices
   * the user made on the rows that failed back to the destructive default.
   */
  const [batchEpoch, setBatchEpoch] = useState(0)
  const wasPending = useRef(false)

  useEffect(() => {
    const pending = pendingConflicts.length > 0
    if (pending && !wasPending.current) {
      setBatchEpoch((current) => current + 1)
      setDismissed(false)
      setOutcomes([])
    }
    wasPending.current = pending
  }, [pendingConflicts])
  // TODO: Add UI indicator when isSyncing is true
  void isSyncing

  const { data: userSettings } = useUserSettingsQuery()
  const syncEnabled = userSettings?.syncEnabled

  // Progressive rendering: start with one batch, add a batch per frame
  const displayCount = useProgressiveCount({
    total: planners.length,
    step: PROGRESSIVE_REVEAL.CARD_BATCH,
    initial: PROGRESSIVE_REVEAL.CARD_BATCH,
  })

  // Determine if any filters are active (for empty state messaging)
  const hasActiveFilters =
    !!category ||
    !!search ||
    !!(
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

  const submitResolutions = async (resolutions: ConflictResolution[]) => {
    setOutcomes(await resolveConflicts(resolutions))
  }

  return (
    <>
      {/* Batch conflict dialog for sync conflicts (local draft vs server newer).
          A sibling of both branches: filtered to nothing, the list would
          otherwise return before the conflicts were ever rendered. */}
      <BatchConflictDialog
        key={batchEpoch}
        open={pendingConflicts.length > 0 && !dismissed}
        conflicts={pendingConflicts}
        onResolve={(resolutions) => void submitResolutions(resolutions)}
        isResolving={isResolvingConflicts}
        outcomes={outcomes}
        onDismiss={() => setDismissed(true)}
      />

      {pendingConflicts.length > 0 && dismissed && (
        <div className="mb-4">
          <Button variant="outline" onClick={() => setDismissed(false)}>
            {t('pages.plannerMD.batchConflict.reopen', '{{count}} unresolved conflicts', {
              count: pendingConflicts.length,
            })}
          </Button>
        </div>
      )}

      {planners.length === 0 ? (
        <PlannerEmptyState view="my-plans" isFiltered={hasActiveFilters} />
      ) : (
        <>
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
      )}
    </>
  )
}
