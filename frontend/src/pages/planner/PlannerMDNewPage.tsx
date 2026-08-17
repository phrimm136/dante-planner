import { Suspense } from 'react'
import { Skeleton } from '@/components/ui/skeleton'
import { PlannerEditorStoreProvider } from './stores/usePlannerEditorStore'
import { PlannerCreateEditor } from './components/planner/PlannerCreateEditor'
import { staggerDelay } from '@/lib/stagger'
import { SECTION_STYLES } from '@/lib/constants'

/**
 * Page-level skeleton for initial data load
 */
function PlannerMDNewPageSkeleton() {
  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <div className="flex items-center justify-between mb-4">
        <div className="space-y-2">
          <Skeleton className="h-9 w-64" />
        </div>
        <Skeleton className="h-10 w-24" />
      </div>
      <Skeleton className="h-5 w-96 mb-6" />

      <div className="bg-background rounded-lg p-6 space-y-6">
        <div className="flex gap-4">
          <Skeleton className="h-10 w-32" />
          <Skeleton className="h-10 flex-1" />
        </div>

        <Skeleton className="h-10 w-full" />

        <div className="space-y-2">
          <Skeleton className="h-6 w-32" />
          <div className="border-2 border-border rounded-lg p-4">
            <div className={SECTION_STYLES.LAYOUT.wrap}>
              {Array.from({ length: 12 }).map((_, i) => (
                <Skeleton key={i} className="w-16 h-20 rounded-md" style={staggerDelay(i)} />
              ))}
            </div>
          </div>
        </div>

        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="space-y-2">
            <Skeleton className="h-6 w-48" />
            <Skeleton className="h-32 w-full rounded-lg" />
          </div>
        ))}
      </div>
    </div>
  )
}

/**
 * Main export with Suspense boundary
 * Delegates all editor logic to PlannerCreateEditor
 */
export default function PlannerMDNewPage() {
  return (
    <PlannerEditorStoreProvider>
      <Suspense fallback={<PlannerMDNewPageSkeleton />}>
        <PlannerCreateEditor />
      </Suspense>
    </PlannerEditorStoreProvider>
  )
}
