import type { CSSProperties } from 'react'

import { getEGOGiftOnHoverPath, getEGOGiftSelectHighlightPath } from '@/shared/assets'
import { EGO_GIFT_GEOMETRY, aspectOf } from '@/shared/cardLayout'
import { cn } from '@/lib/utils'
import { parseTier } from '../lib/egoGiftTier'
import { EGO_GIFT_CARD, pct } from '../lib/cardLayout'
import { EGOGiftIcon } from './EGOGiftIcon'
import type { EGOGiftListItem } from '../types/EGOGiftTypes'
import { EGOGiftCardBackground } from './EGOGiftCardBackground'
import { EGOGiftTierIndicator } from './EGOGiftTierIndicator'
import { EGOGiftEnhancementIndicator } from './EGOGiftEnhancementIndicator'
import { EGOGiftKeywordIndicator } from './EGOGiftKeywordIndicator'

interface EGOGiftCardProps {
  /** The EGO gift data to display */
  gift: EGOGiftListItem
  /** Enhancement level (0, 1, or 2) */
  enhancement?: 0 | 1 | 2
  /** Whether the card is selected */
  isSelected?: boolean
  /** Enable hover highlight overlay (for selection contexts like links and grids) */
  enableHoverHighlight?: boolean
  /** Additional CSS classes for styling flexibility */
  className?: string
}

const ROOT_STYLE: CSSProperties = {
  containerType: 'inline-size',
  aspectRatio: aspectOf(EGO_GIFT_GEOMETRY.size),
}

/**
 * View-only EGO gift card, filling the width its slot gives it.
 *
 * Carries no interaction of its own; a parent wraps it in a `Link`, a button, or a trigger.
 */
export const EGOGiftCard = function EGOGiftCard({
  gift,
  enhancement = 0,
  isSelected = false,
  enableHoverHighlight = false,
  className,
}: EGOGiftCardProps) {
  const { id } = gift

  const tier = parseTier(gift.tag) ?? ''

  return (
    <div className={cn('group relative w-full', className)} style={ROOT_STYLE}>
      <EGOGiftCardBackground enhancement={enhancement} />

      <div
        className="absolute inset-0 flex items-center justify-center"
        style={{ transform: `translateY(-${pct(EGO_GIFT_CARD.icon.liftY)})` }}
      >
        <EGOGiftIcon
          giftId={id}
          style={{ width: pct(EGO_GIFT_CARD.icon.size), height: pct(EGO_GIFT_CARD.icon.size) }}
        />
      </div>

      {enableHoverHighlight && (
        <img
          src={getEGOGiftOnHoverPath()}
          alt=""
          className="absolute inset-0 w-full h-full object-contain pointer-events-none opacity-0 group-hover:opacity-100 group-active:opacity-100"
          loading="lazy"
        />
      )}

      {isSelected && (
        <img
          src={getEGOGiftSelectHighlightPath()}
          alt="Selected"
          className="absolute inset-0 w-full h-full object-contain pointer-events-none"
          loading="lazy"
        />
      )}

      <EGOGiftTierIndicator tier={tier} />

      <EGOGiftEnhancementIndicator enhancement={enhancement} />

      <EGOGiftKeywordIndicator keyword={gift.keyword} />
    </div>
  )
}
