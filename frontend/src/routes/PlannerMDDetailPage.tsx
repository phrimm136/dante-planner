import { Suspense } from 'react'
import { Link, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ErrorBoundary } from '@/components/common/ErrorBoundary'
import { PlannerViewer } from '@/components/plannerViewer/PlannerViewer'
import { useSavedPlannerQuery } from '@/hooks/useSavedPlannerQuery'

/**
 * Planner MD Detail Page - View a saved planner in guide or tracker mode
 */
export default function PlannerMDDetailPage() {
  const { id } = useParams({ from: '/planner/md/$id' })
  const { t } = useTranslation(['planner', 'common'])

  return (
    <ErrorBoundary>
      <div className="container mx-auto py-6">
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

  // Load planner from storage via Suspense query
  const planner = useSavedPlannerQuery(plannerId)

  // Handle not found
  if (!planner) {
    return (
      <div className="space-y-6 text-center py-12">
        <h1 className="text-2xl font-bold">{t('pages.detail.notFound', 'Planner Not Found')}</h1>
        <p className="text-muted-foreground">{t('pages.detail.notFoundMessage', 'The planner you are looking for does not exist.')}</p>
        <Button asChild variant="outline">
          <Link to="/planner/md">{t('pages.detail.backToList', 'Back to List')}</Link>
        </Button>
      </div>
    )
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

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">{planner.metadata.title || t('pages.plannerMD.untitled')}</h1>
        <Button asChild variant="outline">
          <Link to="/planner/md">{t('pages.detail.backToList', 'Back to List')}</Link>
        </Button>
      </div>

      {/* Planner Viewer */}
      <PlannerViewer planner={planner} />
    </div>
  )
}
