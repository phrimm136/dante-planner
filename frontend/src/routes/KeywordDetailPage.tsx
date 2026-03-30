/**
 * KeywordDetailPage - Keyword detail page with two-column layout
 *
 * Desktop: 4:6 ratio with description in right column
 * Mobile: Single column with all content stacked
 *
 * Pattern Source: EGOGiftDetailPage.tsx
 */

import { Link, useParams } from '@tanstack/react-router'
import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { FormattedDescription } from '@/components/common/FormattedDescription'
import { DetailPageLayout } from '@/components/common/DetailPageLayout'
import { DetailPageSkeleton } from '@/components/common/DetailPageSkeleton'
import { Skeleton } from '@/components/ui/skeleton'
import { KeywordCard } from '@/components/keyword/KeywordCard'
import { useKeywordDetailSpec, useKeywordDetailI18n } from '@/hooks/useKeywordDetailData'
import { useIdentityListI18n } from '@/hooks/useIdentityListData'
import { useEGOListI18n } from '@/hooks/useEGOListData'
import { useEGOGiftListI18n } from '@/hooks/useEGOGiftListData'
import { getSinnerFromId } from '@/lib/utils'
import colorCode from '@static/data/colorCode.json'

const colorMap = colorCode as Record<string, string>

/**
 * Keyword name display with buffType color.
 * Internal Suspense — does NOT suspend parent.
 *
 * Pattern Source: GiftNameI18n.tsx
 */
function KeywordNameI18n({ id, buffType }: { id: string; buffType: string }) {
  const nameColor = colorMap[buffType] ?? colorMap['Neutral']

  return (
    <Suspense fallback={<Skeleton className="h-8 w-32" />}>
      <KeywordNameContent id={id} nameColor={nameColor} />
    </Suspense>
  )
}

function KeywordNameContent({ id, nameColor }: { id: string; nameColor: string }) {
  const i18nData = useKeywordDetailI18n(id)
  return (
    <h1 className="text-2xl font-bold" style={{ color: nameColor }}>
      {i18nData?.name ?? id}
    </h1>
  )
}

/**
 * Backlink section for Related Identities.
 * Internal Suspense for independent language switching.
 */
function KeywordRelatedIdentities({ ids }: { ids: string[] }) {
  const { t } = useTranslation(['database', 'sinnerNames'])
  const nameList = useIdentityListI18n()

  return (
    <div className="space-y-1.5">
      <div className="text-xs text-muted-foreground font-medium uppercase tracking-wide">
        {t('keyword.relatedIdentities', { ns: 'database' })}
      </div>
      {ids.length === 0 ? (
        <div className="text-sm text-muted-foreground/60">-</div>
      ) : (
        <ul className="space-y-1">
          {ids.map((entityId) => {
            const sinnerKey = getSinnerFromId(entityId)
            const sinnerName = t(`${sinnerKey}`, { ns: 'sinnerNames', defaultValue: sinnerKey })
            const identityName = (nameList[entityId] ?? entityId).replace(/\n/g, ' ')
            return (
              <li key={entityId}>
                <Link
                  to="/identity/$id"
                  params={{ id: entityId }}
                  className="text-sm text-foreground underline hover:text-primary transition-colors"
                >
                  {identityName} - {sinnerName}
                </Link>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

/**
 * Backlink section for Related E.G.O.
 * Internal Suspense for independent language switching.
 */
function KeywordRelatedEgos({ ids }: { ids: string[] }) {
  const { t } = useTranslation(['database', 'sinnerNames'])
  const nameList = useEGOListI18n()

  return (
    <div className="space-y-1.5">
      <div className="text-xs text-muted-foreground font-medium uppercase tracking-wide">
        {t('keyword.relatedEgos', { ns: 'database' })}
      </div>
      {ids.length === 0 ? (
        <div className="text-sm text-muted-foreground/60">-</div>
      ) : (
        <ul className="space-y-1">
          {ids.map((entityId) => {
            const sinnerKey = getSinnerFromId(entityId)
            const sinnerName = t(`${sinnerKey}`, { ns: 'sinnerNames', defaultValue: sinnerKey })
            const egoName = (nameList[entityId] ?? entityId).replace(/\n/g, ' ')
            return (
              <li key={entityId}>
                <Link
                  to="/ego/$id"
                  params={{ id: entityId }}
                  className="text-sm text-foreground underline hover:text-primary transition-colors"
                >
                  {egoName} - {sinnerName}
                </Link>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

/**
 * Backlink section for Related E.G.O Gifts.
 * Internal Suspense for independent language switching.
 */
function KeywordRelatedEgoGifts({ ids }: { ids: string[] }) {
  const { t } = useTranslation('database')
  const nameList = useEGOGiftListI18n()

  return (
    <div className="space-y-1.5">
      <div className="text-xs text-muted-foreground font-medium uppercase tracking-wide">
        {t('keyword.relatedEgoGifts')}
      </div>
      {ids.length === 0 ? (
        <div className="text-sm text-muted-foreground/60">-</div>
      ) : (
        <ul className="space-y-1">
          {ids.map((entityId) => (
            <li key={entityId}>
              <Link
                to="/ego-gift/$id"
                params={{ id: entityId }}
                className="text-sm text-foreground underline hover:text-primary transition-colors"
              >
                {nameList[entityId] ?? entityId}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/**
 * Keyword description section with formatted keyword rendering.
 * Internal Suspense for independent language switching.
 */
function KeywordDescriptionContent({ id }: { id: string }) {
  const { t } = useTranslation('database')
  const i18nData = useKeywordDetailI18n(id)

  return (
    <div className="border rounded p-4 space-y-3">
      <h2 className="text-lg font-semibold">{t('keyword.description')}</h2>
      {i18nData?.desc ? (
        <div className="text-sm leading-relaxed">
          <FormattedDescription text={i18nData.desc} />
        </div>
      ) : (
        <div className="text-sm text-muted-foreground">-</div>
      )}
    </div>
  )
}

const BacklinkSkeleton = () => (
  <div className="space-y-1.5">
    <Skeleton className="h-4 w-32" />
    <Skeleton className="h-4 w-48" />
    <Skeleton className="h-4 w-40" />
  </div>
)

/**
 * Inner content component that uses Suspense-aware hooks.
 *
 * Pattern Source: EGOGiftDetailContent in EGOGiftDetailPage.tsx
 */
function KeywordDetailContent() {
  const { id } = useParams({ strict: false })

  if (!id) {
    throw new Error('Keyword ID is required')
  }

  const spec = useKeywordDetailSpec(id)

  if (!spec) {
    throw new Error(`Keyword not found: ${id}`)
  }

  // Left column: Header (card + name), Metadata (backlinks)
  const leftColumn = (
    <div className="space-y-4">
      {/* Header row: Card + Name (vertically centered) */}
      <div className="flex gap-4 items-center">
        <KeywordCard id={id} iconId={spec.iconId} />
        <KeywordNameI18n id={id} buffType={spec.buffType} />
      </div>

      {/* Backlinks panel */}
      <div className="border rounded p-4 space-y-4">
        <Suspense fallback={<BacklinkSkeleton />}>
          <KeywordRelatedIdentities ids={spec.identities} />
        </Suspense>
        <Suspense fallback={<BacklinkSkeleton />}>
          <KeywordRelatedEgos ids={spec.egos} />
        </Suspense>
        <Suspense fallback={<BacklinkSkeleton />}>
          <KeywordRelatedEgoGifts ids={spec.egoGifts} />
        </Suspense>
      </div>
    </div>
  )

  // Right column: Description
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

/**
 * KeywordDetailPage - Main export with Suspense boundary
 *
 * Pattern Source: EGOGiftDetailPage.tsx
 */
export default function KeywordDetailPage() {
  return (
    <Suspense fallback={<DetailPageSkeleton preset="keyword" />}>
      <KeywordDetailContent />
    </Suspense>
  )
}
