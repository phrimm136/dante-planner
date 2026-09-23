import { Suspense, type ReactNode } from 'react'

import { StyledNameSkeleton } from '@/shared/gameText'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { SECTION_STYLES } from '@/lib/constants'
import type { ImportancePalette } from '@/shared/gameData'
import { ImportanceDecoration, importanceNameStyle } from '@/shared/importance'

interface EGOPassiveCardProps {
  nameSlot: ReactNode
  descSlot: ReactNode
  flavorSlot: ReactNode
  isLocked: boolean
  importanceLevel: number
  palette: ImportancePalette
}

export function EGOPassiveCard({
  nameSlot,
  descSlot,
  flavorSlot,
  isLocked,
  importanceLevel,
  palette,
}: EGOPassiveCardProps) {
  const nameStyle = importanceNameStyle(importanceLevel, palette)

  return (
    <div className={cn('space-y-1', isLocked && 'opacity-50', nameStyle && 'relative isolate')}>
      <ImportanceDecoration level={importanceLevel} palette={palette} variant="passive" />
      <div
        className={
          nameStyle ? cn(SECTION_STYLES.LAYOUT.row, 'importance-name') : SECTION_STYLES.LAYOUT.row
        }
        style={nameStyle}
      >
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
