import { DetailPageSkeleton } from '@/components/feedback/DetailPageSkeleton'
import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { LabeledPanel } from '@/components/layout/LabeledPanel'
import { Skeleton } from '@/components/ui/skeleton'
import { CardSlot, EGO_GIFT_GEOMETRY } from '@/shared/cardLayout'
import { CARD_MOBILE_SCALE_NONE } from '@/lib/constants'

/**
 * EGO Gift detail: Card + name + metadata (left)
 * Enhancement descriptions panel (right)
 */
export function EGOGiftDetailSkeleton() {
  return (
    <DetailPageSkeleton
      left={
        <div className="space-y-4">
          {/* Header: card + name */}
          <div className="flex gap-4 items-center">
            <CardSlot
              size={EGO_GIFT_GEOMETRY.size}
              mobileScale={CARD_MOBILE_SCALE_NONE}
              className="shrink-0"
            >
              <Skeleton className="size-full rounded-lg" />
            </CardSlot>
            <TextSkeleton size="2xl" width="md" />
          </div>

          {/* Metadata panel: price, max enhancement, theme pack */}
          <LabeledPanel>
            <div className="space-y-1">
              <TextSkeleton size="xs" width="sm" />
              <TextSkeleton size="sm" width="md" />
            </div>
            <div className="space-y-1">
              <TextSkeleton size="xs" width="sm" />
              <TextSkeleton size="sm" width="md" />
            </div>
            <div className="space-y-1">
              <TextSkeleton size="xs" width="sm" />
              <TextSkeleton size="sm" width="md" />
            </div>
          </LabeledPanel>

          {/* Battle keywords */}
          <LabeledPanel>
            <TextSkeleton size="xs" width="full" />
          </LabeledPanel>
        </div>
      }
      right={
        <div className="space-y-4">
          {/* Enhancement rows: base, +, ++ */}
          <div className="border rounded-lg p-4 space-y-4">
            <div className="pb-4 border-b">
              <div className="flex items-center gap-3 mb-3">
                <div className="h-12 w-12 rounded bg-muted" />
                <div className="flex items-center gap-2">
                  <div className="w-6 h-6 rounded bg-muted" />
                  <TextSkeleton size="sm" width="xs" />
                </div>
              </div>
              <TextSkeleton lines={3} width="full" />
            </div>
            <div className="pb-4 border-b">
              <div className="flex items-center gap-3 mb-3">
                <div className="h-12 w-12 rounded bg-muted" />
                <div className="flex items-center gap-2">
                  <div className="w-6 h-6 rounded bg-muted" />
                  <TextSkeleton size="sm" width="xs" />
                </div>
              </div>
              <TextSkeleton lines={3} width="full" />
            </div>
            <div>
              <div className="flex items-center gap-3 mb-3">
                <div className="h-12 w-12 rounded bg-muted" />
                <div className="flex items-center gap-2">
                  <div className="w-6 h-6 rounded bg-muted" />
                  <TextSkeleton size="sm" width="xs" />
                </div>
              </div>
              <TextSkeleton lines={3} width="full" />
            </div>
          </div>
        </div>
      }
    />
  )
}
