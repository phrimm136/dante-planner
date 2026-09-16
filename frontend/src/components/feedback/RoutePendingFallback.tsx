import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { SECTION_STYLES } from '@/lib/constants'

/**
 * Route-shaped placeholder shown while a lazy route chunk and its loader resolve.
 */
export function RoutePendingFallback() {
  return (
    <div className={SECTION_STYLES.LAYOUT.page}>
      <TextSkeleton size="2xl" width="lg" />
      <div className="mt-6 space-y-3">
        <TextSkeleton width="full" className="max-w-3xl" />
        <TextSkeleton width="full" className="max-w-2xl" />
        <TextSkeleton width="full" className="max-w-xl" />
      </div>
    </div>
  )
}
