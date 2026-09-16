import { useTranslation } from 'react-i18next'

import { DetailPageSkeleton } from '@/components/feedback/DetailPageSkeleton'
import { TextSkeleton } from '@/components/feedback/TextSkeleton'
import { LabeledPanel } from '@/components/layout/LabeledPanel'
import { Skeleton } from '@/components/ui/skeleton'
import { CARD_MOBILE_SCALE_NONE } from '@/lib/constants'
import { CardSlot, EGO_GIFT_GEOMETRY, THEME_PACK_GEOMETRY } from '@/shared/cardLayout'
import { SectionTitle } from './SectionTitle'

/** One labelled metadata row of the left column's panel. */
function MetadataBlock() {
  return (
    <div className="space-y-1">
      <TextSkeleton size="xs" width="sm" />
      <div className="flex flex-wrap gap-2">
        <div className="h-5 w-16 rounded bg-muted" />
        <div className="h-5 w-16 rounded bg-muted" />
      </div>
    </div>
  )
}

/** One named EGO gift card of a gift row. */
function NamedGiftBlock() {
  return (
    <div className="flex flex-col items-center gap-1">
      <CardSlot size={EGO_GIFT_GEOMETRY.size} mobileScale={CARD_MOBILE_SCALE_NONE}>
        <Skeleton className="size-full rounded-lg" />
      </CardSlot>
      <TextSkeleton size="xs" lines={2} width="full" />
    </div>
  )
}

/** One abnormality event card: landscape image with a clamped description below. */
function EventBlock() {
  return (
    <div className="w-40">
      <div className="flex flex-col gap-1.5">
        <Skeleton className="w-full aspect-[3/2] rounded-sm" />
        <TextSkeleton size="xs" lines={2} width="full" />
      </div>
    </div>
  )
}

/**
 * Theme Pack detail: Card image + difficulty/floor metadata (left)
 * Specific gifts + events sections (right)
 */
export function ThemePackDetailSkeleton() {
  const { t } = useTranslation('database')

  return (
    <DetailPageSkeleton
      left={
        <div className="flex gap-4">
          {/* Theme pack card image */}
          <CardSlot size={THEME_PACK_GEOMETRY.size} className="shrink-0">
            <Skeleton className="size-full rounded-lg" />
          </CardSlot>
          {/* Metadata panel: difficulty + floors + hidden theme rate */}
          <LabeledPanel className="flex-1">
            <MetadataBlock />
            <MetadataBlock />
            <MetadataBlock />
          </LabeledPanel>
        </div>
      }
      right={
        <div className="space-y-6">
          {/* Section: Exclusive EGO gifts */}
          <div className="space-y-3">
            <SectionTitle>{t('themePack.exclusiveGifts', 'Exclusive EGO Gifts')}</SectionTitle>
            <div className="flex flex-wrap gap-3">
              <NamedGiftBlock />
              <NamedGiftBlock />
              <NamedGiftBlock />
            </div>
          </div>
          {/* Section: Exclusive events */}
          <div className="space-y-3">
            <SectionTitle>
              {t('themePack.exclusiveEvents', 'Exclusive Dungeon Events')}
            </SectionTitle>
            <div className="flex flex-wrap gap-3">
              <EventBlock />
              <EventBlock />
            </div>
          </div>
          {/* Section: All acquirable EGO gifts */}
          <div className="space-y-3">
            <SectionTitle>{t('themePack.allGifts', 'All Acquirable EGO Gifts')}</SectionTitle>
            <div className="flex flex-wrap gap-2">
              {Array.from({ length: 12 }).map((_, i) => (
                <CardSlot
                  key={i}
                  size={EGO_GIFT_GEOMETRY.size}
                  mobileScale={CARD_MOBILE_SCALE_NONE}
                >
                  <Skeleton className="size-full rounded-lg" />
                </CardSlot>
              ))}
            </div>
          </div>
          {/* Section: All encounterable events */}
          <div className="space-y-3">
            <SectionTitle>{t('themePack.allEvents', 'All Encounterable Events')}</SectionTitle>
            <div className="flex flex-wrap gap-3">
              <EventBlock />
              <EventBlock />
              <EventBlock />
            </div>
          </div>
        </div>
      }
    />
  )
}
