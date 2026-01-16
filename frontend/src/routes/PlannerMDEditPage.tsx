import { Suspense } from 'react'
import { Link, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ErrorBoundary } from '@/components/common/ErrorBoundary'
import { PlannerEditorStoreProvider } from '@/stores/usePlannerEditorStore'
import { deserializeSets } from '@/schemas/PlannerSchemas'
import { PlannerMDEditorContent } from './PlannerMDEditorContent'
import { useSavedPlannerQuery } from '@/hooks/useSavedPlannerQuery'
import type { MDCategory } from '@/lib/constants'
import type { MDPlannerContent } from '@/types/PlannerTypes'
import type { FloorThemeSelection } from '@/types/ThemePackTypes'
import type { PlannerEditorState } from '@/stores/usePlannerEditorStore'

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

  // Build initial state from planner data for the store
  const content = planner.content as MDPlannerContent
  const deserialized = deserializeSets(content)

  const initialState: Partial<PlannerEditorState> = {
    title: planner.metadata.title,
    category: planner.config.category as MDCategory,
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
    sectionNotes: Object.fromEntries(
      Object.entries(content.sectionNotes).map(([key, note]) => [
        key,
        { content: note.content },
      ])
    ),
  }

  // key forces remount when planner ID changes (e.g., after "Keep Both" navigation)
  // This ensures plannerId state resets and auto-save writes to the correct planner
  return (
    <PlannerEditorStoreProvider key={planner.metadata.id} initialState={initialState}>
      <PlannerMDEditorContent mode="edit" planner={planner} />
    </PlannerEditorStoreProvider>
  )
}
