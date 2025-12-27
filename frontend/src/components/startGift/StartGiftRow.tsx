import { getStatusEffectIconPath } from '@/lib/assetPaths'
import { EgoGiftMiniCard } from '@/components/egoGift/EgoGiftMiniCard'
import type { EGOGiftSpec, EGOGiftNameList } from '@/types/EGOGiftTypes'

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
      className={`
        inline-flex items-center gap-4 p-3 rounded-lg transition-colors cursor-pointer
        hover:ring-2 hover:ring-[#fcba03]
        ${isRowSelected ? 'ring-2 ring-[#fcba03]' : ''}
      `}
      onClick={handleRowClick}
    >
      {/* Keyword icon */}
      <div
        className="w-16 h-16 flex items-center justify-center"
        title={keyword}
      >
        <img
          src={getStatusEffectIconPath(keyword)}
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

          const tier = spec?.tag?.find(t => t.startsWith('TIER_'))?.replace('TIER_', '') || '1'

          return (
            <div key={giftId} onClick={(e) => handleGiftCardClick(e, idStr)}>
              <EgoGiftMiniCard
                giftId={idStr}
                giftName={name}
                attributeType={spec?.attributeType || 'CRIMSON'}
                tier={tier}
                keyword={spec?.keyword || null}
                isSelected={isSelected}
                isSelectable={canSelect}
              />
            </div>
          )
        })}
      </div>
    </div>
  )
}
