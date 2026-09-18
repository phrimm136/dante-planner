import { Link, useSearch } from '@tanstack/react-router'

import { useMDGesellschaftData } from '../../hooks/useMDGesellschaftData'
import { useProgressiveCount } from '@/components/hooks/useProgressiveReveal'
import { PROGRESSIVE_REVEAL } from '@/lib/constants'
import { PLANNER_GEOMETRY } from '@/shared/cardLayout'

import { PublishedPlannerCard } from './PublishedPlannerCard'
import { PlannerListPagination } from './PlannerListPagination'
import { PlannerEmptyState } from './PlannerEmptyState'
import { ResponsiveCardGrid } from '@/components/layout/ResponsiveCardGrid'

import type { MDGesellschaftFilters } from '../../types/MDPlannerListTypes'

export interface PublishedPlannerListProps {
  filters: MDGesellschaftFilters
  isAuthenticated: boolean
  onPageChange: (page: number) => void
}

export function PublishedPlannerList({
  filters,
  isAuthenticated,
  onPageChange,
}: PublishedPlannerListProps) {
  const { category, keyword, identity, ego, gift, themePack, search } = filters
  const { data } = useMDGesellschaftData({
    page: filters.page,
    mode: filters.mode,
    ...(category !== undefined && { category }),
    ...(search ? { search } : {}),
    ...(keyword !== undefined && { keyword }),
    ...(identity !== undefined && { identity }),
    ...(ego !== undefined && { ego }),
    ...(gift !== undefined && { gift }),
    ...(themePack !== undefined && { themePack }),
  })

  const currentSearch = useSearch({ strict: false })

  const displayCount = useProgressiveCount({
    total: data.content.length,
    step: PROGRESSIVE_REVEAL.CARD_BATCH,
    initial: PROGRESSIVE_REVEAL.CARD_BATCH,
  })

  const hasActiveFilters =
    !!filters.category ||
    !!filters.search ||
    filters.mode === 'best' ||
    !!filters.keyword ||
    !!filters.identity ||
    !!filters.ego ||
    !!filters.gift ||
    !!filters.themePack

  if (data.content.length === 0) {
    return <PlannerEmptyState view="community" isFiltered={hasActiveFilters} />
  }

  return (
    <>
      <ResponsiveCardGrid size={PLANNER_GEOMETRY.size} rows="content">
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
