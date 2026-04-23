/**
 * AbEventDetailPage - Abnormality event detail page with two-column layout
 *
 * Desktop: 4:6 ratio (left: image + related gifts/packs, right: choices expanded)
 * Mobile: Single column, left on top, right below
 */

import { useParams, Link } from '@tanstack/react-router'
import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { DetailPageLayout } from '@/components/common/DetailPageLayout'
import { DetailPageSkeleton } from '@/components/common/DetailPageSkeleton'
import { ColoredText } from '@/components/common/ColoredText'
import { EGOGiftCard } from '@/components/egoGift/EGOGiftCard'
import { EGOGiftName } from '@/components/egoGift/EGOGiftName'
import { ChoiceBranch } from '@/components/abEvent/AbEventChoiceBranch'
import { Skeleton } from '@/components/ui/skeleton'
import { useAbEventDetailData, useAbEventShared } from '@/hooks/useAbEventDetailData'
import { useAbEventListSpec } from '@/hooks/useAbEventListData'
import { useEGOGiftListSpec, useEGOGiftListI18n } from '@/hooks/useEGOGiftListData'
import { useThemePackI18n } from '@/hooks/useThemePackListData'
import { getAbEventImagePath } from '@/lib/assetPaths'
import { createEffectTextResolver } from '@/lib/abEventTextResolver'
import type { CoinTossI18nContext } from '@/lib/abEventTextResolver'
import type { AbEventChoice } from '@/schemas/AbEventSchemas'

// =============================================================================
// Left Column Components
// =============================================================================

function EventImage({
  eventId,
  hasImage,
  illustId,
}: {
  eventId: string
  hasImage: boolean
  illustId?: string
}) {
  if (!hasImage && !illustId) {
    return (
      <div className="w-full aspect-[3/2] rounded-lg bg-muted flex items-center justify-center text-muted-foreground text-sm">
        {eventId}
      </div>
    )
  }
  return (
    <img
      src={getAbEventImagePath(illustId ?? eventId)}
      alt=""
      className="w-full rounded-lg"
    />
  )
}

function RelatedEgoGifts({ giftIds, label }: { giftIds: string[]; label: string }) {
  const spec = useEGOGiftListSpec()

  if (giftIds.length === 0) return null

  return (
    <div className="space-y-2">
      <div className="text-xs text-muted-foreground font-medium uppercase tracking-wide">
        {label}
      </div>
      <div className="flex flex-wrap gap-2">
        {giftIds.map((id) => {
          const giftSpec = spec[id]
          if (!giftSpec) return null
          return (
            <Link key={id} to="/ego-gift/$id" params={{ id }}>
              <div className="flex flex-col items-center gap-1">
                <EGOGiftCard
                  gift={{
                    id,
                    tag: giftSpec.tag,
                    keyword: giftSpec.keyword,
                    battleKeywordList: giftSpec.battleKeywordList ?? [],
                    attributeType: giftSpec.attributeType,
                    themePack: giftSpec.themePack,
                    maxEnhancement: giftSpec.maxEnhancement,
                  }}
                  enableHoverHighlight
                />
                <span className="text-xs text-center text-foreground line-clamp-2 w-24 leading-tight font-medium">
                  <EGOGiftName id={id} />
                </span>
              </div>
            </Link>
          )
        })}
      </div>
    </div>
  )
}

function RelatedThemePacks({ packIds, label }: { packIds: string[]; label: string }) {
  const themePackI18n = useThemePackI18n()

  if (packIds.length === 0) return null

  return (
    <div className="space-y-2">
      <div className="text-xs text-muted-foreground font-medium uppercase tracking-wide">
        {label}
      </div>
      <div className="text-sm">
        {packIds.map((id, idx) => (
          <span key={id}>
            {idx > 0 && ', '}
            <Link
              to="/theme-pack/$id"
              params={{ id }}
              className="hover:underline text-foreground"
            >
              {themePackI18n[id]?.name ?? id}
            </Link>
          </span>
        ))}
      </div>
    </div>
  )
}

// =============================================================================
// Main Page
// =============================================================================

function AbEventDetailContent() {
  const { id } = useParams({ strict: false })
  const { t } = useTranslation('database')

  if (!id) {
    throw new Error('AbEvent ID is required')
  }

  const { spec, i18n } = useAbEventDetailData(id)
  const abEventSpec = useAbEventListSpec()
  const specEntry = abEventSpec[id]
  const shared = useAbEventShared()
  const giftNames = useEGOGiftListI18n()

  const sinnerNameLabel = t('abEvent.sinnerName', 'Sinner Name')
  const processText = (text: string) =>
    text.replace(/\[?\{0\}\]?/g, `{${sinnerNameLabel}}`)

  const i18nCtx: CoinTossI18nContext = {
    affinityNames: shared.affinities ?? {},
    unitKeywords: shared.unitKeywords ?? {},
    sinnerNames: shared.sinnerNames ?? {},
    identityNames: shared.identityNames ?? {},
    successLabel: t('abEvent.success', 'SUCCESS'),
    failureLabel: t('abEvent.failure', 'FAILURE'),
  }

  const resolveEffectText = createEffectTextResolver(shared, giftNames)

  const leftColumn = (
    <div className="space-y-4">
      <EventImage eventId={id} hasImage={specEntry?.hasImage ?? false} illustId={specEntry?.illustId} />

      {i18n.desc && (
        <div className="text-sm text-muted-foreground whitespace-pre-line border rounded p-4">
          <ColoredText text={processText(i18n.desc)} />
        </div>
      )}

      {specEntry && (specEntry.relatedEgoGifts.length > 0 || specEntry.relatedThemePacks.length > 0) && (
        <div className="border rounded p-4 space-y-4">
          {specEntry.relatedEgoGifts.length > 0 && (
            <Suspense fallback={<Skeleton className="h-24 w-full" />}>
              <RelatedEgoGifts giftIds={specEntry.relatedEgoGifts} label={t('abEvent.relatedEgoGifts', 'Related EGO Gifts')} />
            </Suspense>
          )}
          {specEntry.relatedThemePacks.length > 0 && (
            <Suspense fallback={<Skeleton className="h-12 w-full" />}>
              <RelatedThemePacks packIds={specEntry.relatedThemePacks} label={t('abEvent.relatedThemePacks', 'Related Theme Packs')} />
            </Suspense>
          )}
        </div>
      )}
    </div>
  )

  const getSelectionKey = (choice: AbEventChoice): string | undefined => {
    if (!choice.nextEventId) return undefined
    const lastTwo = String(choice.nextEventId).slice(-2)
    return String(parseInt(lastTwo, 10))
  }

  const rightColumn = (
    <div className="space-y-4">
      {spec.choices?.map((choice, idx) => {
        const option = i18n.options?.[idx]
        const nextId = choice.nextEventId

        // Check if nextEventId maps to a sub-event (full ID match)
        const subEventId = nextId && spec.subEvents?.[String(nextId)] ? String(nextId) : undefined

        // Otherwise check for coin toss (last 2 digits)
        const selKey = !subEventId ? getSelectionKey(choice) : undefined
        const selectionEvent = selKey ? spec.selectionEvents?.[selKey] : undefined
        const selectionText = selKey ? i18n.selectionTexts?.[selKey] : undefined

        return (
          <ChoiceBranch
            key={choice.index}
            choice={choice}
            option={option}
            selectionEvent={selectionEvent}
            selectionText={selectionText}
            processText={processText}
            resolveEffectText={resolveEffectText}
            allSelectionEvents={spec.selectionEvents}
            allSelectionTexts={i18n.selectionTexts}
            subEvents={spec.subEvents}
            subEventTexts={i18n.subEventTexts}
            linkedSubEventId={subEventId}
            i18nCtx={i18nCtx}
          />
        )
      })}
    </div>
  )

  return (
    <DetailPageLayout
      leftColumn={leftColumn}
      rightColumn={rightColumn}
      mobileTabsContent={rightColumn}
    />
  )
}

export default function AbEventDetailPage() {
  return (
    <Suspense fallback={<DetailPageSkeleton preset="abEvent" />}>
      <AbEventDetailContent />
    </Suspense>
  )
}
