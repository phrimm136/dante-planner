import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { PlannerSection } from '@/components/common/PlannerSection'
import { decodeGiftSelection } from '@/lib/egoGiftEncoding'
import { EMPTY_STATE } from '@/lib/constants'
import { cn } from '@/lib/utils'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { EnhancementLevel } from '@/lib/constants'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { EGOGiftCard } from '@/components/egoGift/EGOGiftCard'
import { EGOGiftTooltipContent } from '@/components/egoGift/EGOGiftTooltipContent'
import type { SerializableFloorSelection } from '@/types/PlannerTypes'

interface ComprehensiveGiftGridTrackerProps {
  floorSelections: SerializableFloorSelection[]
  doneMarks: Record<number, Set<string>>
  hoveredThemePackId: string | null
}

interface DecodedGift {
  item: EGOGiftListItem
  enhancement: EnhancementLevel
  encodedId: string
}

/**
 * Comprehensive gift grid for tracker mode (separate PlannerSection)
 * Shows all gifts from all floors with dimming for done theme packs
 */
export function ComprehensiveGiftGridTracker({
  floorSelections,
  doneMarks,
  hoveredThemePackId,
}: ComprehensiveGiftGridTrackerProps) {
  const { t } = useTranslation(['planner', 'common'])
  const { spec, i18n } = useEGOGiftListData()

  // Collect all comprehensive gift IDs from all floors
  const allComprehensiveGiftIds = useMemo(() => {
    const allGifts = new Set<string>()
    floorSelections.forEach((selection) => {
      selection.giftIds.forEach((giftId) => allGifts.add(giftId))
    })
    return allGifts
  }, [floorSelections])

  // Get gift IDs to highlight based on hovered theme pack
  const highlightedGiftIds = useMemo(() => {
    const ids = new Set<string>()
    if (hoveredThemePackId) {
      // Find which floor has this theme pack
      floorSelections.forEach((selection) => {
        if (selection.themePackId === hoveredThemePackId) {
          // Highlight all gifts from this floor
          selection.giftIds.forEach((giftId) => ids.add(giftId))
        }
      })
    }
    return ids
  }, [hoveredThemePackId, floorSelections])

  // Get gift IDs from "done" theme packs for dimming
  const doneThemePackGiftIds = useMemo(() => {
    const ids = new Set<string>()

    // For each floor
    floorSelections.forEach((selection, floorIndex) => {
      const floorDoneMarks = doneMarks[floorIndex]
      // If this floor's theme pack is marked as done
      if (selection.themePackId && floorDoneMarks?.has(selection.themePackId)) {
        // Add all gifts from this floor to dim list
        selection.giftIds.forEach((giftId) => ids.add(giftId))
      }
    })

    return ids
  }, [floorSelections, doneMarks])

  // Decode selected IDs and convert to gift items with enhancement
  const selectedGifts = useMemo(() => {
    const gifts: DecodedGift[] = []
    for (const encodedId of allComprehensiveGiftIds) {
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
          },
          enhancement,
          encodedId,
        })
      }
    }

    // Sort: highlighted gifts first, then by ID for stable sort
    return gifts.sort((a, b) => {
      const aHighlighted = highlightedGiftIds.has(a.encodedId)
      const bHighlighted = highlightedGiftIds.has(b.encodedId)

      if (aHighlighted && !bHighlighted) return -1
      if (!aHighlighted && bHighlighted) return 1

      // Stable sort by gift ID
      return a.item.id.localeCompare(b.item.id)
    })
  }, [allComprehensiveGiftIds, spec, i18n, highlightedGiftIds])

  const hasSelectedGifts = selectedGifts.length > 0

  return (
    <PlannerSection title={t('pages.plannerMD.comprehensiveEgoGiftList')}>
      {hasSelectedGifts ? (
        <div className="flex flex-wrap gap-2 p-2 min-h-28">
          {selectedGifts.map(({ item, enhancement, encodedId }) => {
            const isHighlighted = highlightedGiftIds.has(encodedId)
            const isDone = doneThemePackGiftIds.has(encodedId)

            return (
              <Tooltip key={encodedId}>
                <TooltipTrigger asChild>
                  <div
                    className={cn(
                      'transition-opacity duration-200',
                      isDone && 'opacity-50'
                    )}
                  >
                    <EGOGiftCard
                      gift={item}
                      enhancement={enhancement}
                      isSelected={isHighlighted}
                    />
                  </div>
                </TooltipTrigger>
                <TooltipContent
                  side="bottom"
                  className="w-auto max-w-md bg-black/85 border-neutral-800 text-foreground rounded-none p-2"
                >
                  <EGOGiftTooltipContent
                    giftId={item.id}
                    enhancement={enhancement}
                  />
                </TooltipContent>
              </Tooltip>
            )
          })}
        </div>
      ) : (
        <div
          className={cn(
            'flex items-center justify-center p-4 text-muted-foreground',
            EMPTY_STATE.MIN_HEIGHT,
            EMPTY_STATE.DASHED_BORDER
          )}
        >
          <span className="text-sm text-center">
            {t('pages.plannerMD.emptyState.noEgoGifts')}
          </span>
        </div>
      )}
    </PlannerSection>
  )
}
