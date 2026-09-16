import { getEGOGiftEnhancementIconPath } from '@/shared/assets'
import { EGO_GIFT_CARD, pct } from '../lib/cardLayout'

interface EGOGiftEnhancementIndicatorProps {
  enhancement: 0 | 1 | 2
}

/**
 * Enhancement level indicator for EGO gift cards
 * Shows +1 or +2 icon in upper-right corner
 * Not displayed for base level (0)
 */
export function EGOGiftEnhancementIndicator({ enhancement }: EGOGiftEnhancementIndicatorProps) {
  if (enhancement === 0) {
    return null
  }

  const { height, top, right } = EGO_GIFT_CARD.enhancement[enhancement]

  return (
    <img
      src={getEGOGiftEnhancementIconPath(enhancement)}
      alt={`+${enhancement}`}
      className="absolute pointer-events-none"
      style={{ height: pct(height), top: pct(top), right: pct(right) }}
    />
  )
}
