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
import { passiveBoxLayout } from '../lib/passiveLayout'

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
  const layout = passiveBoxLayout()

  return (
    <div
      className={cn(isLocked && 'opacity-50', decorated && 'relative isolate @container')}
      style={importanceBoxPadding(importanceLevel)}
    >
      <ImportanceDecoration level={importanceLevel} palette={palette} variant="passive" />
      <div data-slot="passive-box" className="relative" style={layout.box}>
        <ImportanceBanner
          level={importanceLevel}
          palette={palette}
          className={SECTION_STYLES.LAYOUT.row}
          style={layout.plateRow}
        >
          <Suspense fallback={<StyledNameSkeleton attributeType="NEUTRAL" plate={layout.plate} />}>
            {nameSlot}
          </Suspense>
          {isLocked && <span className="text-xs">🔒</span>}
        </ImportanceBanner>
        <div data-slot="passive-info" className="text-sm" style={layout.info}>
          <Suspense fallback={<Skeleton className="h-4 w-full" />}>{descSlot}</Suspense>
        </div>
        <Suspense fallback={null}>{flavorSlot}</Suspense>
      </div>
    </div>
  )
}
