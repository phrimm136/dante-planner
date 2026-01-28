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

import { Suspense } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { PlusCircle } from 'lucide-react'
import { ErrorBoundary as ReactErrorBoundary } from 'react-error-boundary'

import { Button } from '@/components/ui/button'

import { useMDGesellschaftFilters } from '@/hooks/useMDGesellschaftFilters'
import { useAuthQuery } from '@/hooks/useAuthQuery'

import { MDPlannerNavButtons } from '@/components/plannerList/MDPlannerNavButtons'
import { MDPlannerToolbar } from '@/components/plannerList/MDPlannerToolbar'
import { PlannerListFilterPills } from '@/components/plannerList/PlannerListFilterPills'
import { PublishedPlannerList } from '@/components/plannerList/PublishedPlannerList'
import { LoadingState } from '@/components/common/LoadingState'
import { PlannerGridSkeleton } from '@/components/common/ListPageSkeleton'
import { CommunityPlansErrorFallback } from '@/components/home/CommunityPlansErrorFallback'

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

      {/* Content Grid with ErrorBoundary + Suspense for data loading */}
      <ReactErrorBoundary FallbackComponent={CommunityPlansErrorFallback}>
        <Suspense fallback={<PlannerGridSkeleton />}>
          <PublishedPlannerList
            mode={mode}
            category={category}
            page={page}
            search={search}
            isAuthenticated={isAuthenticated}
            onPageChange={(p) => setFilters({ page: p })}
          />
        </Suspense>
      </ReactErrorBoundary>
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
