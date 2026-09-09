/**
 * AbEventDetailPage - Abnormality event detail page with two-column layout
 *
 * Desktop: 4:6 ratio (left: image + related gifts/packs, right: choices expanded)
 * Mobile: Single column, left on top, right below
 */

import { useParams, Link } from '@tanstack/react-router'
import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'

import { DetailPageLayout } from '@/components/layout/DetailPageLayout'
import { ColoredText } from '@/shared/gameText'
import { EGOGiftGrid } from '@/pages/egoGift'
import { Skeleton } from '@/components/ui/skeleton'
import { useEGOGiftListSpec, useEGOGiftListI18n } from '@/pages/egoGift'
import { useThemePackListI18n } from '@/pages/themePack'
import { getAbEventImagePath } from '@/shared/assets'
import { AbEventIdSchema } from '@/shared/gameData'
import type { AbEventId } from '@/shared/gameData'
import {
  AbEventDetailSkeleton,
  ChoiceBranch,
  useAbEventDetailSpec,
  useAbEventDetailI18n,
  useAbEventShared,
  useAbEventListSpec,
  createEffectTextResolver,
} from '@/pages/abEvent'
import type { CoinTossI18nContext, AbEventChoice } from '@/pages/abEvent'

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
  illustId?: string | undefined
}) {
  if (!hasImage && !illustId) {
    return (
      <div className="w-full aspect-[3/2] rounded-lg bg-muted flex items-center justify-center text-muted-foreground text-sm">
        {eventId}
      </div>
    )
  }
  return <img src={getAbEventImagePath(illustId ?? eventId)} alt="" className="w-full rounded-lg" />
}

function RelatedEgoGifts({ giftIds, label }: { giftIds: string[]; label: string }) {
  const spec = useEGOGiftListSpec()

  if (giftIds.length === 0) return null

  return (
    <div className="space-y-2">
      <div className="text-xs text-muted-foreground font-medium uppercase tracking-wide">
        {label}
      </div>
      <EGOGiftGrid ids={giftIds} spec={spec} showName />
    </div>
  )
}

function RelatedThemePacks({ packIds, label }: { packIds: string[]; label: string }) {
  const themePackI18n = useThemePackListI18n()

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
            <Link to="/theme-pack/$id" params={{ id }} className="hover:underline text-foreground">
              {themePackI18n[id]?.name ?? id}
            </Link>
          </span>
        ))}
      </div>
    </div>
  )
}

/** Swaps the game's `{0}` sinner placeholder for the localized label. */
function replaceSinnerPlaceholder(text: string, sinnerNameLabel: string): string {
  return text.replace(/\[?\{0\}\]?/g, `{${sinnerNameLabel}}`)
}

function EventDescription({ id }: { id: AbEventId }) {
  const { t } = useTranslation('database')
  const i18n = useAbEventDetailI18n(id)

  if (!i18n.desc) return null

  return (
    <div className="text-sm text-muted-foreground whitespace-pre-line border rounded p-4">
      <ColoredText
        text={replaceSinnerPlaceholder(i18n.desc, t('abEvent.sinnerName', 'Sinner Name'))}
      />
    </div>
  )
}

// =============================================================================
// Right Column Components
// =============================================================================

function getSelectionKey(choice: AbEventChoice): string | undefined {
  if (!choice.nextEventId) return undefined
  const lastTwo = choice.nextEventId.slice(-2)
  return String(parseInt(lastTwo, 10))
}

function EventChoices({
  id,
  spec,
}: {
  id: AbEventId
  spec: ReturnType<typeof useAbEventDetailSpec>
}) {
  const { t } = useTranslation('database')
  const i18n = useAbEventDetailI18n(id)
  const shared = useAbEventShared()
  const giftNames = useEGOGiftListI18n()

  const sinnerNameLabel = t('abEvent.sinnerName', 'Sinner Name')
  const processText = (text: string) => replaceSinnerPlaceholder(text, sinnerNameLabel)

  const i18nCtx: CoinTossI18nContext = {
    affinityNames: shared.affinities ?? {},
    unitKeywords: shared.unitKeywords ?? {},
    sinnerNames: shared.sinnerNames ?? {},
    identityNames: shared.identityNames ?? {},
    successLabel: t('abEvent.success', 'SUCCESS'),
    failureLabel: t('abEvent.failure', 'FAILURE'),
  }

  const resolveEffectText = createEffectTextResolver(shared, giftNames)

  return (
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
}

// =============================================================================
// Main Page
// =============================================================================

function AbEventDetailContent() {
  const { id: rawId } = useParams({ strict: false })
  const { t } = useTranslation('database')

  if (!rawId) {
    throw new Error('AbEvent ID is required')
  }
  const id = AbEventIdSchema.parse(rawId)

  const spec = useAbEventDetailSpec(id)
  const specEntry = useAbEventListSpec()[id]

  const leftColumn = (
    <div className="space-y-4">
      <EventImage
        eventId={id}
        hasImage={specEntry?.hasImage ?? false}
        illustId={specEntry?.illustId}
      />

      <Suspense fallback={<Skeleton className="h-16 w-full" />}>
        <EventDescription id={id} />
      </Suspense>

      {specEntry &&
        (specEntry.relatedEgoGifts.length > 0 || specEntry.relatedThemePacks.length > 0) && (
          <div className="border rounded p-4 space-y-4">
            {specEntry.relatedEgoGifts.length > 0 && (
              <Suspense fallback={<Skeleton className="h-24 w-full" />}>
                <RelatedEgoGifts
                  giftIds={specEntry.relatedEgoGifts}
                  label={t('abEvent.relatedEgoGifts', 'Related EGO Gifts')}
                />
              </Suspense>
            )}
            {specEntry.relatedThemePacks.length > 0 && (
              <Suspense fallback={<Skeleton className="h-12 w-full" />}>
                <RelatedThemePacks
                  packIds={specEntry.relatedThemePacks}
                  label={t('abEvent.relatedThemePacks', 'Related Theme Packs')}
                />
              </Suspense>
            )}
          </div>
        )}
    </div>
  )

  const rightColumn = (
    <Suspense fallback={<Skeleton className="h-40 w-full" />}>
      <EventChoices id={id} spec={spec} />
    </Suspense>
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
    <Suspense fallback={<AbEventDetailSkeleton />}>
      <AbEventDetailContent />
    </Suspense>
  )
}
