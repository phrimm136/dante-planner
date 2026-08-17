import { memo } from 'react'

import type { EnhancementLevel } from '@/shared/gameData'
import { CARD_GRID } from '@/lib/constants'
import { ScaledCardWrapper } from '@/components/layout/ScaledCardWrapper'
import type { EGOGiftListItem } from '../types/EGOGiftTypes'
import { EGOGiftCard } from './EGOGiftCard'
import { EGOGiftObservationCard } from './EGOGiftObservationCard'
import { EGOGiftSelectableCard } from './EGOGiftSelectableCard'

interface GiftCellProps {
  gift: EGOGiftListItem
  isSelected: boolean
  /** Whether the gift survives the current filters */
  isVisible: boolean
}

interface EnhancementCellProps extends GiftCellProps {
  enhancement: EnhancementLevel
  onEnhancementSelect: (giftId: string, enhancement: EnhancementLevel) => void
}

interface ObservationCellProps extends GiftCellProps {
  onSelect: (giftId: string) => void
}

/**
 * A caller may mint its gift items per render, so the comparison names every field
 * the card reads instead of leaning on the item's identity. `name` carries the
 * active language.
 */
function sameGift(prev: GiftCellProps, next: GiftCellProps): boolean {
  return (
    prev.gift.id === next.gift.id &&
    prev.gift.name === next.gift.name &&
    prev.gift.tag === next.gift.tag &&
    prev.gift.keyword === next.gift.keyword &&
    prev.gift.maxEnhancement === next.gift.maxEnhancement &&
    prev.isSelected === next.isSelected &&
    prev.isVisible === next.isVisible
  )
}

/**
 * One gift's seat in an enhancement-selection grid.
 *
 * The cell builds its own subtree rather than accepting it as `children`: the grid
 * maps over every gift, so a subtree passed in would be a fresh element on each
 * render and no comparison could bail out.
 */
export const EGOGiftEnhancementCell = memo(
  EGOGiftEnhancementCellImpl,
  (prev, next) =>
    sameGift(prev, next) &&
    prev.enhancement === next.enhancement &&
    prev.onEnhancementSelect === next.onEnhancementSelect,
)

function EGOGiftEnhancementCellImpl({
  gift,
  enhancement,
  isSelected,
  isVisible,
  onEnhancementSelect,
}: EnhancementCellProps) {
  return (
    <ScaledCardWrapper
      cardWidth={CARD_GRID.WIDTH.EGO_GIFT}
      cardHeight={CARD_GRID.HEIGHT.EGO_GIFT}
      mobileScale={CARD_GRID.MOBILE_SCALE.STANDARD}
      className={isVisible ? '' : 'hidden'}
    >
      <EGOGiftSelectableCard
        giftId={gift.id}
        enhancement={enhancement}
        maxEnhancement={gift.maxEnhancement}
        isSelected={isSelected}
        onEnhancementSelect={onEnhancementSelect}
      >
        <EGOGiftCard
          gift={gift}
          enhancement={enhancement}
          isSelected={isSelected}
          enableHoverHighlight
        />
      </EGOGiftSelectableCard>
    </ScaledCardWrapper>
  )
}

/** One gift's seat in an observation-selection grid, on the same terms. */
export const EGOGiftObservationCell = memo(
  EGOGiftObservationCellImpl,
  (prev, next) => sameGift(prev, next) && prev.onSelect === next.onSelect,
)

function EGOGiftObservationCellImpl({
  gift,
  isSelected,
  isVisible,
  onSelect,
}: ObservationCellProps) {
  return (
    <ScaledCardWrapper
      cardWidth={CARD_GRID.WIDTH.EGO_GIFT}
      cardHeight={CARD_GRID.HEIGHT.EGO_GIFT}
      mobileScale={CARD_GRID.MOBILE_SCALE.STANDARD}
      className={isVisible ? '' : 'hidden'}
    >
      <EGOGiftObservationCard giftId={gift.id} isSelected={isSelected} onSelect={onSelect}>
        <EGOGiftCard gift={gift} isSelected={isSelected} enableHoverHighlight />
      </EGOGiftObservationCard>
    </ScaledCardWrapper>
  )
}
