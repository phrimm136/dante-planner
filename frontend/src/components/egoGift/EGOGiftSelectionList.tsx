import { useMemo, memo } from 'react'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { EnhancementLevel } from '@/lib/constants'
import { CARD_GRID } from '@/lib/constants'
import { buildSelectionLookup } from '@/lib/egoGiftEncoding'
import { ResponsiveCardGrid } from '@/components/common/ResponsiveCardGrid'
import { EGOGiftSelectableCard } from './EGOGiftSelectableCard'
import { EGOGiftObservationCard } from './EGOGiftObservationCard'
import { EGOGiftCard } from './EGOGiftCard'

// Memoized wrapper component to prevent re-render of children
interface GiftCardWrapperProps {
  giftId: string
  isVisible: boolean
  isSelected: boolean
  children: React.ReactNode
}

// Custom comparison for outer wrapper - ignore children but check selection state
// isSelectable intentionally excluded - it changes for all cards when max is reached
// onSelect excluded - callback identity may change but behavior is same; child handles click
function areGiftPropsEqual(prev: GiftCardWrapperProps, next: GiftCardWrapperProps): boolean {
  return (
    prev.giftId === next.giftId &&
    prev.isVisible === next.isVisible &&
    prev.isSelected === next.isSelected
    // children intentionally excluded - but isSelected tracks selection state
  )
}

const GiftCardWrapper = memo(function GiftCardWrapper({
  isVisible,
  children,
}: GiftCardWrapperProps) {
  return (
    <div className={isVisible ? '' : 'hidden'}>
      {children}
    </div>
  )
}, areGiftPropsEqual)

interface EGOGiftSelectionListProps {
  gifts: EGOGiftListItem[]
  visibleIds: Set<string>
  selectedGiftIds: Set<string>
  maxSelectable: number
  /** Callback for standard selection mode (observation, start gifts) */
  onGiftSelect?: (giftId: string) => void
  /** Enable enhancement selection mode (for comprehensive list) */
  enableEnhancementSelection?: boolean
  /** Callback for enhancement selection (required when enableEnhancementSelection is true) */
  onEnhancementSelect?: (giftId: string, enhancement: EnhancementLevel) => void
}
/**
 * Universal EGO gift selection list with CSS-based filtering (Hybrid pattern)
 * Filtering computed in parent, visibility toggled via CSS class
 * This eliminates React reconciliation on filter changes
 * Used for observation, start gifts, and comprehensive gift selection
 */
export function EGOGiftSelectionList({
  gifts,
  visibleIds,
  selectedGiftIds,
  maxSelectable,
  onGiftSelect,
  enableEnhancementSelection = false,
  onEnhancementSelect,
}: EGOGiftSelectionListProps) {
  // Build O(1) lookup map for enhancement selection mode (avoids O(n) iteration per card)
  const selectionLookup = useMemo(
    () => (enableEnhancementSelection ? buildSelectionLookup(selectedGiftIds) : null),
    [enableEnhancementSelection, selectedGiftIds]
  )

  if (visibleIds.size === 0) {
    return (
      <div className="bg-muted border border-border rounded-md p-6">
        <div className="text-center text-gray-500 py-8">
          No EGO Gifts match your current filters and search criteria
        </div>
      </div>
    )
  }

  return (
    <div className="bg-muted border border-border rounded-md p-6 h-[350px] overflow-y-auto scrollbar-hide">
      <ResponsiveCardGrid cardWidth={CARD_GRID.WIDTH.EGO_GIFT}>
        {gifts.map((gift) => {
          // Enhancement selection mode (comprehensive list)
          if (enableEnhancementSelection && onEnhancementSelect && selectionLookup) {
            const entry = selectionLookup.get(gift.id)
            const selected = entry !== undefined
            const enhancement = entry?.enhancement ?? 0
            return (
              <div
                key={gift.id}
                className={visibleIds.has(gift.id) ? '' : 'hidden'}
              >
                <EGOGiftSelectableCard
                  giftId={gift.id}
                  enhancement={enhancement}
                  maxEnhancement={gift.maxEnhancement}
                  isSelected={selected}
                  onEnhancementSelect={onEnhancementSelect}
                >
                  <EGOGiftCard
                    gift={gift}
                    enhancement={enhancement}
                    isSelected={selected}
                    enableHoverHighlight
                  />
                </EGOGiftSelectableCard>
              </div>
            )
          }

          // Standard selection mode (observation, start gifts)
          if (!onGiftSelect) return null

          const isSelected = selectedGiftIds.has(gift.id)
          const canSelect = isSelected || selectedGiftIds.size < maxSelectable
          return (
            <GiftCardWrapper
              key={gift.id}
              giftId={gift.id}
              isVisible={visibleIds.has(gift.id)}
              isSelected={isSelected}
            >
              <EGOGiftObservationCard
                gift={gift}
                isSelected={isSelected}
                isSelectable={canSelect}
                onSelect={onGiftSelect}
              />
            </GiftCardWrapper>
          )
        })}
      </ResponsiveCardGrid>
    </div>
  )
}
