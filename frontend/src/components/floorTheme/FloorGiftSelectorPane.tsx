import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { useSearchMappings } from '@/hooks/useSearchMappings'
import { EGOGiftSelectionList } from '@/components/egoGift/EGOGiftSelectionList'
import { EGOGiftSearchBar } from '@/components/egoGift/EGOGiftSearchBar'
import { EGOGiftKeywordFilter } from '@/components/egoGift/EGOGiftKeywordFilter'
import { Sorter, type SortMode } from '@/components/common/Sorter'
import { sortEGOGifts } from '@/lib/egoGiftSort'
import { encodeGiftSelection, getBaseGiftId } from '@/lib/egoGiftEncoding'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { EnhancementLevel } from '@/lib/constants'

interface FloorGiftSelectorPaneProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  floorNumber: number
  themePackId: string
  selectedGiftIds: Set<string> // Encoded IDs from parent state
  setSelectedGiftIds: (giftIds: Set<string>) => void // Parent state setter
}

/**
 * Gets gift IDs that are allowed for the floor based on theme pack
 * Returns gifts where themePack contains the selected ID OR is empty array
 */
function getFilteredGiftIds(
  spec: Record<string, { themePack: string[] }>,
  themePackId: string
): number[] {
  const allowedIds: number[] = []
  for (const [id, giftSpec] of Object.entries(spec)) {
    const themePack = giftSpec.themePack
    // Show gift if themePack is empty array OR contains the selected theme pack ID
    if (!themePack || themePack.length === 0 || themePack.includes(themePackId)) {
      allowedIds.push(Number(id))
    }
  }
  return allowedIds
}

/**
 * Dialog for selecting EGO gifts for a floor, filtered by theme pack
 * State is managed by parent (PlannerMDNewPage); this pane calls setSelectedGiftIds
 */
export function FloorGiftSelectorPane({
  open,
  onOpenChange,
  floorNumber,
  themePackId,
  selectedGiftIds,
  setSelectedGiftIds,
}: FloorGiftSelectorPaneProps) {
  const { t } = useTranslation(['planner', 'common'])
  const { spec, i18n } = useEGOGiftListData()
  const { keywordToValue } = useSearchMappings()

  // Filter states (local to pane UI)
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState('')
  const [sortMode, setSortMode] = useState<SortMode>('tier-first')

  // Get filtered gift IDs based on theme pack
  const giftIdFilter = useMemo(
    () => getFilteredGiftIds(spec, themePackId),
    [spec, themePackId]
  )

  // Convert to EGOGiftListItem array
  const gifts = useMemo<EGOGiftListItem[]>(() => {
    return Object.entries(spec).map(([id, specData]) => ({
      id,
      name: i18n[id] || id,
      tag: specData.tag as EGOGiftListItem['tag'],
      keyword: specData.keyword,
      attributeType: specData.attributeType,
      themePack: specData.themePack,
    }))
  }, [spec, i18n])

  // Sort gifts (apply giftIdFilter + sort)
  const sortedGifts = useMemo(() => {
    const idSet = new Set(giftIdFilter.map(String))
    const filtered = gifts.filter((gift) => idSet.has(gift.id))
    return sortEGOGifts(filtered, sortMode)
  }, [gifts, giftIdFilter, sortMode])

  // Compute visible IDs (CSS filtering)
  const visibleIds = useMemo(() => {
    const ids = new Set<string>()
    for (const gift of sortedGifts) {
      // Keyword filter
      if (selectedKeywords.size > 0) {
        if (!gift.keyword || !selectedKeywords.has(gift.keyword)) continue
      }

      // Search filter - match name OR keyword
      if (searchQuery) {
        const lowerQuery = searchQuery.toLowerCase()
        const nameMatch = gift.name?.toLowerCase().includes(lowerQuery)
        const keywordMatch = Array.from(keywordToValue.entries()).some(([naturalLang, pascalValues]) => {
          if (naturalLang.includes(lowerQuery)) {
            return gift.keyword && pascalValues.includes(gift.keyword)
          }
          return false
        })
        if (!nameMatch && !keywordMatch) continue
      }

      ids.add(gift.id)
    }
    return ids
  }, [sortedGifts, selectedKeywords, searchQuery, keywordToValue])

  const handleEnhancementSelect = (giftId: string, enhancement: EnhancementLevel) => {
    const newSelection = new Set(selectedGiftIds)

    // Remove any existing selection for this gift (any enhancement level)
    for (const encodedId of selectedGiftIds) {
      if (getBaseGiftId(encodedId) === giftId) {
        newSelection.delete(encodedId)
        break
      }
    }

    // Add new selection with enhancement (toggle off if clicking same)
    const encodedId = encodeGiftSelection(enhancement, giftId)
    if (!selectedGiftIds.has(encodedId)) {
      newSelection.add(encodedId)
    }

    setSelectedGiftIds(newSelection)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[95vw] lg:max-w-[1440px] max-h-[90vh] overflow-hidden flex flex-col duration-100">
        <DialogHeader>
          <DialogTitle>
            {t('pages.plannerMD.selectEgoGiftsForFloor', { floor: floorNumber })}
          </DialogTitle>
        </DialogHeader>

        {/* Filter bar */}
        <div className="flex gap-4 justify-between items-center py-2">
          <div className="flex gap-4 items-center">
            <EGOGiftKeywordFilter
              selectedKeywords={selectedKeywords}
              onSelectionChange={setSelectedKeywords}
            />
            <Sorter sortMode={sortMode} onSortModeChange={setSortMode} />
          </div>
          <EGOGiftSearchBar searchQuery={searchQuery} onSearchChange={setSearchQuery} />
        </div>

        {/* Gift selection list */}
        <div className="flex-1 overflow-hidden">
          <EGOGiftSelectionList
            gifts={sortedGifts}
            visibleIds={visibleIds}
            selectedGiftIds={selectedGiftIds}
            maxSelectable={Infinity}
            enableEnhancementSelection
            onEnhancementSelect={handleEnhancementSelect}
          />
        </div>
      </DialogContent>
    </Dialog>
  )
}
