import { Skeleton } from '@/components/ui/skeleton'
import { SECTION_STYLES } from '@/lib/constants'

/**
 * Route-shaped placeholder shown while a lazy route chunk and its loader resolve.
 */
export function RoutePendingFallback() {
  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <Skeleton className="h-8 w-64" />
      <div className="mt-6 space-y-3">
        <Skeleton className="h-5 w-full max-w-3xl" />
        <Skeleton className="h-5 w-full max-w-2xl" />
        <Skeleton className="h-5 w-full max-w-xl" />
      </div>
    </div>
  )
}
