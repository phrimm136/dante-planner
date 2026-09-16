import { DetailPageSkeleton } from '@/components/feedback/DetailPageSkeleton'
import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { Skeleton } from '@/components/ui/skeleton'
import { CARD_MOBILE_SCALE_NONE } from '@/lib/constants'
import { CardSlot, EGO_GIFT_GEOMETRY } from '@/shared/cardLayout'

/**
 * Ab Event detail: event image, description and related gifts/packs (left)
 * Choice branches with effects (right)
 */
export function AbEventDetailSkeleton() {
  return (
    <DetailPageSkeleton
      left={
        <div className="space-y-4">
          <Skeleton className="w-full aspect-[3/2] rounded-lg" />
          <div className="border rounded p-4">
            <TextSkeleton lines={3} width="full" />
          </div>
          <div className="border rounded p-4 space-y-4">
            <div className="space-y-2">
              <TextSkeleton size="xs" width="sm" />
              <div className="flex flex-wrap gap-2">
                {[0, 1].map((i) => (
                  <div key={i} className="flex flex-col items-center gap-1">
                    <CardSlot size={EGO_GIFT_GEOMETRY.size} mobileScale={CARD_MOBILE_SCALE_NONE}>
                      <Skeleton className="size-full rounded-lg" />
                    </CardSlot>
                    <TextSkeleton size="xs" lines={2} width="full" />
                  </div>
                ))}
              </div>
            </div>
            <div className="space-y-2">
              <TextSkeleton size="xs" width="sm" />
              <TextSkeleton width="full" />
            </div>
          </div>
        </div>
      }
      right={
        <div className="space-y-4">
          {[0, 1, 2].map((i) => (
            <div key={i} className="border border-border rounded-lg overflow-hidden">
              <div className="bg-muted/50 px-4 py-3 border-b border-border">
                <TextSkeleton size="sm" width="md" />
              </div>
              <div className="p-4 space-y-3">
                <TextSkeleton lines={2} width="full" />
              </div>
            </div>
          ))}
        </div>
      }
    />
  )
}
