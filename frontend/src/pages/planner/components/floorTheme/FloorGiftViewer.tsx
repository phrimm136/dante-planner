import { useTranslation } from 'react-i18next'
import { useEGOGiftListData } from '@/pages/egoGift'
import { ScaledCardWrapper } from '@/components/layout/ScaledCardWrapper'
import { EGOGiftCard } from '@/pages/egoGift'
import { EGOGiftTooltip } from '@/pages/egoGift'
import { decodeGiftSelections, sortGiftSelections } from '@/pages/egoGift'
import { CARD_GRID } from '@/lib/constants'
import type { EGOGiftListItem } from '@/pages/egoGift'
import type { EnhancementLevel } from '@/shared/gameData'
import { cn } from '@/lib/utils'

interface FloorGiftViewerProps {
  selectedGiftIds: Set<string> // Encoded IDs (enhancement + giftId)
  onClick: () => void
  readOnly?: boolean
  className?: string
}

interface DecodedGift {
  item: EGOGiftListItem
  enhancement: EnhancementLevel
}

/**
 * Individual gift item in floor viewer.
 * Memoized to prevent re-renders when other gifts are added/removed.
 */
const FloorGiftItem = function FloorGiftItem({ item, enhancement }: DecodedGift) {
  return (
    <EGOGiftTooltip giftId={item.id} enhancement={enhancement}>
      <div>
        <EGOGiftCard gift={item} enhancement={enhancement} />
      </div>
    </EGOGiftTooltip>
  )
}

/**
 * Displays only the selected EGO gifts for a floor with their enhancement levels
 * Shows placeholder when empty, clicking opens selector pane
 * ReadOnly mode prevents interaction
 */
export function FloorGiftViewer({
  selectedGiftIds,
  onClick,
  readOnly = false,
  className,
}: FloorGiftViewerProps) {
  const { t } = useTranslation(['planner', 'common'])
  const { spec, i18n } = useEGOGiftListData()

  const mobileScale = CARD_GRID.MOBILE_SCALE.STANDARD

  const selectedGifts = sortGiftSelections(
    decodeGiftSelections(selectedGiftIds, spec, i18n),
    'tier-first',
  )

  // Empty state
  if (selectedGifts.length === 0) {
    return (
      <button
        type="button"
        onClick={readOnly ? undefined : onClick}
        disabled={readOnly}
        aria-label={t('pages.plannerMD.selectFloorEgoGifts')}
        className={cn(
          'translate-y-2 w-56 sm:w-full sm:h-100 p-4 rounded-lg border-2 border-dashed border-muted-foreground/50',
          'flex items-center justify-center',
          !readOnly && 'selectable',
          className,
        )}
      >
        <span className="text-sm text-muted-foreground text-center">
          {readOnly
            ? t('pages.plannerMD.emptyState.noFloorGifts')
            : t('pages.plannerMD.selectFloorEgoGifts')}
        </span>
      </button>
    )
  }

  return (
    <button
      type="button"
      onClick={readOnly ? undefined : onClick}
      disabled={readOnly}
      aria-label={t('pages.plannerMD.selectedEgoGifts')}
      className={cn(
        'w-full sm:min-h-104 rounded-lg text-left flex items-start',
        !readOnly && 'selectable',
        className,
      )}
    >
      <div className="flex flex-row flex-wrap items-start gap-2 p-2">
        {selectedGifts.map(({ item, enhancement }) => (
          <ScaledCardWrapper
            key={item.id}
            cardWidth={CARD_GRID.WIDTH.EGO_GIFT}
            cardHeight={CARD_GRID.HEIGHT.EGO_GIFT}
            mobileScale={mobileScale}
          >
            <FloorGiftItem item={item} enhancement={enhancement} />
          </ScaledCardWrapper>
        ))}
      </div>
    </button>
  )
}
