import { Suspense } from 'react'
import { Link, useNavigate, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ErrorBoundary } from '@/components/common/ErrorBoundary'
import { PlannerNotFound } from '@/components/common/PlannerNotFound'
import { PlannerViewer } from '@/components/plannerViewer/PlannerViewer'
import { PlannerDetailHeader } from '@/components/plannerViewer/PlannerDetailHeader'
import { PersonalPlannerList } from '@/components/plannerList/PersonalPlannerList'
import { MDPlannerToolbar } from '@/components/plannerList/MDPlannerToolbar'
import { PlannerListFilterPills } from '@/components/plannerList/PlannerListFilterPills'
import { PlannerGridSkeleton } from '@/components/common/ListPageSkeleton'
import { useSavedPlannerQuery } from '@/hooks/useSavedPlannerQuery'
import { useAuthQuery } from '@/hooks/useAuthQuery'
import { useUserSettingsQuery } from '@/hooks/useUserSettings'
import { useMDUserFilters } from '@/hooks/useMDUserFilters'
import { SECTION_STYLES } from '@/lib/constants'

/**
 * Planner MD Detail Page - View a saved planner in guide or tracker mode
 */
export default function PlannerMDDetailPage() {
  const { id } = useParams({ from: '/planner/md/$id' })

  return (
    <ErrorBoundary>
      <div className="container mx-auto p-8">
        <Suspense
          fallback={
            <div className="space-y-6">
              <div className="flex items-center justify-between">
                <Skeleton className="h-10 w-64" />
                <Skeleton className="h-10 w-32" />
              </div>
              <div className="bg-background rounded-lg p-6 space-y-4">
                <Skeleton className="h-64 w-full" />
                <Skeleton className="h-48 w-full" />
                <Skeleton className="h-96 w-full" />
              </div>
            </div>
          }
        >
          <PlannerDetailContent plannerId={id} />
        </Suspense>
      </div>
    </ErrorBoundary>
  )
}

function PlannerDetailContent({ plannerId }: { plannerId: string }) {
  const { t } = useTranslation(['planner', 'common'])
  const navigate = useNavigate()

  // Load planner from storage via Suspense query
  const planner = useSavedPlannerQuery(plannerId)

  // Get auth state (personal planners are always owned by current user)
  const { data: user } = useAuthQuery()
  const isAuthenticated = user !== null

  // Get sync setting
  const { data: userSettings } = useUserSettingsQuery()
  const syncEnabled = userSettings?.syncEnabled

  // URL search params for list section
  const { category, page, search, setFilters } = useMDUserFilters()

  // Handle not found
  if (!planner) {
    return <PlannerNotFound listPath="/planner/md" />
  }

  // Validate planner type - viewer only supports Mirror Dungeon planners
  if (planner.config.type !== 'MIRROR_DUNGEON') {
    return (
      <div className="space-y-6 text-center py-12">
        <h1 className="text-2xl font-bold">{t('pages.detail.invalidType', 'Invalid Planner Type')}</h1>
        <p className="text-muted-foreground">
          {t('pages.detail.invalidTypeMessage', 'This viewer only supports Mirror Dungeon planners.')}
        </p>
        <p className="text-sm text-muted-foreground">
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
      {/* Header with status badge and edit action */}
      <PlannerDetailHeader
        variant="personal"
        planner={planner}
        isOwner={true}
        isAuthenticated={isAuthenticated}
        syncEnabled={syncEnabled}
        onEdit={handleEdit}
      />

      {/* Planner Viewer */}
      <PlannerViewer planner={planner} />

      {/* Separator */}
      <div className="border-t border-border my-8" />

      {/* Personal Planners List Section */}
      <div className={SECTION_STYLES.SPACING.section}>
        {/* Toolbar: Search */}
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

        {/* Planner List Grid */}
        <Suspense fallback={<PlannerGridSkeleton />}>
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
