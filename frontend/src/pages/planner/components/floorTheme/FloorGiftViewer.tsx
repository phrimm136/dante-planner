import { useTranslation } from 'react-i18next'
import { useEGOGiftListSpec, useEGOGiftListI18n } from '@/pages/egoGift'
import { CardSlot } from '@/shared/cardLayout'
import { EGOGiftCard } from '@/pages/egoGift'
import { EGOGiftTooltip } from '@/pages/egoGift'
import { decodeAndOrderGiftSelections } from '@/pages/egoGift'
import { CARD_MOBILE_SCALE } from '@/lib/constants'
import { EGO_GIFT_GEOMETRY } from '@/pages/egoGift'
import type { EGOGiftListItem } from '@/pages/egoGift'
import type { EncodedGiftId, EnhancementLevel } from '@/shared/gameData'
import { cn } from '@/lib/utils'

interface FloorGiftViewerProps {
  selectedGiftIds: Set<EncodedGiftId>
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
      <div className="w-full">
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
  const spec = useEGOGiftListSpec()
  const i18n = useEGOGiftListI18n()

  const mobileScale = CARD_MOBILE_SCALE

  const selectedGifts = decodeAndOrderGiftSelections(selectedGiftIds, spec, i18n, 'tier-first')

  // Empty state
  if (selectedGifts.length === 0) {
    return (
      <button
        type="button"
        onClick={readOnly ? undefined : onClick}
        disabled={readOnly}
        aria-label={t('pages.plannerMD.selectFloorEgoGifts')}
        className={cn(
          'w-full h-full p-4 rounded-lg border-2 border-dashed border-muted-foreground/50',
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
        'w-full h-full rounded-lg text-left flex items-start',
        !readOnly && 'selectable',
        className,
      )}
    >
      <div className="flex flex-row flex-wrap items-start gap-2 p-2">
        {selectedGifts.map(({ item, enhancement }) => (
          <CardSlot key={item.id} size={EGO_GIFT_GEOMETRY.size} mobileScale={mobileScale}>
            <FloorGiftItem item={item} enhancement={enhancement} />
          </CardSlot>
        ))}
      </div>
    </button>
  )
}
