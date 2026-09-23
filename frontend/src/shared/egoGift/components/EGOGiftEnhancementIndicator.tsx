import { getEGOGiftEnhancementIconPath } from '@/shared/assets'
import { EGO_GIFT_CARD, pct } from '../lib/cardLayout'

interface EGOGiftEnhancementIndicatorProps {
  enhancement: 0 | 1 | 2
}

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
