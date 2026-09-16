import type { EGOGiftSpec, EGOGiftNameList } from '@/pages/egoGift'
import { EGOGiftCard } from '@/pages/egoGift'
import { EGOGiftTooltip } from '@/pages/egoGift'
import { CardSlot } from '@/shared/cardLayout'
import { CARD_MOBILE_SCALE } from '@/lib/constants'
import { KEYWORD_ICON_GEOMETRY } from '../../lib/cardLayout'
import { EGO_GIFT_GEOMETRY } from '@/pages/egoGift'
import { StartGiftKeywordIcon } from './StartGiftKeywordIcon'
import { toGiftListItem, toUnknownGiftListItem } from '@/pages/egoGift'
import type { EGOGiftId } from '@/shared/gameData'

interface StartGiftRowProps {
  keyword: string
  giftIds: EGOGiftId[]
  giftSpecMap: Record<string, EGOGiftSpec>
  giftNameMap: EGOGiftNameList
  isRowSelected: boolean
  selectedGiftIds: Set<string>
  maxSelectable: number
  onRowSelect: (keyword: string) => void
  onGiftClick: (keyword: string, giftId: EGOGiftId) => void
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
  onGiftClick,
}: StartGiftRowProps) {
  // Calculate scaled dimensions
  const mobileScale = CARD_MOBILE_SCALE

  const handleRowClick = () => {
    onRowSelect(keyword)
  }

  const handleGiftCardClick = (giftId: EGOGiftId) => {
    onGiftClick(keyword, giftId)
  }

  return (
    <div
      className="selectable relative inline-flex items-center gap-4 p-3 rounded-lg cursor-pointer"
      data-selected={isRowSelected}
    >
      <button
        type="button"
        className="absolute inset-0 rounded-lg"
        aria-label={keyword}
        aria-pressed={isRowSelected}
        onClick={handleRowClick}
      />

      {/* Keyword icon */}
      <CardSlot
        size={KEYWORD_ICON_GEOMETRY.size}
        mobileScale={mobileScale}
        className="flex-shrink-0"
      >
        <StartGiftKeywordIcon keyword={keyword} title={keyword} />
      </CardSlot>

      {/* Gift cards - horizontal layout */}
      <div className="relative z-10 flex items-start gap-2 lg:gap-4">
        {giftIds.map((giftId) => {
          const spec = giftSpecMap[giftId]
          const name = giftNameMap[giftId] || `Gift ${giftId}`
          const isSelected = selectedGiftIds.has(giftId)
          const canSelect = isRowSelected && (isSelected || selectedGiftIds.size < maxSelectable)

          // Build gift object for EGOGiftCard
          const gift = spec
            ? toGiftListItem(giftId, spec, name)
            : toUnknownGiftListItem(giftId, name)

          return (
            <EGOGiftTooltip key={giftId} giftId={giftId}>
              <CardSlot size={EGO_GIFT_GEOMETRY.size} mobileScale={mobileScale}>
                <button
                  type="button"
                  onClick={() => {
                    handleGiftCardClick(giftId)
                  }}
                  disabled={!canSelect}
                  className={`group block w-full ${!canSelect ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
                >
                  <EGOGiftCard gift={gift} isSelected={isSelected} enableHoverHighlight />
                </button>
              </CardSlot>
            </EGOGiftTooltip>
          )
        })}
      </div>
    </div>
  )
}
