import { useTranslation } from 'react-i18next'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { EGOGiftCard } from '@/components/egoGift/EGOGiftCard'
import { EGOGiftTooltipContent } from '@/components/egoGift/EGOGiftTooltipContent'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { decodeGiftSelection } from '@/lib/egoGiftEncoding'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { EnhancementLevel } from '@/lib/constants'
import { cn } from '@/lib/utils'

interface FloorGiftViewerProps {
  selectedGiftIds: Set<string> // Encoded IDs (enhancement + giftId)
  onClick: () => void
  className?: string
}

interface DecodedGift {
  item: EGOGiftListItem
  enhancement: EnhancementLevel
}

/**
 * Displays only the selected EGO gifts for a floor with their enhancement levels
 * Shows placeholder when empty, clicking opens selector pane
 */
export function FloorGiftViewer({
  selectedGiftIds,
  onClick,
  className,
}: FloorGiftViewerProps) {
  const { t } = useTranslation()
  const { spec, i18n } = useEGOGiftListData()

  // Decode selected IDs and convert to gift items with enhancement
  const selectedGifts: DecodedGift[] = []
  for (const encodedId of selectedGiftIds) {
    const { giftId, enhancement } = decodeGiftSelection(encodedId)
    const giftSpec = spec[giftId]
    if (giftSpec) {
      selectedGifts.push({
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

  // Empty state
  if (selectedGifts.length === 0) {
    return (
      <button
        type="button"
        onClick={onClick}
        aria-label={t('pages.plannerMD.selectFloorEgoGifts')}
        className={cn(
          'selectable w-full min-h-24 p-4 rounded-lg border-2 border-dashed border-muted-foreground/50',
          'flex items-center justify-center cursor-pointer',
          className
        )}
      >
        <span className="text-sm text-muted-foreground text-center">
          {t('pages.plannerMD.selectFloorEgoGifts')}
        </span>
      </button>
    )
  }

  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={t('pages.plannerMD.selectedEgoGifts')}
      className={cn(
        'selectable w-full flex flex-wrap gap-2 p-2 rounded-lg cursor-pointer text-left',
        className
      )}
    >
      {selectedGifts.map(({ item, enhancement }) => (
        <Tooltip key={item.id}>
          <TooltipTrigger asChild>
            <div>
              <EGOGiftCard gift={item} enhancement={enhancement} isSelected />
            </div>
          </TooltipTrigger>
          <TooltipContent
            side="bottom"
            className="max-w-xs bg-gray-900 border border-gray-700 p-3"
          >
            <EGOGiftTooltipContent giftId={item.id} enhancement={enhancement} />
          </TooltipContent>
        </Tooltip>
      ))}
    </button>
  )
}
