import { useEGOGiftListSpec, useEGOGiftListI18n } from '../hooks/useEGOGiftListData'
import type { EGOGiftEntity } from '../types/EGOGiftTypes'
import type { EncodedGiftId } from '@/shared/gameData'
import { getBaseGiftId } from '../lib/egoGiftEncoding'
import { CARD_MOBILE_SCALE, SM_BREAKPOINT_PX } from '@/lib/constants'
import { useIsBreakpoint } from '@/components/hooks/use-is-breakpoint'
import { observationListHeightPx } from '../lib/cardLayout'
import { CardSlot, EGO_GIFT_GEOMETRY, useSlotSizePx } from '@/shared/cardLayout'
import { EGOGiftCard } from './EGOGiftCard'
import { EGOGiftTooltip } from './EGOGiftTooltip'
import { toEGOGiftEntity } from '../lib/egoGiftEntity'

interface EGOGiftObservationSelectionProps {
  selectedGiftIds: EncodedGiftId[]
  onGiftRemove: (giftId: EncodedGiftId) => void
}

/**
 * EGO Gift Observation Selection Display
 * Below `sm` it stacks under the list and scrolls horizontally; at and above `sm` it is a
 * fixed-height column beside the list. Click on gift to remove from selection.
 */
export function EGOGiftObservationSelection({
  selectedGiftIds,
  onGiftRemove,
}: EGOGiftObservationSelectionProps) {
  const spec = useEGOGiftListSpec()
  const i18n = useEGOGiftListI18n()

  // Merge spec and i18n into EGOGiftEntity array
  const gifts: EGOGiftEntity[] = Object.entries(spec).map(([id, entry]) =>
    toEGOGiftEntity(id, entry, i18n[id] || id),
  )

  const mobileScale = CARD_MOBILE_SCALE
  const isSm = useIsBreakpoint('min', SM_BREAKPOINT_PX)
  const { heightPx: slotHeightPx } = useSlotSizePx(EGO_GIFT_GEOMETRY.size, mobileScale)

  const height = isSm ? observationListHeightPx(slotHeightPx) : undefined

  return (
    <div
      className="bg-muted border border-border rounded-md p-4 overflow-x-auto sm:overflow-x-visible flex flex-row sm:flex-col gap-2 items-center justify-center"
      style={{ height }}
    >
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
