import { Suspense } from 'react'
import { PLANNER_CONFIG } from '@/lib/constants'
import { useIdentityMaxLevel } from './hooks/useMirrorDungeonSeason'
import { PlannerEditorStoreProvider } from './stores/usePlannerEditorStore'
import { PlannerCreateEditor } from './components/planner/PlannerCreateEditor'
import { PlannerMDNewPageSkeleton } from './components/plannerSkeletons'

function NewPlannerStore() {
  const identityMaxLevel = useIdentityMaxLevel(PLANNER_CONFIG.mdCurrentVersion)

  return (
    <PlannerEditorStoreProvider identityMaxLevel={identityMaxLevel}>
      <PlannerCreateEditor />
    </PlannerEditorStoreProvider>
  )
}

export default function PlannerMDNewPage() {
  return (
    <Suspense fallback={<PlannerMDNewPageSkeleton />}>
      <NewPlannerStore />
    </Suspense>
  )
}
