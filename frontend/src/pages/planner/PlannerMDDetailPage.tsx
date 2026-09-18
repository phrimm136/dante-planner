import { Suspense } from 'react'
import { Link, useNavigate, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { ErrorBoundary } from '@/components/feedback/ErrorBoundary'
import { PlannerNotFound } from '@/components/feedback/PlannerNotFound'
import { PlannerViewer } from './components/plannerViewer/PlannerViewer'
import { PersonalPlannerHeader } from './components/plannerViewer/PersonalPlannerHeader'
import { PersonalPlannerList } from './components/plannerList/PersonalPlannerList'
import { MDPlannerToolbar } from './components/plannerList/MDPlannerToolbar'
import { PlannerListFilterPills } from './components/plannerList/PlannerListFilterPills'
import { PlannerGridSkeleton } from '@/components/feedback/ListPageSkeleton'
import { PlannerViewerSkeleton } from './components/plannerSkeletons'
import { PLANNER_GEOMETRY } from '@/shared/cardLayout'
import { useSavedPlannerQuery } from './hooks/useSavedPlannerQuery'
import { isMDPlanner } from './types/PlannerTypes'
import { useAuthQuery } from '@/shared/auth'
import { useUserSettingsQuery } from '@/shared/userSettings'
import { useMDUserFilters } from './hooks/useMDUserFilters'
import { SECTION_STYLES } from '@/lib/constants'

export default function PlannerMDDetailPage() {
  const { id } = useParams({ from: '/planner/md/$id' })

  return (
    <ErrorBoundary>
      <div className={SECTION_STYLES.LAYOUT.page}>
        <Suspense fallback={<PlannerViewerSkeleton />}>
          <PlannerDetailContent plannerId={id} />
        </Suspense>
      </div>
    </ErrorBoundary>
  )
}

function PlannerDetailContent({ plannerId }: { plannerId: string }) {
  const { t } = useTranslation(['planner', 'common'])
  const navigate = useNavigate()

  const planner = useSavedPlannerQuery(plannerId)

  const { data: user } = useAuthQuery()
  const isAuthenticated = user !== null

  const { data: userSettings } = useUserSettingsQuery()
  const syncEnabled = userSettings?.syncEnabled

  const { category, page, search, setFilters } = useMDUserFilters()

  if (!planner) {
    return <PlannerNotFound listPath="/planner/md" />
  }

  if (!isMDPlanner(planner)) {
    return (
      <div className="space-y-6 text-center py-12">
        <h1 className={SECTION_STYLES.TEXT.pageTitle}>
          {t('pages.detail.invalidType', 'Invalid Planner Type')}
        </h1>
        <p className={SECTION_STYLES.TEXT.muted}>
          {t(
            'pages.detail.invalidTypeMessage',
            'This viewer only supports Mirror Dungeon planners.',
          )}
        </p>
        <p className={SECTION_STYLES.TEXT.caption}>
          {t('pages.detail.currentType', 'Current type')}: {planner.config.type}
        </p>
        <Button asChild variant="outline">
          <Link to="/planner/md">{t('pages.detail.backToList', 'Back to List')}</Link>
        </Button>
      </div>
    )
  }

  const handleEdit = () => {
    void navigate({
      to: '/planner/md/$id/edit',
      params: { id: plannerId },
    })
  }

  return (
    <div className="space-y-4">
      <PersonalPlannerHeader
        planner={planner}
        isAuthenticated={isAuthenticated}
        syncEnabled={syncEnabled}
        onEdit={handleEdit}
      />

      <PlannerViewer planner={planner} />

      <div className="border-t border-border my-8" />

      <div className={SECTION_STYLES.SPACING.section}>
        <div className="mb-4">
          <MDPlannerToolbar search={search} onSearchChange={(q) => setFilters({ q, page: 0 })} />
        </div>

        <div className="mb-6">
          <PlannerListFilterPills
            selectedCategory={category}
            onCategoryChange={(c) => setFilters({ category: c, page: 0 })}
          />
        </div>

        <Suspense fallback={<PlannerGridSkeleton geometry={PLANNER_GEOMETRY} />}>
          <PersonalPlannerList
            category={category}
            page={page}
            search={search}
            onPageChange={(p) => setFilters({ page: p })}
          />
        </Suspense>
      </div>
    </div>
  )
}
