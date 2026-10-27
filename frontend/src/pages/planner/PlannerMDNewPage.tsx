import { Suspense } from 'react'
import { PLANNER_CONFIG } from '@/lib/constants'
import { PlannerStoreProvider } from './stores/usePlannerStore'
import { PlannerCreateEditor } from './components/planner/PlannerCreateEditor'
import { PlannerMDNewPageSkeleton } from './components/plannerSkeletons'

function NewPlannerStore() {
  return (
    <PlannerStoreProvider mdVersion={PLANNER_CONFIG.mdCurrentVersion}>
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
