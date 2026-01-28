import { useState, useEffect } from 'react'
import { Link, useSearch } from '@tanstack/react-router'

import { useMDGesellschaftData } from '@/hooks/useMDGesellschaftData'
import { CARD_GRID } from '@/lib/constants'

import { PublishedPlannerCard } from './PublishedPlannerCard'
import { PlannerListPagination } from './PlannerListPagination'
import { PlannerEmptyState } from './PlannerEmptyState'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'

import type { MDCategory } from '@/lib/constants'
import type { MDGesellschaftMode } from '@/types/MDPlannerListTypes'

export interface PublishedPlannerListProps {
  /** Display mode: 'published' (all) or 'best' (recommended only) */
  mode: MDGesellschaftMode
  /** MD category filter (optional) */
  category?: MDCategory
  /** Current page number (0-indexed) */
  page: number
  /** Search query for title filtering (optional) */
  search?: string
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
  mode,
  category,
  page,
  search,
  isAuthenticated,
  onPageChange,
}: PublishedPlannerListProps) {
  const { data } = useMDGesellschaftData({
    mode,
    page,
    category,
    search: search || undefined,
  })

  const currentSearch = useSearch({ strict: false })

  // Progressive rendering: start with 10 cards, add more incrementally
  const [displayCount, setDisplayCount] = useState(10)

  // Reset display count when content changes
  useEffect(() => {
    setDisplayCount(10)
  }, [data.content])

  // Progressively render more cards (10 per frame)
  useEffect(() => {
    if (displayCount < data.content.length) {
      const rafId = requestAnimationFrame(() => {
        setDisplayCount((prev) => Math.min(prev + 10, data.content.length))
      })
      return () => cancelAnimationFrame(rafId)
    }
  }, [displayCount, data.content.length])

  // Determine if any filters are active (for empty state messaging)
  const hasActiveFilters = !!category || !!search || mode === 'best'

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
            <PublishedPlannerCard
              planner={planner}
              showBookmark={isAuthenticated}
            />
          </Link>
        ))}
      </ResponsiveCardGrid>

      {data.totalPages > 1 && (
        <div className="mt-6">
          <PlannerListPagination
            currentPage={page}
            totalPages={data.totalPages}
            onPageChange={onPageChange}
          />
        </div>
      )}
    </>
  )
}
