import { Suspense, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { StyledNameSkeleton } from '@/shared/gameText'
import { Skeleton } from '@/components/ui/skeleton'
import {
  getAffinityIconPath,
  getIdentityPassiveCountIconPath,
  getLockIconPath,
} from '@/shared/assets'
import { cn, getDisplayFontForNumeric, getDisplayFontForLanguage } from '@/lib/utils'
import { SECTION_STYLES } from '@/lib/constants'
import type { ImportancePalette } from '@/shared/gameData'
import {
  ImportanceBanner,
  ImportanceDecoration,
  hasImportance,
  importanceBoxPadding,
} from '@/shared/importance'

interface IdentityPassiveCondition {
  type: string
  values: Record<string, number>
}

interface IdentityPassiveCardProps {
  nameSlot: ReactNode
  descSlot: ReactNode
  flavorSlot: ReactNode
  condition?: IdentityPassiveCondition | undefined
  isLocked: boolean
  importanceLevel: number
  palette: ImportancePalette
}

export function IdentityPassiveCard({
  nameSlot,
  descSlot,
  flavorSlot,
  condition,
  isLocked,
  importanceLevel,
  palette,
}: IdentityPassiveCardProps) {
  const { t, i18n } = useTranslation(['database', 'common'])
  const decorated = hasImportance(importanceLevel)

  return (
    <div
      className={cn(
        'relative space-y-1',
        isLocked && 'opacity-50',
        decorated && 'isolate @container',
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
      </ImportanceBanner>
      {isLocked && (
        <img
          src={getLockIconPath()}
          alt=""
          className="absolute right-0 bottom-0 -z-10 h-20 brightness-20"
        />
      )}
      {condition && (
        <div className="flex items-center gap-3 text-md ml-1">
          {Object.entries(condition.values).map(([affinity, count]) => (
            <span key={affinity} className={SECTION_STYLES.LAYOUT.rowTight}>
              <img src={getAffinityIconPath(affinity)} alt={affinity} className="h-8" />
              <img src={getIdentityPassiveCountIconPath()} alt="x" className="w-4 h-4" />
              <span
                className="text-[32px] leading-[32px] -mt-3"
                style={{ fontFamily: getDisplayFontForNumeric() }}
              >
                {count}
              </span>
            </span>
          ))}
          <span className="text-lg" style={getDisplayFontForLanguage(i18n.language)}>
            {t(`passive.${condition.type.toLowerCase()}`)}
          </span>
        </div>
      )}
      <div className="text-sm">
        <Suspense fallback={<Skeleton className="h-4 w-full" />}>{descSlot}</Suspense>
      </div>
      <Suspense fallback={null}>{flavorSlot}</Suspense>
    </div>
  )
}
