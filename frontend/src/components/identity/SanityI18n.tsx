import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { usePanicInfo, getPanicEntry } from '@/hooks/usePanicInfo'
import { FormattedDescription } from '@/components/common/FormattedDescription'
import { FormattedSanityText } from '@/components/common/FormattedSanityText'
import { Skeleton } from '@/components/ui/skeleton'
import { getPanicIconPath } from '@/lib/assetPaths'
import { useSanityConditionFormatter } from '@/lib/sanityConditionFormatter'
import { SANITY_INDICATOR_COLORS, SANITY_CONDITION_TYPE } from '@/lib/constants'
import type { SanityConditionType } from '@/lib/constants'
import { getDisplayFontForLanguage } from '@/lib/utils'

interface PanicTypeSectionI18nProps {
  /** Panic type ID */
  panicType: number
}

/**
 * Panic type section with granular i18n Suspense.
 * Structure stays visible, only name and description suspend.
 *
 * @example
 * <PanicTypeSectionI18n panicType={identity.panicType} />
 */
export function PanicTypeSectionI18n({ panicType }: PanicTypeSectionI18nProps) {
  const { t, i18n } = useTranslation(['database', 'common'])
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  return (
    <div className="flex gap-3">
      {/* Left column: centered header + image + name */}
      <div className="flex flex-col items-center">
        <div className="mb-2">
          <span
            className="font-bold px-3 py-1 text-sm"
            style={{ color: SANITY_INDICATOR_COLORS.INCREMENT, border: `2px solid ${SANITY_INDICATOR_COLORS.INCREMENT_BORDER}`, ...displayStyle }}
          >
            {t('sanity.panicType', 'Panic Type')}
          </span>
        </div>
        <img
          src={getPanicIconPath(panicType)}
          alt="Panic type"
          className="w-20 h-20 object-contain"
        />
        <div className="font-semibold text-sm mt-1">
          <Suspense fallback={<Skeleton className="h-4 w-16" />}>
            <SanityNameI18n panicType={panicType} />
          </Suspense>
        </div>
      </div>

      {/* Right column: description */}
      <div className="flex-1 text-sm">
        <div className="mt-8">
          <span>·{t('sanity.panicEffect')}</span>
        </div>
        <div>
          <Suspense fallback={<Skeleton className="h-8 w-full" />}>
            <SanityDescContent panicType={panicType} />
          </Suspense>
        </div>
      </div>
    </div>
  )
}

/**
 * Internal component that fetches and renders panic description.
 * Returns empty FormattedDescription if panic entry not found.
 */
function SanityDescContent({ panicType }: { panicType: number }) {
  const { data: panicInfo } = usePanicInfo()
  const panicEntry = getPanicEntry(panicInfo, panicType)
  const desc = panicEntry?.panicDesc ?? ''
  return <FormattedDescription text={desc} />
}

interface PanicTypeSkeletonProps {
  /** Panic type ID for icon display */
  panicType: number
}

/**
 * Skeleton for panic type section - keeps structure visible during loading.
 * Shows the panic icon immediately while text loads.
 */
export function PanicTypeSkeleton({ panicType }: PanicTypeSkeletonProps) {
  const { t } = useTranslation(['database', 'common'])

  return (
    <div className="flex gap-3">
      {/* Left column: centered header + image + name */}
      <div className="flex flex-col items-center">
        <div className="mb-2">
          <span
            className="font-bold px-3 py-1 text-sm"
            style={{ color: SANITY_INDICATOR_COLORS.INCREMENT, border: `2px solid ${SANITY_INDICATOR_COLORS.INCREMENT_BORDER}` }}
          >
            {t('sanity.panicType', 'Panic Type')}
          </span>
        </div>
        <img
          src={getPanicIconPath(panicType)}
          alt="Panic type"
          className="w-20 h-20 object-contain"
        />
        <Skeleton className="h-4 w-16 mt-1" />
      </div>

      {/* Right column: description */}
      <div className="flex-1 text-sm">
        <div className="mt-8">
          <span>·{t('sanity.panicEffect')}</span>
        </div>
        <Skeleton className="h-8 w-full" />
      </div>
    </div>
  )
}

// =============================================================================
// Granular I18n Components (text primitives)
// =============================================================================

interface SanityNameI18nProps {
  /** Panic type ID */
  panicType: number
}

/**
 * Suspending component that fetches and displays panic type name.
 * Uses useSuspenseQuery internally - MUST be wrapped in Suspense boundary.
 *
 * Returns just the panic name - caller handles styling.
 * Returns empty string if panic entry not found (defensive fallback).
 * This allows granular loading: sanity structure stays visible while only
 * the name shows skeleton during language change.
 *
 * @example
 * <Suspense fallback={<Skeleton className="h-4 w-16" />}>
 *   <SanityNameI18n panicType={identity.panicType} />
 * </Suspense>
 */
export function SanityNameI18n({ panicType }: SanityNameI18nProps) {
  const { i18n } = useTranslation()
  const { data: panicInfo } = usePanicInfo()
  const panicEntry = getPanicEntry(panicInfo, panicType)
  const name = panicEntry?.name ?? ''
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  return <span style={displayStyle}>{name}</span>
}

interface SanityDescI18nProps {
  /** Panic type ID */
  panicType: number
  /** Description type: 'panic' for panic description, 'lowMorale' for low morale description */
  descType: 'panic' | 'lowMorale'
}

/**
 * Suspending component that fetches and returns panic description.
 * Uses useSuspenseQuery internally - MUST be wrapped in Suspense boundary.
 *
 * Returns the description string - caller handles rendering.
 * Returns empty string if panic entry not found (defensive fallback).
 * This allows granular loading: sanity structure stays visible while only
 * the description shows skeleton during language change.
 *
 * @example
 * <Suspense fallback={<Skeleton className="h-8 w-full" />}>
 *   <SanityDescI18n panicType={identity.panicType} descType="panic" />
 * </Suspense>
 */
export function SanityDescI18n({ panicType, descType }: SanityDescI18nProps) {
  const { data: panicInfo } = usePanicInfo()
  const panicEntry = getPanicEntry(panicInfo, panicType)
  const desc = descType === 'panic'
    ? (panicEntry?.panicDesc ?? '')
    : (panicEntry?.lowMoraleDesc ?? '')

  return <>{desc}</>
}

// =============================================================================
// Sanity Conditions Section (increment/decrement factors)
// =============================================================================

interface SanityConditionsSectionI18nProps {
  /** Array of encoded sanity condition names */
  addConditions: string[]
  /** Array of encoded sanity condition names */
  minConditions: string[]
}

/**
 * Sanity conditions section with granular i18n Suspense.
 * Structure stays visible, only condition text suspends.
 *
 * @example
 * <SanityConditionsSectionI18n
 *   addConditions={mentalConditionInfo.add}
 *   minConditions={mentalConditionInfo.min}
 * />
 */
export function SanityConditionsSectionI18n({ addConditions, minConditions }: SanityConditionsSectionI18nProps) {
  const { t, i18n } = useTranslation(['database', 'common'])
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  return (
    <>
      {/* Sanity Increment Section */}
      <div>
        <div className="mb-2">
          <span
            className="font-bold px-3 py-1 text-sm"
            style={{ color: SANITY_INDICATOR_COLORS.INCREMENT, border: `2px solid ${SANITY_INDICATOR_COLORS.INCREMENT_BORDER}`, ...displayStyle }}
          >
            {t('sanity.increaseHeader', 'Factors increasing Sanity')}
          </span>
        </div>
        <div className="text-sm space-y-2 ml-1">
          {addConditions.length > 0 ? (
            <Suspense fallback={<ConditionListSkeleton count={addConditions.length} />}>
              <ConditionListContent conditions={addConditions} type={SANITY_CONDITION_TYPE.INCREMENT} />
            </Suspense>
          ) : (
            <div className="text-muted-foreground">{t('sanity.noIncrease', 'No sanity increase conditions')}</div>
          )}
        </div>
      </div>

      {/* Sanity Decrement Section */}
      <div>
        <div className="mb-2">
          <span
            className="font-bold px-3 py-1 text-sm"
            style={{ color: SANITY_INDICATOR_COLORS.DECREMENT, border: `2px solid ${SANITY_INDICATOR_COLORS.DECREMENT_BORDER}`, ...displayStyle }}
          >
            {t('sanity.decreaseHeader', 'Factors decreasing Sanity')}
          </span>
        </div>
        <div className="text-sm space-y-2 ml-1">
          {minConditions.length > 0 ? (
            <Suspense fallback={<ConditionListSkeleton count={minConditions.length} />}>
              <ConditionListContent conditions={minConditions} type={SANITY_CONDITION_TYPE.DECREMENT} />
            </Suspense>
          ) : (
            <div className="text-muted-foreground">{t('sanity.noDecrease', 'No sanity decrease conditions')}</div>
          )}
        </div>
      </div>
    </>
  )
}

/**
 * Internal component that fetches and renders condition list.
 */
function ConditionListContent({ conditions, type }: { conditions: string[]; type: SanityConditionType }) {
  const { formatAll } = useSanityConditionFormatter()
  return (
    <>
      {formatAll(conditions, type).map((desc, idx) => (
        <div key={idx}>
          <span>·</span>
          <FormattedSanityText text={desc} />
        </div>
      ))}
    </>
  )
}

/**
 * Skeleton for condition list.
 */
function ConditionListSkeleton({ count }: { count: number }) {
  return (
    <>
      {Array.from({ length: count }).map((_, idx) => (
        <Skeleton key={idx} className="h-4 w-3/4" />
      ))}
    </>
  )
}

/**
 * Skeleton for sanity conditions section during i18n loading.
 */
export function SanityConditionsSkeleton() {
  const { t } = useTranslation(['database', 'common'])

  return (
    <>
      <div>
        <div className="mb-2">
          <span
            className="font-bold px-3 py-1 text-sm"
            style={{ color: SANITY_INDICATOR_COLORS.INCREMENT, border: `2px solid ${SANITY_INDICATOR_COLORS.INCREMENT_BORDER}` }}
          >
            {t('sanity.increaseHeader', 'Factors increasing Sanity')}
          </span>
        </div>
        <div className="text-sm space-y-2 ml-1">
          <Skeleton className="h-4 w-3/4" />
          <Skeleton className="h-4 w-2/3" />
        </div>
      </div>
      <div>
        <div className="mb-2">
          <span
            className="font-bold px-3 py-1 text-sm"
            style={{ color: SANITY_INDICATOR_COLORS.DECREMENT, border: `2px solid ${SANITY_INDICATOR_COLORS.DECREMENT_BORDER}` }}
          >
            {t('sanity.decreaseHeader', 'Factors decreasing Sanity')}
          </span>
        </div>
        <div className="text-sm space-y-2 ml-1">
          <Skeleton className="h-4 w-3/4" />
          <Skeleton className="h-4 w-2/3" />
        </div>
      </div>
    </>
  )
}
