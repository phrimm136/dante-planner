import { Suspense, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { usePanicInfo, getPanicEntry } from '../hooks/usePanicInfo'
import { FormattedDescription } from '@/shared/gameText'
import { FormattedSanityText } from '@/shared/gameText'
import { Skeleton } from '@/components/ui/skeleton'
import { getPanicIconPath, getSanityIncIconPath, getSanityDecIconPath } from '@/shared/assets'
import { useSanityConditionFormatter } from '../hooks/useSanityConditionFormatter'
import { PANIC_TYPE_DESC_ONLY, SANITY_CONDITION_TYPE } from '@/shared/gameData'
import { SANITY_INDICATOR_COLORS, SECTION_STYLES } from '@/lib/constants'
import type { SanityConditionType } from '@/shared/gameData'
import { getDisplayFontForLanguage } from '@/lib/utils'
import { conditionLayout, panicLayout, type SanityConditionKind } from '../lib/sanityLayout'

interface PanicTypeSectionProps {
  panicType: string
  nameSlot: ReactNode
  descSlot: ReactNode
  lowMoraleSlot?: ReactNode
}

export function PanicTypeSection({
  panicType,
  nameSlot,
  descSlot,
  lowMoraleSlot,
}: PanicTypeSectionProps) {
  const { t, i18n } = useTranslation(['database', 'common'])
  const displayStyle = getDisplayFontForLanguage(i18n.language)
  const layout = panicLayout()
  const descOnly = panicType === PANIC_TYPE_DESC_ONLY

  return (
    <div data-slot="panic-section" style={layout.section}>
      <div
        data-slot="panic-tag-bg"
        aria-hidden="true"
        style={{
          ...layout.tagBg,
          border: `2px solid ${SANITY_INDICATOR_COLORS.INCREMENT_BORDER}`,
        }}
      />
      <span
        data-slot="panic-tag"
        className="flex items-center justify-center font-bold"
        style={{ ...layout.tagText, color: SANITY_INDICATOR_COLORS.INCREMENT, ...displayStyle }}
      >
        {t('sanity.panicType', 'Panic Type')}
      </span>
      <img
        data-slot="panic-icon"
        src={getPanicIconPath(panicType)}
        alt={t('common:a11y.panicType')}
        className="object-contain"
        style={layout.icon}
      />
      <div
        data-slot="panic-name"
        className="flex items-center justify-center text-center"
        style={layout.name}
      >
        <Suspense fallback={<Skeleton className="h-4 w-16" />}>{nameSlot}</Suspense>
      </div>

      <div data-slot="panic-desc" className="text-sm" style={layout.desc}>
        {!descOnly && lowMoraleSlot !== undefined && (
          <>
            <span data-slot="low-morale-effect">
              <span>·{t('sanity.lowMoraleEffect')}</span>
              <br />
              <Suspense fallback={<Skeleton className="h-8 w-full" />}>{lowMoraleSlot}</Suspense>
            </span>
            <br />
          </>
        )}
        {!descOnly && (
          <>
            <span>·{t('sanity.panicEffect')}</span>
            <br />
          </>
        )}
        <Suspense fallback={<Skeleton className="h-8 w-full" />}>{descSlot}</Suspense>
      </div>
    </div>
  )
}

export function PanicName({ name }: { name: string }) {
  const { i18n } = useTranslation()
  const displayStyle = getDisplayFontForLanguage(i18n.language)
  return <span style={{ ...displayStyle, color: SANITY_INDICATOR_COLORS.INCREMENT }}>{name}</span>
}

interface PanicTypeSectionI18nProps {
  panicType: string
}

export function PanicTypeSectionI18n({ panicType }: PanicTypeSectionI18nProps) {
  return (
    <Suspense fallback={<Skeleton className="w-full" style={panicLayout().section} />}>
      <PanicTypeSectionContent panicType={panicType} />
    </Suspense>
  )
}

function PanicTypeSectionContent({ panicType }: PanicTypeSectionI18nProps) {
  const { data: panicInfo } = usePanicInfo()
  const panicEntry = getPanicEntry(panicInfo, panicType)
  const lowMoraleDesc = panicEntry?.lowMoraleDesc ?? ''
  return (
    <PanicTypeSection
      panicType={panicType}
      nameSlot={<PanicName name={panicEntry?.name ?? ''} />}
      descSlot={<FormattedDescription text={panicEntry?.panicDesc ?? ''} />}
      {...(lowMoraleDesc !== '' && {
        lowMoraleSlot: <FormattedDescription text={lowMoraleDesc} />,
      })}
    />
  )
}

interface SanityConditionsSectionI18nProps {
  addConditions: string[]
  minConditions: string[]
}

export function SanityConditionsSectionI18n({
  addConditions,
  minConditions,
}: SanityConditionsSectionI18nProps) {
  const { t } = useTranslation(['database', 'common'])

  return (
    <>
      <SanityConditionSection
        kind="add"
        conditions={addConditions}
        type={SANITY_CONDITION_TYPE.INCREMENT}
        header={t('sanity.increaseHeader', 'Factors increasing Sanity')}
        emptyText={t('sanity.noIncrease', 'No sanity increase conditions')}
        color={SANITY_INDICATOR_COLORS.INCREMENT}
        borderColor={SANITY_INDICATOR_COLORS.INCREMENT_BORDER}
        iconPath={getSanityIncIconPath()}
      />
      <SanityConditionSection
        kind="min"
        conditions={minConditions}
        type={SANITY_CONDITION_TYPE.DECREMENT}
        header={t('sanity.decreaseHeader', 'Factors decreasing Sanity')}
        emptyText={t('sanity.noDecrease', 'No sanity decrease conditions')}
        color={SANITY_INDICATOR_COLORS.DECREMENT}
        borderColor={SANITY_INDICATOR_COLORS.DECREMENT_BORDER}
        iconPath={getSanityDecIconPath()}
      />
    </>
  )
}

interface SanityConditionSectionProps {
  kind: SanityConditionKind
  conditions: string[]
  type: SanityConditionType
  header: string
  emptyText: string
  color: string
  borderColor: string
  iconPath: string
}

function SanityConditionSection({
  kind,
  conditions,
  type,
  header,
  emptyText,
  color,
  borderColor,
  iconPath,
}: SanityConditionSectionProps) {
  const { i18n } = useTranslation()
  const displayStyle = getDisplayFontForLanguage(i18n.language)
  const layout = conditionLayout(kind)

  return (
    <div data-slot="sanity-condition" data-kind={kind} className="isolate" style={layout.section}>
      <span
        data-slot="sanity-condition-tag"
        className="flex items-center justify-center text-center font-bold"
        style={{ ...layout.tag, color, border: `2px solid ${borderColor}`, ...displayStyle }}
      >
        {header}
      </span>
      <div data-slot="sanity-condition-list" className="text-sm" style={layout.list}>
        {conditions.length > 0 ? (
          <Suspense fallback={<ConditionListSkeleton count={conditions.length} />}>
            <ConditionListContent conditions={conditions} type={type} />
          </Suspense>
        ) : (
          <div className={SECTION_STYLES.TEXT.muted}>{emptyText}</div>
        )}
      </div>
      <img
        data-slot="sanity-condition-icon"
        src={iconPath}
        alt=""
        className="pointer-events-none -z-10 object-contain"
        style={layout.icon}
      />
    </div>
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
