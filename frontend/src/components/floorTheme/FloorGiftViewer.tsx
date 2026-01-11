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
  readOnly?: boolean
  className?: string
}

interface DecodedGift {
  item: EGOGiftListItem
  enhancement: EnhancementLevel
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

  // Handle click - prevent when readOnly
  const handleClick = () => {
    if (!readOnly) {
      onClick()
    }
  }

  // Empty state
  if (selectedGifts.length === 0) {
    return (
      <button
        type="button"
        onClick={handleClick}
        disabled={readOnly}
        aria-label={t('pages.plannerMD.selectFloorEgoGifts')}
        className={cn(
          'w-full h-104 p-4 rounded-lg border-2 border-dashed border-muted-foreground/50',
          'flex items-center justify-center',
          !readOnly && 'selectable',
          className
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
      onClick={handleClick}
      disabled={readOnly}
      aria-label={t('pages.plannerMD.selectedEgoGifts')}
      className={cn(
        'w-full flex flex-wrap gap-2 p-2 rounded-lg text-left',
        !readOnly && 'selectable',
        className
      )}
    >
      {selectedGifts.map(({ item, enhancement }) => (
        <Tooltip key={item.id}>
          <TooltipTrigger asChild>
            <div>
              <EGOGiftCard gift={item} enhancement={enhancement} />
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
