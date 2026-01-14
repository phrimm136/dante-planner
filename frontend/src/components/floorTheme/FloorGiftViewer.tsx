import { useMemo, memo } from 'react'
import { useTranslation } from 'react-i18next'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { EGOGiftCard } from '@/components/egoGift/EGOGiftCard'
import { EGOGiftTooltip } from '@/components/egoGift/EGOGiftTooltip'
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

// Custom comparison - compare Set by size and elements
function areFloorGiftViewerPropsEqual(
  prev: FloorGiftViewerProps,
  next: FloorGiftViewerProps
): boolean {
  if (prev.readOnly !== next.readOnly || prev.className !== next.className) {
    return false
  }

  // Compare selectedGiftIds Set
  if (prev.selectedGiftIds !== next.selectedGiftIds) {
    if (prev.selectedGiftIds.size !== next.selectedGiftIds.size) return false
    for (const id of prev.selectedGiftIds) {
      if (!next.selectedGiftIds.has(id)) return false
    }
  }

  // onClick intentionally skipped - should be stable from parent
  return true
}

/**
 * Displays only the selected EGO gifts for a floor with their enhancement levels
 * Shows placeholder when empty, clicking opens selector pane
 * ReadOnly mode prevents interaction
 */
export const FloorGiftViewer = memo(function FloorGiftViewer({
  selectedGiftIds,
  onClick,
  readOnly = false,
  className,
}: FloorGiftViewerProps) {
  const { t } = useTranslation(['planner', 'common'])
  const { spec, i18n } = useEGOGiftListData()

  // Memoize decoded gifts to prevent unnecessary re-renders
  const selectedGifts = useMemo(() => {
    const gifts: DecodedGift[] = []
    for (const encodedId of selectedGiftIds) {
      const { giftId, enhancement } = decodeGiftSelection(encodedId)
      const giftSpec = spec[giftId]
      if (giftSpec) {
        gifts.push({
          item: {
            id: giftId,
            name: i18n[giftId] || giftId,
            tag: giftSpec.tag as EGOGiftListItem['tag'],
            keyword: giftSpec.keyword,
            attributeType: giftSpec.attributeType,
            themePack: giftSpec.themePack,
            maxEnhancement: giftSpec.maxEnhancement,
          },
          enhancement,
        })
      }
    }
    return gifts
  }, [selectedGiftIds, spec, i18n])

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
        <EGOGiftTooltip key={item.id} giftId={item.id} enhancement={enhancement}>
          <div>
            <EGOGiftCard gift={item} enhancement={enhancement} />
          </div>
        </EGOGiftTooltip>
      ))}
    </button>
  )
}, areFloorGiftViewerPropsEqual)
