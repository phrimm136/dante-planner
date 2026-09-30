import { Suspense, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { NamePlateSkeleton, type NamePlateImportance } from '@/shared/gameText'
import { Skeleton } from '@/components/ui/skeleton'
import {
  getAffinityIconPath,
  getIdentityPassiveCountIconPath,
  getLockIconPath,
} from '@/shared/assets'
import { cn, getDisplayFontForNumeric, getDisplayFontForLanguage } from '@/lib/utils'
import { SECTION_STYLES } from '@/lib/constants'
import type { ImportancePalette } from '@/shared/gameData'
import { ImportanceDecoration, hasImportance, importanceBoxPadding } from '@/shared/importance'
import { passiveBoxLayout } from '../lib/passiveLayout'

interface IdentityPassiveCondition {
  type: string
  values: Record<string, number>
}

interface IdentityPassiveCardProps {
  renderName: (importance: NamePlateImportance) => ReactNode
  descSlot: ReactNode
  flavorSlot: ReactNode
  condition?: IdentityPassiveCondition | undefined
  isLocked: boolean
  importanceLevel: number
  palette: ImportancePalette
}

export function IdentityPassiveCard({
  renderName,
  descSlot,
  flavorSlot,
  condition,
  isLocked,
  importanceLevel,
  palette,
}: IdentityPassiveCardProps) {
  const { t, i18n } = useTranslation(['database', 'common'])
  const decorated = hasImportance(importanceLevel)
  const layout = passiveBoxLayout()

  return (
    <div
      className={cn('relative', isLocked && 'opacity-50', decorated && 'isolate @container')}
      style={importanceBoxPadding(importanceLevel)}
    >
      <ImportanceDecoration level={importanceLevel} palette={palette} variant="passive" />
      <div data-slot="passive-box" className="relative" style={layout.box}>
        <div className={SECTION_STYLES.LAYOUT.row} style={layout.plateRow}>
          <Suspense fallback={<NamePlateSkeleton kind="passive" attributeType="NEUTRAL" />}>
            {renderName({ level: importanceLevel, palette })}
          </Suspense>
        </div>
        {condition && (
          <div
            data-slot="passive-sin-row"
            className="flex items-center gap-3"
            style={layout.sinRow}
          >
            {Object.entries(condition.values).map(([affinity, count]) => (
              <span
                key={affinity}
                data-slot="passive-sin-item"
                className={SECTION_STYLES.LAYOUT.rowTight}
                style={layout.sinItem}
              >
                <img src={getAffinityIconPath(affinity)} alt={affinity} style={layout.sinIcon} />
                <img src={getIdentityPassiveCountIconPath()} alt="x" style={layout.sinTimes} />
                <span
                  className="-mt-3"
                  style={{ ...layout.sinCount, fontFamily: getDisplayFontForNumeric() }}
                >
                  {count}
                </span>
              </span>
            ))}
            <span
              data-slot="passive-sin-label"
              style={{ ...layout.sinLabel, ...getDisplayFontForLanguage(i18n.language) }}
            >
              {t(`passive.${condition.type.toLowerCase()}`)}
            </span>
          </div>
        )}
        <div data-slot="passive-info" className="text-sm" style={layout.info}>
          <Suspense fallback={<Skeleton className="h-4 w-full" />}>{descSlot}</Suspense>
        </div>
        <Suspense fallback={null}>{flavorSlot}</Suspense>
        {isLocked && (
          <img
            src={getLockIconPath()}
            alt=""
            data-slot="passive-lock"
            className="absolute -z-10 object-contain brightness-20"
            style={layout.lock}
          />
        )}
      </div>
    </div>
  )
}
