import { Suspense, type ReactNode } from 'react'

import { StyledNameSkeleton } from '@/shared/gameText'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { SECTION_STYLES } from '@/lib/constants'
import type { ImportancePalette } from '@/shared/gameData'
import {
  ImportanceBanner,
  ImportanceDecoration,
  hasImportance,
  importanceBoxPadding,
} from '@/shared/importance'

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
  const decorated = hasImportance(importanceLevel)

  return (
    <div
      className={cn(
        'space-y-1',
        isLocked && 'opacity-50',
        decorated && 'relative isolate @container',
      )}
      style={importanceBoxPadding(importanceLevel)}
    >
      <ImportanceDecoration level={importanceLevel} palette={palette} variant="passive" />
      <ImportanceBanner
        level={importanceLevel}
        palette={palette}
        className={SECTION_STYLES.LAYOUT.row}
      >
        <Suspense fallback={<StyledNameSkeleton attributeType="NEUTRAL" />}>{nameSlot}</Suspense>
        {isLocked && <span className="text-xs">🔒</span>}
      </ImportanceBanner>
      <div className="text-sm">
        <Suspense fallback={<Skeleton className="h-4 w-full" />}>{descSlot}</Suspense>
      </div>
      <Suspense fallback={null}>{flavorSlot}</Suspense>
    </div>
  )
}
