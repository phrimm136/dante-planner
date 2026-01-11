import { useMemo } from 'react'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { EGOGiftCard } from './EGOGiftCard'
import { EGOGiftTooltipContent } from './EGOGiftTooltipContent'

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
      })),
    [spec, i18n]
  )

  return (
    <div className="bg-muted border border-border rounded-md p-4 h-[350px] flex flex-col gap-4">
      {selectedGiftIds.map((giftId) => {
        const gift = gifts.find((g) => g.id === giftId)
        if (!gift) return null

        return (
          <Tooltip key={giftId}>
            <TooltipTrigger asChild>
              <button
                type="button"
                onClick={() => { onGiftRemove(giftId); }}
                className="cursor-pointer"
              >
                <EGOGiftCard gift={gift} isSelected={true} />
              </button>
            </TooltipTrigger>
            <TooltipContent
              side="bottom"
              className="w-auto max-w-md bg-black/85 border-neutral-800 text-foreground rounded-none p-2"
            >
              <EGOGiftTooltipContent giftId={giftId} enhancement={0} />
            </TooltipContent>
          </Tooltip>
        )
      })}
    </div>
  )
}
