import { getKeywordIconPath } from '@/shared/assets'
import type { EGOGiftSpec, EGOGiftNameList } from '@/pages/egoGift'
import { EGOGiftCard } from '@/pages/egoGift'
import { EGOGiftTooltip } from '@/pages/egoGift'
import { ScaledCardWrapper } from '@/components/layout/ScaledCardWrapper'
import { CARD_GRID } from '@/lib/constants'
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
  onGiftClick: (keyword: string, giftId: string) => void
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
  const mobileScale = CARD_GRID.MOBILE_SCALE.STANDARD

  const handleRowClick = () => {
    onRowSelect(keyword)
  }

  const handleGiftCardClick = (giftId: string) => {
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
      <ScaledCardWrapper
        mobileScale={mobileScale}
        cardWidth={CARD_GRID.WIDTH.KEYWORD_ICON}
        cardHeight={CARD_GRID.HEIGHT.KEYWORD_ICON}
        className="flex-shrink-0"
      >
        <div className="w-16 h-16 flex items-center justify-center">
          <img
            src={getKeywordIconPath(keyword)}
            alt={keyword}
            className="w-12 h-12 object-contain"
            title={keyword}
          />
        </div>
      </ScaledCardWrapper>

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
              <ScaledCardWrapper
                mobileScale={mobileScale}
                cardWidth={CARD_GRID.WIDTH.EGO_GIFT}
                cardHeight={CARD_GRID.HEIGHT.EGO_GIFT}
              >
                <button
                  type="button"
                  onClick={() => {
                    handleGiftCardClick(giftId)
                  }}
                  disabled={!canSelect}
                  className={`group ${!canSelect ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
                >
                  <EGOGiftCard gift={gift} isSelected={isSelected} enableHoverHighlight />
                </button>
              </ScaledCardWrapper>
            </EGOGiftTooltip>
          )
        })}
      </div>
    </div>
  )
}
