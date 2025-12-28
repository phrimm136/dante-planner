import { useState } from 'react'
import type { EGOGiftListItem } from '@/types/EGOGiftTypes'
import type { EnhancementLevel } from '@/lib/constants'
import { EGOGiftCard } from './EGOGiftCard'
import { EGOGiftEnhancementSelector } from './EGOGiftEnhancementSelector'

interface EGOGiftSelectableCardProps {
  gift: EGOGiftListItem
  enhancement: EnhancementLevel
  isSelected: boolean
  onEnhancementSelect: (giftId: string, enhancement: EnhancementLevel) => void
}

/**
 * Gift card with enhancement selector overlay (for comprehensive list)
 * Shows enhancement selector on hover, tooltip on enhancement button hover
 */
export function EGOGiftSelectableCard({
  gift,
  enhancement,
  isSelected,
  onEnhancementSelect,
}: EGOGiftSelectableCardProps) {
  const [isHovered, setIsHovered] = useState(false)

  return (
    <div
      className="relative inline-block cursor-pointer"
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      <EGOGiftCard
        gift={gift}
        enhancement={enhancement}
        isSelected={isSelected}
      />
      {isHovered && (
        <EGOGiftEnhancementSelector
          giftId={gift.id}
          currentEnhancement={enhancement}
          isSelected={isSelected}
          onSelect={onEnhancementSelect}
        />
      )}
    </div>
  )
}
