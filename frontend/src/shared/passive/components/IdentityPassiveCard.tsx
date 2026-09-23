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
import { ImportanceDecoration, importanceNameStyle } from '@/shared/importance'

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
  const nameStyle = importanceNameStyle(importanceLevel, palette)

  return (
    <div className={cn('relative space-y-1', isLocked && 'opacity-50', nameStyle && 'isolate')}>
      <ImportanceDecoration level={importanceLevel} palette={palette} variant="passive" />
      <div
        className={
          nameStyle ? cn(SECTION_STYLES.LAYOUT.row, 'importance-name') : SECTION_STYLES.LAYOUT.row
        }
        style={nameStyle}
      >
        <Suspense fallback={<StyledNameSkeleton attributeType="NEUTRAL" />}>{nameSlot}</Suspense>
      </div>
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
