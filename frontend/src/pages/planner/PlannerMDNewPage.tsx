import { Suspense } from 'react'
import { PlannerEditorStoreProvider } from './stores/usePlannerEditorStore'
import { PlannerCreateEditor } from './components/planner/PlannerCreateEditor'
import { PlannerMDNewPageSkeleton } from './components/plannerSkeletons'

export default function PlannerMDNewPage() {
  return (
    <PlannerEditorStoreProvider>
      <Suspense fallback={<PlannerMDNewPageSkeleton />}>
        <PlannerCreateEditor />
      </Suspense>
    </PlannerEditorStoreProvider>
  )
}
