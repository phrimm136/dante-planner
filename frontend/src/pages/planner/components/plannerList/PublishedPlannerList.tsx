import { Link, useSearch } from '@tanstack/react-router'

import { useMDGesellschaftData } from '../../hooks/useMDGesellschaftData'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { CARD_GRID, PROGRESSIVE_REVEAL } from '@/lib/constants'

import { PublishedPlannerCard } from './PublishedPlannerCard'
import { PlannerListPagination } from './PlannerListPagination'
import { PlannerEmptyState } from './PlannerEmptyState'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'

import type { MDCategory } from '@/shared/gameData'
import type { MDGesellschaftMode } from '../../types/MDPlannerListTypes'

export interface PublishedPlannerListProps {
  /** Display mode: 'published' (all) or 'best' (recommended only) */
  mode: MDGesellschaftMode
  /** MD category filter (optional) */
  category?: MDCategory
  /** Current page number (0-indexed) */
  page: number
  /** Search query for title filtering (optional) */
  search?: string
  /** Comma-separated keyword filter */
  keyword?: string
  /** Comma-separated identity ID filter */
  identity?: string
  /** Comma-separated EGO ID filter */
  ego?: string
  /** Comma-separated gift ID filter */
  gift?: string
  /** Comma-separated theme pack ID filter */
  themePack?: string
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
  keyword,
  identity,
  ego,
  gift,
  themePack,
  isAuthenticated,
  onPageChange,
}: PublishedPlannerListProps) {
  const { data } = useMDGesellschaftData({
    mode,
    page,
    category,
    search: search || undefined,
    keyword,
    identity,
    ego,
    gift,
    themePack,
  })

  const currentSearch = useSearch({ strict: false })

  // Progressive rendering: start with one batch, add a batch per frame
  const displayCount = useProgressiveCount({
    total: data.content.length,
    step: PROGRESSIVE_REVEAL.CARD_BATCH,
    initial: PROGRESSIVE_REVEAL.CARD_BATCH,
    resetKey: data.content,
  })

  // Determine if any filters are active (for empty state messaging)
  const hasActiveFilters =
    !!category ||
    !!search ||
    mode === 'best' ||
    !!keyword ||
    !!identity ||
    !!ego ||
    !!gift ||
    !!themePack

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
