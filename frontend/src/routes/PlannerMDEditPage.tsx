import { Suspense } from 'react'
import { Link, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ErrorBoundary } from '@/components/common/ErrorBoundary'
import { PlannerMDEditorContent } from './PlannerMDEditorContent'
import { useSavedPlannerQuery } from '@/hooks/useSavedPlannerQuery'

/**
 * Planner MD Edit Page - Edit an existing planner
 */
export default function PlannerMDEditPage() {
  const { id } = useParams({ from: '/planner/md/$id/edit' })

  return (
    <ErrorBoundary>
      <Suspense
        fallback={
          <div className="container mx-auto py-6">
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
          </div>
        }
      >
        <PlannerEditContent id={id} />
      </Suspense>
    </ErrorBoundary>
  )
}

function PlannerEditContent({ id }: { id: string }) {
  const { t } = useTranslation(['planner', 'common'])
  const planner = useSavedPlannerQuery(id)

  if (!planner) {
    return (
      <div className="container mx-auto py-16 text-center">
        <h1 className="text-2xl font-bold mb-4">
          {t('pages.detail.notFound', 'Planner Not Found')}
        </h1>
        <p className="text-muted-foreground mb-6">
          {t('pages.detail.notFoundMessage', 'The planner you are looking for does not exist.')}
        </p>
        <Button asChild variant="outline">
          <Link to="/planner/md">{t('pages.detail.backToList', 'Back to List')}</Link>
        </Button>
      </div>
    )
  }

  if (planner.config.type !== 'MIRROR_DUNGEON') {
    return (
      <div className="container mx-auto py-16 text-center">
        <h1 className="text-2xl font-bold mb-4">
          {t('pages.detail.invalidType', 'Invalid Planner Type')}
        </h1>
        <p className="text-muted-foreground mb-6">
          {t('pages.detail.invalidTypeMessage', 'This editor only supports Mirror Dungeon planners.')}
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

  return <PlannerMDEditorContent mode="edit" planner={planner} />
}
