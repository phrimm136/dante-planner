import { Skeleton } from '@/components/ui/skeleton'
import { Suspense } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { PlusCircle } from 'lucide-react'
import { ErrorBoundary as ReactErrorBoundary } from 'react-error-boundary'

import { Button } from '@/components/ui/button'

import { useMDGesellschaftFilters } from './hooks/useMDGesellschaftFilters'
import { usePlannerSearchFilters } from './hooks/usePlannerSearchFilters'
import { useAuthQuery } from '@/shared/auth'

import { MDPlannerNavButtons } from './components/plannerList/MDPlannerNavButtons'
import { MDPlannerToolbar } from './components/plannerList/MDPlannerToolbar'
import { PlannerListFilterPills } from './components/plannerList/PlannerListFilterPills'
import { PlannerFilterPane } from './components/plannerList/PlannerFilterPane'
import { PublishedPlannerList } from './components/plannerList/PublishedPlannerList'
import { LoadingState } from '@/components/feedback/LoadingState'
import { PlannerGridSkeleton } from '@/components/feedback/ListPageSkeleton'
import { PLANNER_GEOMETRY } from '@/shared/cardLayout'
import { CommunityPlansErrorFallback } from '@/components/feedback/CommunityPlansErrorFallback'
import { SECTION_STYLES } from '@/lib/constants'

function GesellschaftPageContent() {
  const { t } = useTranslation(['planner', 'common'])
  const { data: user } = useAuthQuery()
  const isAuthenticated = !!user

  const { filters, setFilters } = useMDGesellschaftFilters()

  const { filters: searchFilters, setFilters: setSearchFilters } = usePlannerSearchFilters()

  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <div className="flex justify-end mb-6">
        <Button asChild>
          <Link to="/planner/md/new">
            <PlusCircle className="size-4" />
            {t('pages.list.createNew')}
          </Link>
        </Button>
      </div>

      <div className="mb-6">
        <MDPlannerNavButtons />
      </div>

      <div className="mb-4">
        <MDPlannerToolbar
          search={filters.search}
          onSearchChange={(q) => setFilters({ q, page: 0 })}
          showModeToggle
          mode={filters.mode}
          onModeChange={(m) => setFilters({ mode: m, page: 0 })}
        />
      </div>

      <div className="mb-4">
        <PlannerListFilterPills
          selectedCategory={filters.category}
          onCategoryChange={(c) => setFilters({ category: c, page: 0 })}
        />
      </div>

      <div className="mb-4">
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <PlannerFilterPane filters={searchFilters} onFiltersChange={setSearchFilters} />
        </Suspense>
      </div>

      <ReactErrorBoundary FallbackComponent={CommunityPlansErrorFallback}>
        <Suspense fallback={<PlannerGridSkeleton geometry={PLANNER_GEOMETRY} />}>
          <PublishedPlannerList
            filters={filters}
            isAuthenticated={isAuthenticated}
            onPageChange={(p) => setFilters({ page: p })}
          />
        </Suspense>
      </ReactErrorBoundary>
    </div>
  )
}

export default function PlannerMDGesellschaftPage() {
  return (
    <Suspense fallback={<LoadingState />}>
      <GesellschaftPageContent />
    </Suspense>
  )
}
