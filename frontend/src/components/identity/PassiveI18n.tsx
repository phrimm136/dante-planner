import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { useIdentityDetailI18n } from '@/hooks/useIdentityDetailData'
import { FormattedDescription } from '@/components/common/FormattedDescription'
import { StyledSkillName, StyledNameSkeleton } from '@/components/common/StyledName'
import { Skeleton } from '@/components/ui/skeleton'
import { getAffinityIconPath, getIdentityPassiveCountIconPath, getLockIconPath } from '@/lib/assetPaths'
import { cn, getDisplayFontForNumeric, getDisplayFontForLanguage } from '@/lib/utils'

interface PassiveCondition {
  type: string
  values: Record<string, number>
}

interface PassiveCardProps {
  /** Passive name */
  name: string
  /** Passive description */
  desc: string
  /** Passive activation condition (affinity requirements) */
  condition?: PassiveCondition
  /** Whether this passive is locked (from higher tier) */
  isLocked: boolean
}

interface PassiveCardWithSuspenseProps {
  /** Identity ID for i18n lookup */
  id: string
  /** Passive ID */
  passiveId: number
  /** Passive activation condition (affinity requirements) */
  condition?: PassiveCondition
  /** Whether this passive is locked (from higher tier) */
  isLocked: boolean
}

/**
 * PassiveCard - Base component that displays passive information.
 * Takes name and desc as props directly.
 */
export function PassiveCard({
  name,
  desc,
  condition,
  isLocked,
}: PassiveCardProps) {
  const { t } = useTranslation(['database', 'common'])

  return (
    <div className={cn('relative space-y-1', isLocked && 'opacity-50')}>
      <div className="flex items-center gap-2">
        <StyledSkillName name={name} attributeType="NEUTRAL" />
      </div>
      {isLocked && (
        <img
          src={getLockIconPath()}
          alt=""
          className="absolute right-4 bottom-0 -z-10 h-20 brightness-50"
        />
      )}
      {condition && (
        <div className="flex items-center gap-3 text-md ml-1">
          {Object.entries(condition.values).map(([affinity, count]) => (
            <span key={affinity} className="flex items-center gap-1">
              <img
                src={getAffinityIconPath(affinity)}
                alt={affinity}
                className="w-8 h-8"
              />
              <img
                src={getIdentityPassiveCountIconPath()}
                alt="x"
                className="w-4 h-4"
              />
              <span>{count}</span>
            </span>
          ))}
          <span>{t(`passive.${condition.type.toLowerCase()}`)}</span>
        </div>
      )}
      <div className="text-sm">
        <FormattedDescription text={desc} />
      </div>
    </div>
  )
}

// =============================================================================
// Granular I18n Version
// =============================================================================

/**
 * PassiveCard with granular i18n Suspense.
 * Structure (condition icons, locked indicator) stays visible,
 * only name and description suspend.
 */
export function PassiveCardWithSuspense({
  id,
  passiveId,
  condition,
  isLocked,
}: PassiveCardWithSuspenseProps) {
  const { t, i18n } = useTranslation(['database', 'common'])

  return (
    <div className={cn('relative space-y-1', isLocked && 'opacity-50')}>
      <div className="flex items-center gap-2">
        <Suspense fallback={<StyledNameSkeleton attributeType="NEUTRAL" />}>
          <PassiveNameContent id={id} passiveId={passiveId} />
        </Suspense>
      </div>
      {isLocked && (
        <img
          src={getLockIconPath()}
          alt=""
          className="absolute right-4 bottom-0 -z-10 h-20 brightness-50"
        />
      )}
      {condition && (
        <div className="flex items-center gap-3 text-md ml-1">
          {Object.entries(condition.values).map(([affinity, count]) => (
            <span key={affinity} className="flex items-center gap-1">
              <img
                src={getAffinityIconPath(affinity)}
                alt={affinity}
                className="h-8"
              />
              <img
                src={getIdentityPassiveCountIconPath()}
                alt="x"
                className="w-4 h-4"
              />
              <span 
                className="text-[32px] leading-[32px] -mt-3"
                style={{ fontFamily: getDisplayFontForNumeric() }}
              >
                {count}
              </span>
            </span>
          ))}
          <span
            className="text-lg"
            style={getDisplayFontForLanguage(i18n.language)}
          >
            {t(`passive.${condition.type.toLowerCase()}`)}
          </span>
        </div>
      )}
      <div className="text-sm">
        <Suspense fallback={<Skeleton className="h-4 w-full" />}>
          <PassiveDescContent id={id} passiveId={passiveId} />
        </Suspense>
      </div>
    </div>
  )
}

/**
 * Internal: Fetches and renders passive name with styled formatting.
 */
function PassiveNameContent({ id, passiveId }: { id: string; passiveId: number }) {
  const i18n = useIdentityDetailI18n(id)
  const passiveI18n = i18n.passives[String(passiveId)]
  return <StyledSkillName name={passiveI18n?.name ?? ''} attributeType="NEUTRAL" />
}

/**
 * Internal: Fetches and renders passive description.
 */
function PassiveDescContent({ id, passiveId }: { id: string; passiveId: number }) {
  const i18n = useIdentityDetailI18n(id)
  const passiveI18n = i18n.passives[String(passiveId)]
  return <FormattedDescription text={passiveI18n?.desc ?? ''} />
}

/**
 * Alias for PassiveCardWithSuspense.
 * Used in IdentityDetailPage.tsx - kept for semantic clarity
 * (I18n suffix indicates this component handles its own i18n fetching).
 */
export { PassiveCardWithSuspense as PassiveCardI18n }
