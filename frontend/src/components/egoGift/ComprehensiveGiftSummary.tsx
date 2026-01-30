import { useMemo, memo } from 'react'
import { useTranslation } from 'react-i18next'

import { decodeGiftSelection } from '@/lib/egoGiftEncoding'
import { EMPTY_STATE, CARD_GRID } from '@/lib/constants'
import { cn } from '@/lib/utils'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { EnhancementLevel } from '@/lib/constants'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { usePlannerEditorStoreSafe } from '@/stores/usePlannerEditorStore'
import { PlannerSection } from '@/components/common/PlannerSection'
import { ScaledCardWrapper } from '@/components/common/ScaledCardWrapper'
import { EGOGiftCard } from '@/components/egoGift/EGOGiftCard'
import { EGOGiftTooltip } from '@/components/egoGift/EGOGiftTooltip'

interface ComprehensiveGiftSummaryProps {
  onClick: () => void
  /** Override selectedGiftIds from store (for tracker mode) */
  selectedGiftIdsOverride?: Set<string> // Encoded IDs (enhancement + giftId)
}

interface DecodedGift {
  item: EGOGiftListItem
  enhancement: EnhancementLevel
}

/**
 * Individual gift item for summary display.
 * Memoized with custom comparison on item.id and enhancement to prevent
 * re-renders when other gifts are added/removed from selection.
 */
const SummaryGiftItem = memo(function SummaryGiftItem({
  item,
  enhancement,
  mobileScale,
}: DecodedGift & { mobileScale: number }) {
  return (
    <ScaledCardWrapper
      cardWidth={CARD_GRID.WIDTH.EGO_GIFT}
      cardHeight={CARD_GRID.HEIGHT.EGO_GIFT}
      mobileScale={mobileScale}
    >
      <EGOGiftTooltip giftId={item.id} enhancement={enhancement}>
        <div>
          <EGOGiftCard gift={item} enhancement={enhancement} />
        </div>
      </EGOGiftTooltip>
    </ScaledCardWrapper>
  )
}, (prev, next) => {
  // Only re-render if the gift ID or enhancement changed
  return prev.item.id === next.item.id && prev.enhancement === next.enhancement
})

/**
 * Displays selected EGO gifts for the comprehensive gift section.
 * Shows placeholder when empty, clicking opens selector pane.
 * Pattern: FloorGiftViewer (grid + tooltips) + PlannerSection wrapper
 * Suspends while loading - wrap in Suspense boundary
 */
export function ComprehensiveGiftSummary({
  onClick,
  selectedGiftIdsOverride,
}: ComprehensiveGiftSummaryProps) {
  // Store state (safe - returns undefined if outside context)
  const storeSelectedGiftIds = usePlannerEditorStoreSafe((s) => s.comprehensiveGiftIds)
  const selectedGiftIds = selectedGiftIdsOverride ?? storeSelectedGiftIds!
  const { t } = useTranslation(['planner', 'common'])
  const { spec, i18n } = useEGOGiftListData()

  // Breakpoint detection for scaling


  const mobileScale = CARD_GRID.MOBILE_SCALE.STANDARD

  // Decode selected IDs and convert to gift items with enhancement
  // Memoized to prevent re-computation on every render
  const selectedGifts = useMemo(() => {
    const gifts: DecodedGift[] = []
    for (const encodedId of selectedGiftIds) {
      const { giftId, enhancement } = decodeGiftSelection(encodedId)
      const giftSpec = spec[giftId]
      if (giftSpec) {
        gifts.push({
          item: {
            id: giftId,
            name: i18n[giftId] || giftId,
            tag: giftSpec.tag as EGOGiftListItem['tag'],
            keyword: giftSpec.keyword,
            attributeType: giftSpec.attributeType,
            themePack: giftSpec.themePack,
            maxEnhancement: giftSpec.maxEnhancement,
          },
          enhancement,
        })
      }
    }
    return gifts
  }, [selectedGiftIds, spec, i18n])

  const hasSelectedGifts = selectedGifts.length > 0

  return (
    <PlannerSection title={t('pages.plannerMD.comprehensiveEgoGiftList')}>
      <button
        type="button"
        onClick={onClick}
        aria-label={
          hasSelectedGifts
            ? t('pages.plannerMD.selectedEgoGifts')
            : t('pages.plannerMD.selectComprehensiveEgoGifts')
        }
        className="selectable w-full text-left cursor-pointer"
      >
        {hasSelectedGifts ? (
          <div className="flex flex-wrap gap-2 p-2 min-h-28">
            {selectedGifts.map(({ item, enhancement }) => (
              <SummaryGiftItem key={item.id} item={item} enhancement={enhancement} mobileScale={mobileScale} />
            ))}
          </div>
        ) : (
          <div
            className={cn(
              'flex items-center justify-center p-4 text-muted-foreground',
              EMPTY_STATE.MIN_HEIGHT,
              EMPTY_STATE.DASHED_BORDER
            )}
          >
            <span className="text-sm text-center">
              {t('pages.plannerMD.selectComprehensiveEgoGifts')}
            </span>
          </div>
        )}
      </button>
    </PlannerSection>
  )
}
