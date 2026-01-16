/**
 * PlannerMDGesellschaftPage - Community planners view (Gesellschaft)
 *
 * Route: /planner/md/gesellschaft
 *
 * Features:
 * - Shows community planners (published/recommended from API)
 * - Mode toggle: All Published vs Best Only
 * - Category filter pills (5F, 10F, 15F)
 * - Search controls
 * - Paginated card grid with context menus
 * - Voting and bookmark functionality (authenticated only)
 * - Empty states for no results
 * - Navigation buttons to switch to My Plans view
 *
 * URL state managed via useMDGesellschaftFilters hook
 * Data fetched via useMDGesellschaftData hook
 *
 * Pattern: PlannerMDPage.tsx (Suspense wrapping, filter layout)
 */

import { Suspense, useState, useEffect } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { PlusCircle } from 'lucide-react'

import { Button } from '@/components/ui/button'

import { useMDGesellschaftData } from '@/hooks/useMDGesellschaftData'
import { useMDGesellschaftFilters } from '@/hooks/useMDGesellschaftFilters'
import { useAuthQuery } from '@/hooks/useAuthQuery'
import { CARD_GRID } from '@/lib/constants'

import { MDPlannerNavButtons } from '@/components/plannerList/MDPlannerNavButtons'
import { MDPlannerToolbar } from '@/components/plannerList/MDPlannerToolbar'
import { PlannerListFilterPills } from '@/components/plannerList/PlannerListFilterPills'
import { PlannerCard } from '@/components/plannerList/PlannerCard'
import { PlannerCardContextMenu } from '@/components/plannerList/PlannerCardContextMenu'
import { PlannerEmptyState } from '@/components/plannerList/PlannerEmptyState'
import { PlannerListPagination } from '@/components/plannerList/PlannerListPagination'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'
import { LoadingState } from '@/components/common/LoadingState'
import { PlannerGridSkeleton } from '@/components/common/ListPageSkeleton'

import type { MDCategory } from '@/lib/constants'
import type { MDGesellschaftMode } from '@/types/MDPlannerListTypes'

// ============================================================================
// Inner Content Component
// ============================================================================

interface GesellschaftContentProps {
  mode: MDGesellschaftMode
  category: MDCategory | undefined
  page: number
  search: string
  isAuthenticated: boolean
  onPageChange: (page: number) => void
}

/**
 * Inner component that uses Suspense-aware query hooks.
 * Must be wrapped in Suspense boundary.
 */
function GesellschaftContent({
  mode,
  category,
  page,
  search,
  isAuthenticated,
  onPageChange,
}: GesellschaftContentProps) {
  const { data } = useMDGesellschaftData({
    mode,
    page,
    category,
    search: search || undefined,
  })

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
          <PlannerCardContextMenu
            key={planner.id}
            planner={planner}
            view="community"
            isAuthenticated={isAuthenticated}
          >
            <PlannerCard
              planner={planner}
              showBookmark={isAuthenticated}
            />
          </PlannerCardContextMenu>
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

// ============================================================================
// Page Content Component
// ============================================================================

/**
 * Page content with all filter controls and data fetching.
 * Wrapped in Suspense by the outer page component.
 */
function GesellschaftPageContent() {
  const { t } = useTranslation(['planner', 'common'])
  const { data: user } = useAuthQuery()
  const isAuthenticated = !!user

  const {
    category,
    page,
    mode,
    search,
    setFilters,
  } = useMDGesellschaftFilters()

  return (
    <div className="container mx-auto p-8">
      {/* Create New Button */}
      <div className="flex justify-end mb-6">
        <Button asChild>
          <Link to="/planner/md/new">
            <PlusCircle className="size-4" />
            {t('pages.list.createNew')}
          </Link>
        </Button>
      </div>

      {/* Navigation: My Plans / Gesellschaft */}
      <div className="mb-6">
        <MDPlannerNavButtons />
      </div>

      {/* Toolbar: Search + Mode Toggle */}
      <div className="mb-4">
        <MDPlannerToolbar
          search={search}
          onSearchChange={(q) => setFilters({ q, page: 0 })}
          showModeToggle
          mode={mode}
          onModeChange={(m) => setFilters({ mode: m, page: 0 })}
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
      <Suspense fallback={<PlannerGridSkeleton />}>
        <GesellschaftContent
          mode={mode}
          category={category}
          page={page}
          search={search}
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
 * PlannerMDGesellschaftPage - Community planners page with Suspense boundary
 *
 * Pattern: PlannerMDPage.tsx
 * Outer component wraps inner content in Suspense for loading state
 */
export default function PlannerMDGesellschaftPage() {
  return (
    <Suspense fallback={<LoadingState />}>
      <GesellschaftPageContent />
    </Suspense>
  )
}
