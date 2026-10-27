import { Suspense } from 'react'
import { Link, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { ErrorBoundary } from '@/components/feedback/ErrorBoundary'
import { PlannerNotFound } from '@/components/feedback/PlannerNotFound'
import { PlannerStoreProvider, createDefaultSectionNotes } from './stores/usePlannerStore'
import { deserializeSets } from './schemas/PlannerSchemas'
import { PlannerEditEditor, editorSessionFor } from './components/planner/PlannerEditEditor'
import { PlannerViewerSkeleton } from './components/plannerSkeletons'
import { useSavedPlannerQuery } from './hooks/useSavedPlannerQuery'
import { useIdentityMaxLevel } from './hooks/useMirrorDungeonSeason'
import { isMDPlanner } from './types/PlannerTypes'
import type { FloorThemeSelection } from '@/pages/themePack'
import type { PlannerStoreState } from './stores/usePlannerStore'
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

  const { content } = planner
  const deserialized = deserializeSets(content, planner.config.category)

  const initialState: Partial<PlannerStoreState> = {
    title: planner.metadata.title,
    category: planner.config.category,
    isPublished: planner.metadata.published ?? false,
    equipment: content.equipment,
    floorSelections: deserialized.floorSelections as FloorThemeSelection[],
    comprehensiveGiftIds: deserialized.comprehensiveGiftIds,
    deploymentOrder: content.deploymentOrder,
    selectedKeywords: deserialized.selectedKeywords,
    selectedBuffIds: deserialized.selectedBuffIds,
    selectedGiftIds: deserialized.selectedGiftIds,
    observationGiftIds: deserialized.observationGiftIds,
    selectedGiftKeyword: content.selectedGiftKeyword,
    skillEAState: content.skillEAState,
    sectionNotes: {
      ...createDefaultSectionNotes(),
      ...(content.sectionNotes
        ? Object.fromEntries(
            Object.entries(content.sectionNotes).map(([key, note]) => [
              key,
              { content: note?.content ?? '' },
            ]),
          )
        : {}),
    },
  }

  return (
    <PlannerEditStore key={planner.metadata.id} planner={planner} initialState={initialState} />
  )
}

function PlannerEditStore({
  planner,
  initialState,
}: {
  planner: MDSaveablePlanner
  initialState: Partial<PlannerStoreState>
}) {
  const identityMaxLevel = useIdentityMaxLevel(
    editorSessionFor(planner, PLANNER_CONFIG.mdCurrentVersion).contentVersion,
  )

  return (
    <PlannerStoreProvider identityMaxLevel={identityMaxLevel} initialState={initialState}>
      <PlannerEditEditor planner={planner} />
    </PlannerStoreProvider>
  )
}
