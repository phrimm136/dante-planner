import { getKeywordIconPath } from '@/lib/assetPaths'
import type { EGOGiftSpec, EGOGiftNameList } from '@/types/EGOGiftTypes'
import { EGOGiftCard } from '@/components/egoGift/EGOGiftCard'
import { EGOGiftTooltip } from '@/components/egoGift/EGOGiftTooltip'

interface StartGiftRowProps {
  keyword: string
  giftIds: number[]
  giftSpecMap: Record<string, EGOGiftSpec>
  giftNameMap: EGOGiftNameList
  isRowSelected: boolean
  selectedGiftIds: Set<string>
  maxSelectable: number
  onRowSelect: (keyword: string) => void
  onGiftSelect: (giftId: string) => void
}

/**
 * Horizontal row showing keyword icon and 3 EGO gifts for start gift selection
 * Layout: keyword icon (left) | 3 gift cards (right, horizontal)
 */
export function StartGiftRow({
  keyword,
  giftIds,
  giftSpecMap,
  giftNameMap,
  isRowSelected,
  selectedGiftIds,
  maxSelectable,
  onRowSelect,
  onGiftSelect,
}: StartGiftRowProps) {
  const handleRowClick = () => {
    onRowSelect(keyword)
  }

  const handleGiftSelect = (giftId: string) => {
    // Toggle selection: if already selected, deselect; otherwise select if under limit
    if (selectedGiftIds.has(giftId)) {
      onGiftSelect(giftId)
    } else if (selectedGiftIds.size < maxSelectable) {
      onGiftSelect(giftId)
    }
  }

  const handleGiftCardClick = (e: React.MouseEvent, giftId: string) => {
    e.stopPropagation() // Prevent row click when clicking gift
    // Select row first if not selected
    if (!isRowSelected) {
      onRowSelect(keyword)
    }
    handleGiftSelect(giftId)
  }

  return (
    <div
      className="selectable inline-flex items-center gap-4 p-3 rounded-lg cursor-pointer"
      data-selected={isRowSelected}
      onClick={handleRowClick}
    >
      {/* Keyword icon */}
      <div
        className="w-16 h-16 flex items-center justify-center"
        title={keyword}
      >
        <img
          src={getKeywordIconPath(keyword)}
          alt={keyword}
          className="w-12 h-12 object-contain"
        />
      </div>

      {/* Gift cards - horizontal layout */}
      <div className="flex items-start gap-4">
        {giftIds.map((giftId) => {
          const idStr = String(giftId)
          const spec = giftSpecMap[idStr]
          const name = giftNameMap[idStr] || `Gift ${giftId}`
          const isSelected = selectedGiftIds.has(idStr)
          const canSelect = isRowSelected && (isSelected || selectedGiftIds.size < maxSelectable)

          // Build gift object for EGOGiftCard
          const gift = {
            id: idStr,
            name,
            tag: spec?.tag || ['TIER_1'],
            keyword: spec?.keyword || null,
            attributeType: spec?.attributeType || 'CRIMSON',
            themePack: spec?.themePack || [],
          }

          return (
            <EGOGiftTooltip key={giftId} giftId={idStr}>
              <button
                type="button"
                onClick={(e) => { handleGiftCardClick(e, idStr); }}
                disabled={!canSelect}
                className={!canSelect ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}
              >
                <EGOGiftCard gift={gift} isSelected={isSelected} enableHoverHighlight />
              </button>
            </EGOGiftTooltip>
          )
        })}
      </div>
    </div>
  )
}
