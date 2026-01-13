import { useMemo } from 'react'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import { EGOGiftCard } from './EGOGiftCard'
import { EGOGiftTooltip } from './EGOGiftTooltip'

interface EGOGiftObservationSelectionProps {
  selectedGiftIds: string[]
  onGiftRemove: (giftId: string) => void
}

/**
 * EGO Gift Observation Selection Display
 * Displays selected gifts in vertical layout
 * Click on gift to remove from selection
 */
export function EGOGiftObservationSelection({
  selectedGiftIds,
  onGiftRemove,
}: EGOGiftObservationSelectionProps) {
  const { spec, i18n } = useEGOGiftListData()

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

  return (
    <div className="bg-muted border border-border rounded-md p-4 h-[350px] flex flex-col gap-4">
      {selectedGiftIds.map((giftId) => {
        const gift = gifts.find((g) => g.id === giftId)
        if (!gift) return null

        return (
          <EGOGiftTooltip key={giftId} giftId={giftId} className="max-w-[320px]">
            <button
              type="button"
              onClick={() => { onGiftRemove(giftId); }}
              className="cursor-pointer"
            >
              <EGOGiftCard gift={gift} isSelected={true} enableHoverHighlight />
            </button>
          </EGOGiftTooltip>
        )
      })}
    </div>
  )
}
