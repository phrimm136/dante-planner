import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { useEGOGiftListData } from '@/hooks/useEGOGiftListData'
import { EGOGiftSelectionList } from '@/components/egoGift/EGOGiftSelectionList'
import { EGOGiftSearchBar } from '@/components/egoGift/EGOGiftSearchBar'
import { EGOGiftKeywordFilter } from '@/components/egoGift/EGOGiftKeywordFilter'
import { Sorter, type SortMode } from '@/components/common/Sorter'
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
  const { t } = useTranslation()
  const { spec, i18n } = useEGOGiftListData()

  // Filter states (local to pane UI)
  const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set())
  const [searchQuery, setSearchQuery] = useState('')
  const [sortMode, setSortMode] = useState<SortMode>('tier-first')

  // Get filtered gift IDs based on theme pack
  const giftIdFilter = getFilteredGiftIds(spec, themePackId)

  // Convert to EGOGiftListItem array
  const gifts: EGOGiftListItem[] = Object.entries(spec).map(([id, specData]) => ({
    id,
    name: i18n[id] || id,
    tag: specData.tag as EGOGiftListItem['tag'],
    keyword: specData.keyword,
    attributeType: specData.attributeType,
    themePack: specData.themePack,
  }))

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
      <DialogContent className="max-w-7xl max-h-[85vh] overflow-hidden flex flex-col">
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
            gifts={gifts}
            giftIdFilter={giftIdFilter}
            selectedKeywords={selectedKeywords}
            searchQuery={searchQuery}
            sortMode={sortMode}
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
