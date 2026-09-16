import { useEGOGiftListSpec, useEGOGiftListI18n } from '../hooks/useEGOGiftListData'
import type { EGOGiftListItem } from '../types/EGOGiftTypes'
import type { EncodedGiftId } from '@/shared/gameData'
import { getBaseGiftId } from '../lib/egoGiftEncoding'
import { CARD_MOBILE_SCALE } from '@/lib/constants'
import { EGO_GIFT_GEOMETRY } from '../lib/cardLayout'
import { CardSlot } from '@/shared/cardLayout'
import { EGOGiftCard } from './EGOGiftCard'
import { EGOGiftTooltip } from './EGOGiftTooltip'
import { toGiftListItems } from '../lib/giftListItem'

interface EGOGiftObservationSelectionProps {
  selectedGiftIds: EncodedGiftId[]
  onGiftRemove: (giftId: EncodedGiftId) => void
}

/**
 * EGO Gift Observation Selection Display
 * Portrait phone: Stack below, horizontal scroll, 0.8 scale
 * Landscape phone + tablet: Right side, vertical, 0.8 scale, w-24
 * Desktop: Right side, vertical, full size, w-32
 * Click on gift to remove from selection
 */
export function EGOGiftObservationSelection({
  selectedGiftIds,
  onGiftRemove,
}: EGOGiftObservationSelectionProps) {
  const spec = useEGOGiftListSpec()
  const i18n = useEGOGiftListI18n()

  // Merge spec and i18n into EGOGiftListItem array
  const gifts: EGOGiftListItem[] = toGiftListItems(spec, i18n)

  const mobileScale = CARD_MOBILE_SCALE

  return (
    <div className="bg-muted border border-border rounded-md p-4 overflow-x-auto sm:overflow-x-visible sm:h-[350px] flex flex-row sm:flex-col gap-2 items-center justify-center">
      {selectedGiftIds.map((giftId) => {
        const gift = gifts.find((g) => g.id === getBaseGiftId(giftId))
        if (!gift) return null

        return (
          <CardSlot key={giftId} size={EGO_GIFT_GEOMETRY.size} mobileScale={mobileScale}>
            <EGOGiftTooltip giftId={giftId} className="max-w-[320px]">
              <button
                type="button"
                onClick={() => {
                  onGiftRemove(giftId)
                }}
                className="block w-full cursor-pointer"
              >
                <EGOGiftCard gift={gift} isSelected={true} enableHoverHighlight />
              </button>
            </EGOGiftTooltip>
          </CardSlot>
        )
      })}
    </div>
  )
}
