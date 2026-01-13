import { useState, memo } from 'react'
import type { EnhancementLevel } from '@/lib/constants'
import { EGOGiftEnhancementSelector } from './EGOGiftEnhancementSelector'

interface EGOGiftSelectableCardProps {
  giftId: string
  enhancement: EnhancementLevel
  maxEnhancement: EnhancementLevel
  isSelected: boolean
  onEnhancementSelect: (giftId: string, enhancement: EnhancementLevel) => void
  children: React.ReactNode
}

// Inner component props (without children)
interface EGOGiftSelectableCardInnerProps {
  giftId: string
  enhancement: EnhancementLevel
  maxEnhancement: EnhancementLevel
  isSelected: boolean
  onEnhancementSelect: (giftId: string, enhancement: EnhancementLevel) => void
}

/**
 * Inner component that handles hover state and enhancement selector overlay
 * Separated from outer wrapper to prevent card re-renders on hover
 */
const EGOGiftSelectableCardInner = memo(function EGOGiftSelectableCardInner({
  giftId,
  enhancement,
  maxEnhancement,
  isSelected,
  onEnhancementSelect,
}: EGOGiftSelectableCardInnerProps) {
  const [isHovered, setIsHovered] = useState(false)

  return (
    <div
      className="absolute inset-0"
      onMouseEnter={() => { setIsHovered(true); }}
      onMouseLeave={() => { setIsHovered(false); }}
    >
      {isHovered && (
        <EGOGiftEnhancementSelector
          giftId={giftId}
          currentEnhancement={enhancement}
          maxEnhancement={maxEnhancement}
          isSelected={isSelected}
          onSelect={onEnhancementSelect}
        />
      )}
    </div>
  )
})

// Custom comparison for outer wrapper - ignore children
function arePropsEqual(prev: EGOGiftSelectableCardProps, next: EGOGiftSelectableCardProps): boolean {
  return (
    prev.giftId === next.giftId &&
    prev.enhancement === next.enhancement &&
    prev.maxEnhancement === next.maxEnhancement &&
    prev.isSelected === next.isSelected &&
    prev.onEnhancementSelect === next.onEnhancementSelect
    // children intentionally excluded
  )
}

/**
 * Gift card with enhancement selector overlay (for comprehensive list)
 * Shows enhancement selector on hover without re-rendering the card itself
 *
 * Pattern: Like TierLevelSelector
 * - Outer wrapper: memo with custom comparison excluding children
 * - Children rendered outside hover component with pointer-events-none
 * - Inner component: handles hover state + overlay separately
 * - Result: Hover only re-renders overlay, not the card
 */
export const EGOGiftSelectableCard = memo(function EGOGiftSelectableCard({
  giftId,
  enhancement,
  maxEnhancement,
  isSelected,
  onEnhancementSelect,
  children,
}: EGOGiftSelectableCardProps) {
  return (
    <div className="relative inline-block cursor-pointer">
      <div className="pointer-events-none">
        {children}
      </div>
      <EGOGiftSelectableCardInner
        giftId={giftId}
        enhancement={enhancement}
        maxEnhancement={maxEnhancement}
        isSelected={isSelected}
        onEnhancementSelect={onEnhancementSelect}
      />
    </div>
  )
}, arePropsEqual)
