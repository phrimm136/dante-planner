import { Link, useSearch } from '@tanstack/react-router'

import { useMDGesellschaftData } from '../../hooks/useMDGesellschaftData'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { CARD_GRID, PROGRESSIVE_REVEAL } from '@/lib/constants'

import { PublishedPlannerCard } from './PublishedPlannerCard'
import { PlannerListPagination } from './PlannerListPagination'
import { PlannerEmptyState } from './PlannerEmptyState'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'

import type { MDGesellschaftFilters } from '../../types/MDPlannerListTypes'

export interface PublishedPlannerListProps {
  /** Every filter value the query runs under */
  filters: MDGesellschaftFilters
  /** Whether user is authenticated (for bookmark display) */
  isAuthenticated: boolean
  /** Callback when page changes */
  onPageChange: (page: number) => void
}

/**
 * Published planner list component with pagination
 * Displays community planners from API
 *
 * Features:
 * - Progressive rendering
 * - Direct navigation on click
 * - Pagination support
 * - Empty state handling
 *
 * Usage: Gesellschaft list page and detail page bottom section
 */
export function PublishedPlannerList({
  filters,
  isAuthenticated,
  onPageChange,
}: PublishedPlannerListProps) {
  const { data } = useMDGesellschaftData({
    ...filters,
    search: filters.search || undefined,
  })

  const currentSearch = useSearch({ strict: false })

  // Progressive rendering: start with one batch, add a batch per frame
  const displayCount = useProgressiveCount({
    total: data.content.length,
    step: PROGRESSIVE_REVEAL.CARD_BATCH,
    initial: PROGRESSIVE_REVEAL.CARD_BATCH,
  })

  // Determine if any filters are active (for empty state messaging)
  const hasActiveFilters =
    !!filters.category ||
    !!filters.search ||
    filters.mode === 'best' ||
    !!filters.keyword ||
    !!filters.identity ||
    !!filters.ego ||
    !!filters.gift ||
    !!filters.themePack

  // Handle empty state
  if (data.content.length === 0) {
    return <PlannerEmptyState view="community" isFiltered={hasActiveFilters} />
  }

  return (
    <>
      <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.PLANNER}>
        {data.content.slice(0, displayCount).map((planner) => (
          <Link
            key={planner.id}
            to="/planner/md/gesellschaft/$id"
            params={{ id: planner.id }}
            search={currentSearch}
            className="block"
          >
            <PublishedPlannerCard planner={planner} showBookmark={isAuthenticated} />
          </Link>
        ))}
      </ResponsiveCardGrid>

      {data.page.totalPages > 1 && (
        <div className="mt-6">
          <PlannerListPagination
            currentPage={filters.page}
            totalPages={data.page.totalPages}
            onPageChange={onPageChange}
          />
        </div>
      )}
    </>
  )
}
