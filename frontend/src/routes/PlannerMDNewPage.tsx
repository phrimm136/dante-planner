import { Suspense } from 'react'
import { Skeleton } from '@/components/ui/skeleton'
import { PlannerMDEditorContent } from './PlannerMDEditorContent'

/**
 * Page-level skeleton for initial data load
 */
function PlannerMDNewPageSkeleton() {
  return (
    <div className="container mx-auto p-8">
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
            <div className="flex flex-wrap gap-2">
              {Array.from({ length: 12 }).map((_, i) => (
                <Skeleton
                  key={i}
                  className="w-16 h-20 rounded-md"
                  style={{ animationDelay: `${i * 40}ms` }}
                />
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
 * Delegates all editor logic to PlannerMDEditorContent with mode="new"
 */
export default function PlannerMDNewPage() {
  return (
    <Suspense fallback={<PlannerMDNewPageSkeleton />}>
      <PlannerMDEditorContent mode="new" />
    </Suspense>
  )
}
