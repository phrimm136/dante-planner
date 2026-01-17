/**
 * PlannerMDPage - Personal planners view (My Plans)
 *
 * Route: /planner/md
 *
 * Features:
 * - Shows user's personal planners (IndexedDB for guests, server for auth)
 * - Category filter pills (5F, 10F, 15F)
 * - Paginated card grid
 * - Empty states for no results
 * - Navigation buttons to switch to Gesellschaft view
 *
 * URL state managed via useMDUserFilters hook
 * Data fetched via useMDUserPlannersData hook
 *
 * Pattern: IdentityPage.tsx (Suspense wrapping, filter layout)
 */

import { Suspense, useState, useEffect } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { PlusCircle } from 'lucide-react'

import { Button } from '@/components/ui/button'

import { useMDUserPlannersData } from '@/hooks/useMDUserPlannersData'
import { useMDUserFilters } from '@/hooks/useMDUserFilters'
import { useUserSettingsQuery } from '@/hooks/useUserSettings'
import { CARD_GRID, calculatePlannerPages } from '@/lib/constants'

import { MDPlannerNavButtons } from '@/components/plannerList/MDPlannerNavButtons'
import { PersonalPlannerCard } from '@/components/plannerList/PersonalPlannerCard'
import { MDPlannerToolbar } from '@/components/plannerList/MDPlannerToolbar'
import { PlannerListFilterPills } from '@/components/plannerList/PlannerListFilterPills'
import { PlannerEmptyState } from '@/components/plannerList/PlannerEmptyState'
import { PlannerListPagination } from '@/components/plannerList/PlannerListPagination'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'
import { LoadingState } from '@/components/common/LoadingState'
import { PlannerGridSkeleton } from '@/components/common/ListPageSkeleton'
import { BatchConflictDialog } from '@/components/dialogs/BatchConflictDialog'

import type { MDCategory } from '@/lib/constants'
import type { PlannerSummary } from '@/types/PlannerTypes'

// ============================================================================
// Inner Content Component
// ============================================================================

interface PlannerMDContentProps {
  category: MDCategory | undefined
  page: number
  search: string
  onPageChange: (page: number) => void
}

/**
 * Inner component that uses Suspense-aware query hooks.
 * Must be wrapped in Suspense boundary.
 */
function PlannerMDContent({
  category,
  page,
  search,
  onPageChange,
}: PlannerMDContentProps) {
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

// ============================================================================
// Page Content Component
// ============================================================================

/**
 * Page content with all filter controls and data fetching.
 * Wrapped in Suspense by the outer PlannerMDPage component.
 */
function PlannerMDPageContent() {
  const { t } = useTranslation(['planner', 'common'])

  const {
    category,
    page,
    search,
    setFilters,
  } = useMDUserFilters()

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

      {/* Toolbar: Search only (no mode toggle for personal planners) */}
      <div className="mb-4">
        <MDPlannerToolbar
          search={search}
          onSearchChange={(q) => setFilters({ q, page: 0 })}
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
        <PlannerMDContent
          category={category}
          page={page}
          search={search}
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
 * PlannerMDPage - Personal planners page with Suspense boundary
 *
 * Pattern: IdentityPage.tsx
 * Outer component wraps inner content in Suspense for loading state
 */
export default function PlannerMDPage() {
  return (
    <Suspense fallback={<LoadingState />}>
      <PlannerMDPageContent />
    </Suspense>
  )
}
