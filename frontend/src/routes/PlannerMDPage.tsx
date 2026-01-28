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

import { Suspense } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { PlusCircle } from 'lucide-react'

import { Button } from '@/components/ui/button'

import { useMDUserFilters } from '@/hooks/useMDUserFilters'

import { MDPlannerNavButtons } from '@/components/plannerList/MDPlannerNavButtons'
import { PersonalPlannerList } from '@/components/plannerList/PersonalPlannerList'
import { MDPlannerToolbar } from '@/components/plannerList/MDPlannerToolbar'
import { PlannerListFilterPills } from '@/components/plannerList/PlannerListFilterPills'
import { LoadingState } from '@/components/common/LoadingState'
import { PlannerGridSkeleton } from '@/components/common/ListPageSkeleton'

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
        <PersonalPlannerList
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
