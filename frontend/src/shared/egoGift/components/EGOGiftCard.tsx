import type { CSSProperties } from 'react'

import { getEGOGiftOnHoverPath, getEGOGiftSelectHighlightPath } from '@/shared/assets'
import { EGO_GIFT_GEOMETRY, aspectOf } from '@/shared/cardLayout'
import { cn } from '@/lib/utils'
import { parseTier } from '../lib/egoGiftTier'
import { EGO_GIFT_CARD, pct } from '../lib/cardLayout'
import { EGOGiftIcon } from './EGOGiftIcon'
import type { EGOGiftEntity } from '../types/EGOGiftTypes'
import { EGOGiftCardBackground } from './EGOGiftCardBackground'
import { EGOGiftTierIndicator } from './EGOGiftTierIndicator'
import { EGOGiftEnhancementIndicator } from './EGOGiftEnhancementIndicator'
import { EGOGiftKeywordIndicator } from './EGOGiftKeywordIndicator'

interface EGOGiftCardProps {
  gift: EGOGiftEntity
  enhancement?: 0 | 1 | 2
  isSelected?: boolean
  enableHoverHighlight?: boolean
  className?: string
}

const ROOT_STYLE: CSSProperties = {
  containerType: 'inline-size',
  aspectRatio: aspectOf(EGO_GIFT_GEOMETRY.size),
}

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
