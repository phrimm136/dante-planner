import { Skeleton } from '@/components/ui/skeleton'
import { Suspense } from 'react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { PlusCircle } from 'lucide-react'

import { Button } from '@/components/ui/button'

import { useMDUserFilters } from './hooks/useMDUserFilters'
import { usePlannerSearchFilters } from './hooks/usePlannerSearchFilters'

import { MDPlannerNavButtons } from './components/plannerList/MDPlannerNavButtons'
import { PersonalPlannerList } from './components/plannerList/PersonalPlannerList'
import { MDPlannerToolbar } from './components/plannerList/MDPlannerToolbar'
import { PlannerListFilterPills } from './components/plannerList/PlannerListFilterPills'
import { PlannerFilterPane } from './components/plannerList/PlannerFilterPane'
import { LoadingState } from '@/components/feedback/LoadingState'
import { PlannerGridSkeleton } from '@/components/feedback/ListPageSkeleton'
import { PLANNER_GEOMETRY } from '@/shared/cardLayout'
import { SECTION_STYLES } from '@/lib/constants'

function PlannerMDPageContent() {
  const { t } = useTranslation(['planner', 'common'])

  const { category, page, search, setFilters } = useMDUserFilters()

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
        <MDPlannerToolbar search={search} onSearchChange={(q) => setFilters({ q, page: 0 })} />
      </div>

      <div className="mb-4">
        <PlannerListFilterPills
          selectedCategory={category}
          onCategoryChange={(c) => setFilters({ category: c, page: 0 })}
        />
      </div>

      <div className="mb-4">
        <Suspense fallback={<Skeleton className="h-10 w-full rounded-md" />}>
          <PlannerFilterPane filters={searchFilters} onFiltersChange={setSearchFilters} />
        </Suspense>
      </div>

      <Suspense fallback={<PlannerGridSkeleton geometry={PLANNER_GEOMETRY} />}>
        <PersonalPlannerList
          category={category}
          page={page}
          search={search}
          contentFilters={searchFilters}
          onPageChange={(p) => setFilters({ page: p })}
        />
      </Suspense>
    </div>
  )
}

export default function PlannerMDPage() {
  return (
    <Suspense fallback={<LoadingState />}>
      <PlannerMDPageContent />
    </Suspense>
  )
}
