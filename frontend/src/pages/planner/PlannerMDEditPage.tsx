import { Suspense } from 'react'
import { Link, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { ErrorBoundary } from '@/components/feedback/ErrorBoundary'
import { PlannerNotFound } from '@/components/feedback/PlannerNotFound'
import { PlannerStoreProvider } from './stores/usePlannerStore'
import { hydrateEditorState } from './lib/editorStateCodec'
import { PlannerEditEditor, editorSessionFor } from './components/planner/PlannerEditEditor'
import { PlannerViewerSkeleton } from './components/plannerSkeletons'
import { useSavedPlannerQuery } from './hooks/useSavedPlannerQuery'
import { isMDPlanner } from './types/PlannerTypes'
import type { MDSaveablePlanner } from './types/PlannerTypes'
import { PLANNER_CONFIG, SECTION_STYLES } from '@/lib/constants'

export default function PlannerMDEditPage() {
  const { id } = useParams({ from: '/planner/md/$id/edit' })

  return (
    <ErrorBoundary>
      <Suspense fallback={<PlannerViewerSkeleton />}>
        <PlannerEditContent id={id} />
      </Suspense>
    </ErrorBoundary>
  )
}

function PlannerEditContent({ id }: { id: string }) {
  const { t } = useTranslation(['planner', 'common'])
  const planner = useSavedPlannerQuery(id)

  if (!planner) {
    return <PlannerNotFound listPath="/planner/md" />
  }

  if (!isMDPlanner(planner)) {
    return (
      <div className="container mx-auto py-16 text-center">
        <h1 className="text-2xl font-bold mb-4">
          {t('pages.detail.invalidType', 'Invalid Planner Type')}
        </h1>
        <p className="text-muted-foreground mb-6">
          {t(
            'pages.detail.invalidTypeMessage',
            'This editor only supports Mirror Dungeon planners.',
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

  return <PlannerEditStore key={planner.metadata.id} planner={planner} />
}

function PlannerEditStore({ planner }: { planner: MDSaveablePlanner }) {
  const metadata = {
    title: planner.metadata.title,
    category: planner.config.category,
    isPublished: planner.metadata.published ?? false,
  }

  return (
    <PlannerStoreProvider
      mdVersion={editorSessionFor(planner, PLANNER_CONFIG.mdCurrentVersion).contentVersion}
      initialState={(maxLevel) => hydrateEditorState(planner.content, metadata, maxLevel)}
    >
      <PlannerEditEditor planner={planner} />
    </PlannerStoreProvider>
  )
}
