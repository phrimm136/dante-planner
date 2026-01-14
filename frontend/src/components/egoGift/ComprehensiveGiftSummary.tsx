import { useMemo, memo } from 'react'
import { useTranslation } from 'react-i18next'

import { decodeGiftSelection } from '@/lib/egoGiftEncoding'
import { EMPTY_STATE } from '@/lib/constants'
import { cn } from '@/lib/utils'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { EnhancementLevel } from '@/lib/constants'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { PlannerSection } from '@/components/common/PlannerSection'
import { EGOGiftCard } from '@/components/egoGift/EGOGiftCard'
import { EGOGiftTooltip } from '@/components/egoGift/EGOGiftTooltip'

interface ComprehensiveGiftSummaryProps {
  selectedGiftIds: Set<string> // Encoded IDs (enhancement + giftId)
  onClick: () => void
}

interface DecodedGift {
  item: EGOGiftListItem
  enhancement: EnhancementLevel
}

interface SummaryGiftItemProps {
  item: EGOGiftListItem
  enhancement: EnhancementLevel
}

function areSummaryGiftItemPropsEqual(
  prev: SummaryGiftItemProps,
  next: SummaryGiftItemProps
): boolean {
  return prev.item.id === next.item.id && prev.enhancement === next.enhancement
}

/**
 * Memoized individual gift item for summary display.
 * Prevents re-render when other gifts change selection.
 * Scoped locally to avoid affecting other components (e.g., StartGiftEditPane).
 * Custom comparator uses item.id since item objects are recreated on each render.
 */
const SummaryGiftItem = memo(function SummaryGiftItem({
  item,
  enhancement,
}: SummaryGiftItemProps) {
  return (
    <EGOGiftTooltip giftId={item.id} enhancement={enhancement}>
      <div>
        <EGOGiftCard gift={item} enhancement={enhancement} />
      </div>
    </EGOGiftTooltip>
  )
}, areSummaryGiftItemPropsEqual)

// Custom comparison - compare Set by size and elements
function areComprehensiveGiftSummaryPropsEqual(
  prev: ComprehensiveGiftSummaryProps,
  next: ComprehensiveGiftSummaryProps
): boolean {
  // Compare selectedGiftIds Set
  if (prev.selectedGiftIds !== next.selectedGiftIds) {
    if (prev.selectedGiftIds.size !== next.selectedGiftIds.size) return false
    for (const id of prev.selectedGiftIds) {
      if (!next.selectedGiftIds.has(id)) return false
    }
  }
  // onClick excluded - should be stable from parent
  return true
}

/**
 * Displays selected EGO gifts for the comprehensive gift section.
 * Shows placeholder when empty, clicking opens selector pane.
 * Pattern: FloorGiftViewer (grid + tooltips) + PlannerSection wrapper
 * Suspends while loading - wrap in Suspense boundary
 */
export const ComprehensiveGiftSummary = memo(function ComprehensiveGiftSummary({
  selectedGiftIds,
  onClick,
}: ComprehensiveGiftSummaryProps) {
  const { t } = useTranslation(['planner', 'common'])
  const { spec, i18n } = useEGOGiftListData()

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
            : t('pages.plannerMD.selectEgoGifts')
        }
        className="selectable w-full text-left cursor-pointer"
      >
        {hasSelectedGifts ? (
          <div className="flex flex-wrap gap-2 p-2 min-h-28">
            {selectedGifts.map(({ item, enhancement }) => (
              <SummaryGiftItem key={item.id} item={item} enhancement={enhancement} />
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
              {t('pages.plannerMD.selectEgoGifts')}
            </span>
          </div>
        )}
      </button>
    </PlannerSection>
  )
}, areComprehensiveGiftSummaryPropsEqual)
