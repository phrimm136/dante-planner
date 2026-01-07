import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { decodeGiftSelection } from '@/lib/egoGiftEncoding'
import { EMPTY_STATE } from '@/lib/constants'
import { cn } from '@/lib/utils'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { EnhancementLevel } from '@/lib/constants'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { PlannerSection } from '@/components/common/PlannerSection'
import { EGOGiftCard } from '@/components/egoGift/EGOGiftCard'
import { EGOGiftTooltipContent } from '@/components/egoGift/EGOGiftTooltipContent'

interface ComprehensiveGiftSummaryProps {
  selectedGiftIds: Set<string> // Encoded IDs (enhancement + giftId)
  onClick: () => void
}

interface DecodedGift {
  item: EGOGiftListItem
  enhancement: EnhancementLevel
}

/**
 * Displays selected EGO gifts for the comprehensive gift section.
 * Shows placeholder when empty, clicking opens selector pane.
 * Pattern: FloorGiftViewer (grid + tooltips) + PlannerSection wrapper
 * Suspends while loading - wrap in Suspense boundary
 */
export function ComprehensiveGiftSummary({
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
              <Tooltip key={item.id}>
                <TooltipTrigger asChild>
                  <div>
                    <EGOGiftCard
                      gift={item}
                      enhancement={enhancement}
                      isSelected
                    />
                  </div>
                </TooltipTrigger>
                <TooltipContent
                  side="bottom"
                  className="max-w-xs bg-gray-900 border border-gray-700 p-3"
                >
                  <EGOGiftTooltipContent
                    giftId={item.id}
                    enhancement={enhancement}
                  />
                </TooltipContent>
              </Tooltip>
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
}
