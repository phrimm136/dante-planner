import { useMemo } from 'react'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import { CARD_GRID } from '@/lib/constants'
import { ScaledCardWrapper } from '@/components/common/ScaledCardWrapper'
import { EGOGiftCard } from './EGOGiftCard'
import { EGOGiftTooltip } from './EGOGiftTooltip'

interface EGOGiftObservationSelectionProps {
  selectedGiftIds: string[]
  onGiftRemove: (giftId: string) => void
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
  const { spec, i18n } = useEGOGiftListData()

  // Breakpoint detection for scaling


  // Merge spec and i18n into EGOGiftListItem array
  const gifts = useMemo<EGOGiftListItem[]>(
    () =>
      Object.entries(spec).map(([id, specData]) => ({
        id,
        name: i18n[id] || id,
        tag: specData.tag as EGOGiftListItem['tag'],
        keyword: specData.keyword,
        attributeType: specData.attributeType,
        themePack: specData.themePack,
        maxEnhancement: specData.maxEnhancement,
      })),
    [spec, i18n]
  )

  const mobileScale = CARD_GRID.MOBILE_SCALE.STANDARD

  return (
    <div className="bg-muted border border-border rounded-md p-4 overflow-x-auto sm:overflow-x-visible sm:h-[350px] flex flex-row sm:flex-col gap-2 items-center justify-center">
      {selectedGiftIds.map((giftId) => {
        const gift = gifts.find((g) => g.id === giftId)
        if (!gift) return null

        return (
          <ScaledCardWrapper
            key={giftId}
            cardWidth={CARD_GRID.WIDTH.EGO_GIFT}
            cardHeight={CARD_GRID.HEIGHT.EGO_GIFT}
            mobileScale={mobileScale}
          >
            <EGOGiftTooltip giftId={giftId} className="max-w-[320px]">
              <button
                type="button"
                onClick={() => { onGiftRemove(giftId); }}
                className="cursor-pointer"
              >
                <EGOGiftCard gift={gift} isSelected={true} enableHoverHighlight />
              </button>
            </EGOGiftTooltip>
          </ScaledCardWrapper>
        )
      })}
    </div>
  )
}
