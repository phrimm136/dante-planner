import { Skeleton } from '@/components/ui/skeleton'
import { ListPageSkeleton } from '@/components/feedback/ListPageSkeleton'
import { EGO_GEOMETRY, type CardSizePx } from '@/shared/cardLayout'
import { getEGOMaskPath } from '@/shared/assets'
import { egoPortraitWindowStyle } from '../lib/cardLayout'

interface EGOListSkeletonProps {
  filterCount?: number
}

function egoCard({ widthPx, heightPx }: CardSizePx) {
  return (
    <div className="relative" style={{ width: widthPx, height: heightPx }}>
      <Skeleton style={egoPortraitWindowStyle(getEGOMaskPath())} />
    </div>
  )
}

export function EGOListSkeleton({ filterCount = 5 }: EGOListSkeletonProps) {
  return <ListPageSkeleton geometry={EGO_GEOMETRY} filterCount={filterCount} card={egoCard} />
}
