import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { usePanicInfo, getPanicEntry } from '../hooks/usePanicInfo'
import { FormattedDescription } from '@/shared/gameText'
import { FormattedSanityText } from '@/shared/gameText'
import { Skeleton } from '@/components/ui/skeleton'
import { getPanicIconPath, getSanityIncIconPath, getSanityDecIconPath } from '@/shared/assets'
import { useSanityConditionFormatter } from '../hooks/useSanityConditionFormatter'
import { SANITY_CONDITION_TYPE } from '@/shared/gameData'
import { SANITY_INDICATOR_COLORS, SECTION_STYLES } from '@/lib/constants'
import type { SanityConditionType } from '@/shared/gameData'
import { getDisplayFontForLanguage } from '@/lib/utils'

interface PanicTypeSectionI18nProps {
  panicType: string
}

export function PanicTypeSectionI18n({ panicType }: PanicTypeSectionI18nProps) {
  const { t, i18n } = useTranslation(['database', 'common'])
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  return (
    <div className="flex gap-3">
      <div className="flex flex-col items-center">
        <div className="mb-2">
          <span
            className="font-bold px-3 py-1 text-sm"
            style={{
              color: SANITY_INDICATOR_COLORS.INCREMENT,
              border: `2px solid ${SANITY_INDICATOR_COLORS.INCREMENT_BORDER}`,
              ...displayStyle,
            }}
          >
            {t('sanity.panicType', 'Panic Type')}
          </span>
        </div>
        <img
          src={getPanicIconPath(panicType)}
          alt={t('common:a11y.panicType')}
          className="w-20 h-20 object-contain"
        />
        <div className="text-xl mt-1">
          <Suspense fallback={<Skeleton className="h-4 w-16" />}>
            <SanityNameI18n panicType={panicType} />
          </Suspense>
        </div>
      </div>

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

function SanityDescContent({ panicType }: { panicType: string }) {
  const { data: panicInfo } = usePanicInfo()
  const panicEntry = getPanicEntry(panicInfo, panicType)
  const desc = panicEntry?.panicDesc ?? ''
  return <FormattedDescription text={desc} />
}

interface SanityNameI18nProps {
  panicType: string
}

function SanityNameI18n({ panicType }: SanityNameI18nProps) {
  const { i18n } = useTranslation()
  const { data: panicInfo } = usePanicInfo()
  const panicEntry = getPanicEntry(panicInfo, panicType)
  const name = panicEntry?.name ?? ''
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  return <span style={{ ...displayStyle, color: SANITY_INDICATOR_COLORS.INCREMENT }}>{name}</span>
}

interface SanityConditionsSectionI18nProps {
  addConditions: string[]
  minConditions: string[]
}

export function SanityConditionsSectionI18n({
  addConditions,
  minConditions,
}: SanityConditionsSectionI18nProps) {
  const { t, i18n } = useTranslation(['database', 'common'])
  const displayStyle = getDisplayFontForLanguage(i18n.language)

  return (
    <>
      <div className="relative">
        <div className="mb-2">
          <span
            className="font-bold px-3 py-1 text-sm"
            style={{
              color: SANITY_INDICATOR_COLORS.INCREMENT,
              border: `2px solid ${SANITY_INDICATOR_COLORS.INCREMENT_BORDER}`,
              ...displayStyle,
            }}
          >
            {t('sanity.increaseHeader', 'Factors increasing Sanity')}
          </span>
        </div>
        <div className="text-sm space-y-2 ml-1 min-h-25">
          {addConditions.length > 0 ? (
            <Suspense fallback={<ConditionListSkeleton count={addConditions.length} />}>
              <ConditionListContent
                conditions={addConditions}
                type={SANITY_CONDITION_TYPE.INCREMENT}
              />
            </Suspense>
          ) : (
            <div className={SECTION_STYLES.TEXT.muted}>
              {t('sanity.noIncrease', 'No sanity increase conditions')}
            </div>
          )}
        </div>
        <img
          src={getSanityIncIconPath()}
          alt=""
          className="absolute right-0 bottom-0 h-25 opacity-20 pointer-events-none"
        />
      </div>

      <div className="relative">
        <div className="mb-2">
          <span
            className="font-bold px-3 py-1 text-sm"
            style={{
              color: SANITY_INDICATOR_COLORS.DECREMENT,
              border: `2px solid ${SANITY_INDICATOR_COLORS.DECREMENT_BORDER}`,
              ...displayStyle,
            }}
          >
            {t('sanity.decreaseHeader', 'Factors decreasing Sanity')}
          </span>
        </div>
        <div className="text-sm space-y-2 ml-1 min-h-25">
          {minConditions.length > 0 ? (
            <Suspense fallback={<ConditionListSkeleton count={minConditions.length} />}>
              <ConditionListContent
                conditions={minConditions}
                type={SANITY_CONDITION_TYPE.DECREMENT}
              />
            </Suspense>
          ) : (
            <div className={SECTION_STYLES.TEXT.muted}>
              {t('sanity.noDecrease', 'No sanity decrease conditions')}
            </div>
          )}
        </div>
        <img
          src={getSanityDecIconPath()}
          alt=""
          className="absolute right-0 bottom-0 h-25 opacity-20 pointer-events-none"
        />
      </div>
    </>
  )
}

function ConditionListContent({
  conditions,
  type,
}: {
  conditions: string[]
  type: SanityConditionType
}) {
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

function ConditionListSkeleton({ count }: { count: number }) {
  return (
    <>
      {Array.from({ length: count }).map((_, idx) => (
        <Skeleton key={idx} className="h-4 w-3/4" />
      ))}
    </>
  )
}
