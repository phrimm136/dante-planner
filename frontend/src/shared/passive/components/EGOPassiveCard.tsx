import { Suspense, type ReactNode } from 'react'

import { StyledNameSkeleton } from '@/shared/gameText'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { SECTION_STYLES } from '@/lib/constants'

interface EGOPassiveCardProps {
  nameSlot: ReactNode
  descSlot: ReactNode
  flavorSlot: ReactNode
  isLocked: boolean
}

export function EGOPassiveCard({ nameSlot, descSlot, flavorSlot, isLocked }: EGOPassiveCardProps) {
  return (
    <div className={cn('space-y-1', isLocked && 'opacity-50')}>
      <div className={SECTION_STYLES.LAYOUT.row}>
        <Suspense fallback={<StyledNameSkeleton attributeType="NEUTRAL" />}>{nameSlot}</Suspense>
        {isLocked && <span className="text-xs">🔒</span>}
      </div>
      <div className="text-sm">
        <Suspense fallback={<Skeleton className="h-4 w-full" />}>{descSlot}</Suspense>
      </div>
      <Suspense fallback={null}>{flavorSlot}</Suspense>
    </div>
  )
}
