import { DetailPageSkeleton } from '@/components/feedback/DetailPageSkeleton'
import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { LabeledPanel } from '@/components/layout/LabeledPanel'
import { CardSlot } from '@/shared/cardLayout'
import { Skeleton } from '@/components/ui/skeleton'
import { KEYWORD_GEOMETRY } from '../lib/cardLayout'

/**
 * Keyword detail: Icon + name + backlinks panel (left)
 * Description panel (right)
 */
export function KeywordDetailSkeleton() {
  return (
    <DetailPageSkeleton
      left={
        <div className="space-y-4">
          <div className="flex gap-4 items-center">
            <CardSlot size={KEYWORD_GEOMETRY.size} mobileScale={1}>
              <Skeleton className="size-full rounded-md" />
            </CardSlot>
            <TextSkeleton size="2xl" width="md" />
          </div>
          <LabeledPanel>
            <div className="space-y-1.5">
              <TextSkeleton size="xs" width="sm" />
              <TextSkeleton width="lg" />
            </div>
            <div className="space-y-1.5">
              <TextSkeleton size="xs" width="sm" />
              <TextSkeleton width="lg" />
            </div>
            <div className="space-y-1.5">
              <TextSkeleton size="xs" width="sm" />
              <TextSkeleton width="lg" />
            </div>
          </LabeledPanel>
        </div>
      }
      right={
        <div className="space-y-4">
          <LabeledPanel>
            <TextSkeleton size="lg" width="sm" />
            <TextSkeleton lines={4} width="full" />
          </LabeledPanel>
        </div>
      }
    />
  )
}
