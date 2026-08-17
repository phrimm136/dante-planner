import { memo } from 'react'
import { useTranslation } from 'react-i18next'

import { decodeAndOrderGiftSelections } from '@/pages/egoGift'
import { EMPTY_STATE, CARD_GRID } from '@/lib/constants'
import { cn } from '@/lib/utils'
import type { EGOGiftListItem } from '@/pages/egoGift'
import type { EnhancementLevel } from '@/shared/gameData'
import { useEGOGiftListData } from '@/pages/egoGift'
import { usePlannerEditorStore } from '../../stores/usePlannerEditorStore'
import { PlannerSection } from '@/components/layout/PlannerSection'
import { ScaledCardWrapper } from '@/components/layout/ScaledCardWrapper'
import { EGOGiftCard } from '@/pages/egoGift'
import { EGOGiftTooltip } from '@/pages/egoGift'

export interface ComprehensiveGiftSummaryProps {
  onClick: () => void
  /** Encoded IDs (enhancement + giftId) */
  selectedGiftIds: Set<string>
}

interface DecodedGift {
  item: EGOGiftListItem
  enhancement: EnhancementLevel
}

/**
 * Individual gift item for summary display.
 *
 * `decodeGiftSelections` mints a fresh `item` per render, so the default
 * comparison never bails out. `id` and `name` are the only fields the card
 * renders that vary — `name` carries the active language.
 */
const SummaryGiftItem = memo(
  SummaryGiftItemImpl,
  (prev, next) =>
    prev.item.id === next.item.id &&
    prev.item.name === next.item.name &&
    prev.enhancement === next.enhancement &&
    prev.mobileScale === next.mobileScale,
)

function SummaryGiftItemImpl({
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
}

/**
 * Displays selected EGO gifts for the comprehensive gift section.
 * Shows placeholder when empty, clicking opens selector pane.
 * Pattern: FloorGiftViewer (grid + tooltips) + PlannerSection wrapper
 * Suspends while loading - wrap in Suspense boundary
 */
export function ComprehensiveGiftSummary({
  onClick,
  selectedGiftIds,
}: ComprehensiveGiftSummaryProps) {
  const { t } = useTranslation(['planner', 'common'])
  const { spec, i18n } = useEGOGiftListData()

  const mobileScale = CARD_GRID.MOBILE_SCALE.STANDARD

  const selectedGifts = decodeAndOrderGiftSelections(selectedGiftIds, spec, i18n, 'tier-first')

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
              <SummaryGiftItem
                key={item.id}
                item={item}
                enhancement={enhancement}
                mobileScale={mobileScale}
              />
            ))}
          </div>
        ) : (
          <div
            className={cn(
              'flex items-center justify-center p-4 text-muted-foreground',
              EMPTY_STATE.MIN_HEIGHT,
              EMPTY_STATE.DASHED_BORDER,
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

/** Props a store-bound caller supplies; the selection comes from the store. */
export type StoreBoundComprehensiveGiftSummaryProps = Omit<
  ComprehensiveGiftSummaryProps,
  'selectedGiftIds'
>

/** Renders the summary against the comprehensive gifts held by the planner editor store. */
export function StoreBoundComprehensiveGiftSummary(props: StoreBoundComprehensiveGiftSummaryProps) {
  const selectedGiftIds = usePlannerEditorStore((s) => s.comprehensiveGiftIds)

  return <ComprehensiveGiftSummary {...props} selectedGiftIds={selectedGiftIds} />
}
