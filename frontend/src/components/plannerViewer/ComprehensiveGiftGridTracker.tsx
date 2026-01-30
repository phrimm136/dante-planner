import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ScrollArea } from '@/components/ui/scroll-area'
import { decodeGiftSelection } from '@/lib/egoGiftEncoding'
import { EMPTY_STATE, CARD_GRID } from '@/lib/constants'
import { cn } from '@/lib/utils'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { EnhancementLevel } from '@/lib/constants'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { useSearchMappingsDeferred } from '@/hooks/useSearchMappings'
import { ScaledCardWrapper } from '@/components/common/ScaledCardWrapper'
import { EGOGiftCard } from '@/components/egoGift/EGOGiftCard'
import { EGOGiftTooltip } from '@/components/egoGift/EGOGiftTooltip'
import { EGOGiftKeywordFilter } from '@/components/egoGift/EGOGiftKeywordFilter'
import { SearchBar } from '@/components/common/SearchBar'
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
 * Comprehensive gift grid for tracker mode
 * Shows all gifts from all floors with dimming for done theme packs
 * Includes keyword filter and search bar for easy navigation
 */
export function ComprehensiveGiftGridTracker({
  floorSelections,
  doneMarks,
  hoveredThemePackId,
}: ComprehensiveGiftGridTrackerProps) {
  const { t } = useTranslation(['planner', 'common'])
  const { spec, i18n } = useEGOGiftListData()
  const { keywordToValue } = useSearchMappingsDeferred()

  // Filter states
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState('')

  const mobileScale = CARD_GRID.MOBILE_SCALE.STANDARD

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
    const highlighted: DecodedGift[] = []
    const nonHighlighted: DecodedGift[] = []

    for (const encodedId of allComprehensiveGiftIds) {
      const { giftId, enhancement } = decodeGiftSelection(encodedId)
      const giftSpec = spec[giftId]
      if (!giftSpec) continue

      const giftName = i18n[giftId] || giftId
      const giftKeyword = giftSpec.keyword ?? 'None'

      // Apply keyword filter
      if (selectedKeywords.size > 0 && !selectedKeywords.has(giftKeyword)) {
        continue
      }

      // Apply search filter
      if (searchQuery) {
        const lowerQuery = searchQuery.toLowerCase()
        const nameMatch = giftName.toLowerCase().includes(lowerQuery)
        const keywordMatch = Array.from(keywordToValue.entries()).some(([naturalLang, pascalValues]) => {
          if (naturalLang.includes(lowerQuery)) {
            return pascalValues.includes(giftKeyword)
          }
          return false
        })
        if (!nameMatch && !keywordMatch) continue
      }

      const gift: DecodedGift = {
        item: {
          id: giftId,
          name: giftName,
          tag: giftSpec.tag as EGOGiftListItem['tag'],
          keyword: giftSpec.keyword,
          attributeType: giftSpec.attributeType,
          themePack: giftSpec.themePack,
          maxEnhancement: giftSpec.maxEnhancement,
        },
        enhancement,
        encodedId,
      }

      // Separate into highlighted and non-highlighted arrays
      if (highlightedGiftIds.has(encodedId)) {
        highlighted.push(gift)
      } else {
        nonHighlighted.push(gift)
      }
    }

    // Sort each array separately by gift ID (stable sort)
    highlighted.sort((a, b) => a.item.id.localeCompare(b.item.id))
    nonHighlighted.sort((a, b) => a.item.id.localeCompare(b.item.id))

    // Concatenate: highlighted first, then non-highlighted
    return [...highlighted, ...nonHighlighted]
  }, [allComprehensiveGiftIds, spec, i18n, highlightedGiftIds, selectedKeywords, searchQuery, keywordToValue])

  const hasAnyGifts = allComprehensiveGiftIds.size > 0
  const hasFilteredGifts = selectedGifts.length > 0
  const hasActiveFilters = selectedKeywords.size > 0 || searchQuery.length > 0

  // No gifts in planner at all
  if (!hasAnyGifts) {
    return (
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
    )
  }

  return (
    <div className="space-y-2">
      {/* Filter bar - stacked layout with filter fully expanded */}
      <div className="space-y-2">
        <EGOGiftKeywordFilter
          selectedKeywords={selectedKeywords}
          onSelectionChange={setSelectedKeywords}
        />
        <SearchBar
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          placeholder={t('deckBuilder.egoGiftSearchPlaceholder')}
        />
      </div>

      {/* Gift grid or no results message */}
      {hasFilteredGifts ? (
        <ScrollArea className="md:h-[178px] lg:h-[353px]">
          <div className="flex flex-wrap gap-2 p-2 min-h-24">
            {selectedGifts.map(({ item, enhancement, encodedId }) => {
              const isHighlighted = highlightedGiftIds.has(encodedId)
              const isDone = doneThemePackGiftIds.has(encodedId)

              return (
                <ScaledCardWrapper
                  key={encodedId}
                  mobileScale={mobileScale}
                  cardWidth={CARD_GRID.WIDTH.EGO_GIFT}
                  cardHeight={CARD_GRID.HEIGHT.EGO_GIFT}
                >
                  <EGOGiftTooltip giftId={item.id} enhancement={enhancement}>
                    <div
                      className={cn(
                        isDone && 'brightness-50'
                      )}
                    >
                      <EGOGiftCard
                        gift={item}
                        enhancement={enhancement}
                        isSelected={isHighlighted}
                      />
                    </div>
                  </EGOGiftTooltip>
                </ScaledCardWrapper>
              )
            })}
          </div>
        </ScrollArea>
      ) : (
        <div
          className={cn(
            'flex items-center justify-center p-4 text-muted-foreground',
            EMPTY_STATE.MIN_HEIGHT,
            EMPTY_STATE.DASHED_BORDER
          )}
        >
          <span className="text-sm text-center">
            {hasActiveFilters
              ? t('pages.plannerMD.emptyState.noFilterResults')
              : t('pages.plannerMD.emptyState.noEgoGifts')}
          </span>
        </div>
      )}
    </div>
  )
}
