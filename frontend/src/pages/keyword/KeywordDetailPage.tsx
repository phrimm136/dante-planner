import { useParams } from '@tanstack/react-router'
import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { FormattedDescription } from '@/shared/gameText'
import { DetailPageLayout } from '@/components/layout/DetailPageLayout'
import { LabeledPanel } from '@/components/layout/LabeledPanel'
import { Skeleton } from '@/components/ui/skeleton'
import { KeywordBacklinkList } from './components/KeywordBacklinkList'
import { KeywordCard } from './components/KeywordCard'
import { KeywordDetailSkeleton } from './components/KeywordDetailSkeleton'
import { useKeywordDetailSpec, useKeywordDetailI18n } from './hooks/useKeywordDetailData'
import { useIdentityListI18n } from '@/pages/identity'
import { useEGOListI18n } from '@/pages/ego'
import { useEGOGiftListI18n } from '@/pages/egoGift'
import { getSinnerFromId } from '@/shared/gameData'
import colorCode from '@static/data/color/skillDescColorCode.json'
import { SECTION_STYLES } from '@/lib/constants'
import type { EGOId, IdentityId, SinnerScopedId } from '@/shared/gameData'

const colorMap = colorCode as Record<string, string>
const NEUTRAL_NAME_COLOR = colorCode.Neutral

function KeywordNameI18n({ id, buffType }: { id: string; buffType: string }) {
  const nameColor = colorMap[buffType] ?? NEUTRAL_NAME_COLOR

  return (
    <Suspense fallback={<Skeleton className="h-8 w-32" />}>
      <KeywordNameContent id={id} nameColor={nameColor} />
    </Suspense>
  )
}

function KeywordNameContent({ id, nameColor }: { id: string; nameColor: string }) {
  const i18nData = useKeywordDetailI18n(id)
  return (
    <h1 className={SECTION_STYLES.TEXT.pageTitle} style={{ color: nameColor }}>
      {i18nData?.name ?? id}
    </h1>
  )
}

function useSinnerScopedLabel() {
  const { t } = useTranslation('sinnerNames')

  return (id: SinnerScopedId, name: string) => {
    const sinnerKey = getSinnerFromId(id)
    return (
      <>
        {name.replace(/\n/g, ' ')} - {t(sinnerKey, { defaultValue: sinnerKey })}
      </>
    )
  }
}

function KeywordRelatedIdentities({ ids }: { ids: IdentityId[] }) {
  const names = useIdentityListI18n()
  const formatLabel = useSinnerScopedLabel()

  return (
    <KeywordBacklinkList
      labelKey="keyword.relatedIdentities"
      ids={ids}
      names={names}
      to="/identity/$id"
      formatLabel={formatLabel}
    />
  )
}

function KeywordRelatedEgos({ ids }: { ids: EGOId[] }) {
  const names = useEGOListI18n()
  const formatLabel = useSinnerScopedLabel()

  return (
    <KeywordBacklinkList
      labelKey="keyword.relatedEgos"
      ids={ids}
      names={names}
      to="/ego/$id"
      formatLabel={formatLabel}
    />
  )
}

function KeywordRelatedEgoGifts({ ids }: { ids: string[] }) {
  const names = useEGOGiftListI18n()

  return (
    <KeywordBacklinkList
      labelKey="keyword.relatedEgoGifts"
      ids={ids}
      names={names}
      to="/ego-gift/$id"
    />
  )
}

function KeywordDescriptionContent({ id }: { id: string }) {
  const { t } = useTranslation('database')
  const i18nData = useKeywordDetailI18n(id)

  return (
    <LabeledPanel>
      <h2 className={SECTION_STYLES.TEXT.sectionTitle}>{t('keyword.description')}</h2>
      {i18nData?.desc ? (
        <div className="text-sm leading-relaxed">
          <FormattedDescription text={i18nData.desc} />
        </div>
      ) : (
        <div className={SECTION_STYLES.TEXT.caption}>-</div>
      )}
    </LabeledPanel>
  )
}

const BacklinkSkeleton = () => (
  <div className="space-y-1.5">
    <Skeleton className="h-4 w-32" />
    <Skeleton className="h-4 w-48" />
    <Skeleton className="h-4 w-40" />
  </div>
)

function KeywordDetailContent() {
  const { id } = useParams({ strict: false })

  if (!id) {
    throw new Error('Keyword ID is required')
  }

  const spec = useKeywordDetailSpec(id)

  if (!spec) {
    throw new Error(`Keyword not found: ${id}`)
  }

  const leftColumn = (
    <div className="space-y-4">
      <div className="flex gap-4 items-center">
        <KeywordCard id={id} iconId={spec.iconId} />
        <KeywordNameI18n id={id} buffType={spec.buffType} />
      </div>

      <LabeledPanel>
        <Suspense fallback={<BacklinkSkeleton />}>
          <KeywordRelatedIdentities ids={spec.identities} />
        </Suspense>
        <Suspense fallback={<BacklinkSkeleton />}>
          <KeywordRelatedEgos ids={spec.egos} />
        </Suspense>
        <Suspense fallback={<BacklinkSkeleton />}>
          <KeywordRelatedEgoGifts ids={spec.egoGifts} />
        </Suspense>
      </LabeledPanel>
    </div>
  )

  const rightColumn = (
    <div className="space-y-4">
      <Suspense fallback={<Skeleton className="h-32 w-full rounded-lg" />}>
        <KeywordDescriptionContent id={id} />
      </Suspense>
    </div>
  )

  const mobileContent = rightColumn

  return (
    <DetailPageLayout
      leftColumn={leftColumn}
      rightColumn={rightColumn}
      mobileTabsContent={mobileContent}
    />
  )
}

export default function KeywordDetailPage() {
  return (
    <Suspense fallback={<KeywordDetailSkeleton />}>
      <KeywordDetailContent />
    </Suspense>
  )
}
