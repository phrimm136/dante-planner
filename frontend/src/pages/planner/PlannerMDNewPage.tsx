import { Suspense } from 'react'
import { PLANNER_CONFIG } from '@/lib/constants'
import { useIdentityMaxLevel } from './hooks/useMirrorDungeonSeason'
import { PlannerStoreProvider } from './stores/usePlannerStore'
import { PlannerCreateEditor } from './components/planner/PlannerCreateEditor'
import { PlannerMDNewPageSkeleton } from './components/plannerSkeletons'

function NewPlannerStore() {
  const identityMaxLevel = useIdentityMaxLevel(PLANNER_CONFIG.mdCurrentVersion)

  return (
    <PlannerStoreProvider identityMaxLevel={identityMaxLevel}>
      <PlannerCreateEditor />
    </PlannerStoreProvider>
  )
}

export default function PlannerMDNewPage() {
  return (
    <Suspense fallback={<PlannerMDNewPageSkeleton />}>
      <NewPlannerStore />
    </Suspense>
  )
}
