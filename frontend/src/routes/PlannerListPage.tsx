/**
 * PlannerListPage - Planner browser with tabs for My Plans and Community views
 *
 * Features:
 * - Tab switching between My Plans / Community
 * - Category filter pills (5F, 10F, 15F)
 * - Search and sort controls
 * - Paginated card grid with context menus
 * - Empty states for no results
 *
 * URL state managed via usePlannerListFilters hook
 * Data fetched via usePlannerListData hook
 *
 * Pattern: IdentityPage.tsx (Suspense wrapping, filter layout)
 */

import { Suspense } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { PlusCircle } from 'lucide-react'

import { Button } from '@/components/ui/button'

import { usePlannerListData } from '@/hooks/usePlannerListData'
import { usePlannerListFilters } from '@/hooks/usePlannerListFilters'
import { useAuthQuery } from '@/hooks/useAuthQuery'
import { CARD_GRID } from '@/lib/constants'

import { PlannerListTabs } from '@/components/plannerList/PlannerListTabs'
import { PlannerListToolbar } from '@/components/plannerList/PlannerListToolbar'
import { PlannerListFilterPills } from '@/components/plannerList/PlannerListFilterPills'
import { PlannerCard } from '@/components/plannerList/PlannerCard'
import { PlannerCardContextMenu } from '@/components/plannerList/PlannerCardContextMenu'
import { PlannerEmptyState } from '@/components/plannerList/PlannerEmptyState'
import { PlannerListPagination } from '@/components/plannerList/PlannerListPagination'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'
import { LoadingState } from '@/components/common/LoadingState'

import type { MDCategory } from '@/lib/constants'
import type {
  PlannerListView,
  CommunityFilter,
  PlannerSortOption,
} from '@/types/PlannerListTypes'


// ============================================================================
// Inner Content Component
// ============================================================================

interface PlannerListContentProps {
  view: PlannerListView
  filter: CommunityFilter
  category: MDCategory | undefined
  page: number
  sort: PlannerSortOption
  search: string
  isAuthenticated: boolean
  onPageChange: (page: number) => void
}

/**
 * Inner component that uses Suspense-aware query hooks.
 * Must be wrapped in Suspense boundary.
 */
function PlannerListContent({
  view,
  filter,
  category,
  page,
  sort,
  search,
  isAuthenticated,
  onPageChange,
}: PlannerListContentProps) {
  // Fetch data based on view mode
  // My Plans view: TODO - implement IndexedDB for guests, API for authenticated
  // Community view: Always from API
  const { data } = usePlannerListData({
    filter,
    page,
    category,
    sort,
    search,
  })

  // Determine if any filters are active (for empty state messaging)
  const hasActiveFilters = !!category || !!search

  // Handle empty state
  if (data.content.length === 0) {
    return <PlannerEmptyState view={view} isFiltered={hasActiveFilters} />
  }

  return (
    <>
      <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.PLANNER}>
        {data.content.map((planner) => (
          <PlannerCardContextMenu
            key={planner.id}
            planner={planner}
            view={view}
            isAuthenticated={isAuthenticated}
          >
            <PlannerCard
              planner={planner}
              showBookmark={view === 'community' && isAuthenticated}
            />
          </PlannerCardContextMenu>
        ))}
      </ResponsiveCardGrid>

      {data.totalPages > 1 && (
        <PlannerListPagination
          currentPage={page}
          totalPages={data.totalPages}
          onPageChange={onPageChange}
        />
      )}
    </>
  )
}

// ============================================================================
// Page Content Component
// ============================================================================

/**
 * Page content with all filter controls and data fetching.
 * Wrapped in Suspense by the outer PlannerListPage component.
 */
function PlannerListPageContent() {
  const { t } = useTranslation()
  const { data: user } = useAuthQuery()
  const isAuthenticated = !!user

  const {
    view,
    filter,
    category,
    page,
    sort,
    search,
    setFilters,
  } = usePlannerListFilters()

  return (
    <div className="container mx-auto p-8">
      {/* Page Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-3xl font-bold mb-2">
            {t('pages.plannerList.title')}
          </h1>
          <p className="text-muted-foreground">
            {t('pages.plannerList.description')}
          </p>
        </div>

        {/* Create New Button */}
        <Button asChild>
          <Link to="/planner/md/new">
            <PlusCircle className="size-4" />
            {t('pages.plannerList.createNew')}
          </Link>
        </Button>
      </div>

      {/* Tabs: My Plans / Community */}
      <div className="mb-6">
        <PlannerListTabs
          value={view}
          onChange={(v) => setFilters({ view: v, page: 0 })}
        />
      </div>

      {/* Toolbar: Search + Sort + Recommended toggle */}
      <div className="mb-4">
        <PlannerListToolbar
          search={search ?? ''}
          onSearchChange={(q) => setFilters({ q, page: 0 })}
          sort={sort}
          onSortChange={(s) => setFilters({ sort: s, page: 0 })}
          showRecommendedToggle={view === 'community'}
          isRecommended={filter === 'recommended'}
          onRecommendedChange={(rec) =>
            setFilters({ filter: rec ? 'recommended' : 'all', page: 0 })
          }
        />
      </div>

      {/* Category Filter Pills */}
      <div className="mb-6">
        <PlannerListFilterPills
          selectedCategory={category}
          onCategoryChange={(c) => setFilters({ category: c, page: 0 })}
        />
      </div>

      {/* Content Grid with inner Suspense for data loading */}
      <Suspense fallback={<LoadingState message={t('common.loading')} />}>
        <PlannerListContent
          view={view}
          filter={filter}
          category={category}
          page={page}
          sort={sort}
          search={search ?? ''}
          isAuthenticated={isAuthenticated}
          onPageChange={(p) => setFilters({ page: p })}
        />
      </Suspense>
    </div>
  )
}

// ============================================================================
// Main Page Component
// ============================================================================

/**
 * PlannerListPage - Main entry point with Suspense boundary
 *
 * Pattern: IdentityPage.tsx
 * Outer component wraps inner content in Suspense for loading state
 */
export default function PlannerListPage() {
  return (
    <Suspense fallback={<LoadingState />}>
      <PlannerListPageContent />
    </Suspense>
  )
}
