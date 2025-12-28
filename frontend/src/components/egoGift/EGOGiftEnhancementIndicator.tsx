import { getEGOGiftEnhancementIconPath } from '@/lib/assetPaths'

interface EGOGiftEnhancementIndicatorProps {
  enhancement: 0 | 1 | 2
}

/**
 * Enhancement level indicator for EGO gift cards
 * Shows +1 or +2 icon in upper-right corner
 * Not displayed for base level (0)
 */
export function EGOGiftEnhancementIndicator({
  enhancement,
}: EGOGiftEnhancementIndicatorProps) {
  if (enhancement === 0) {
    return null
  }

  const iconSize = enhancement === 2 ? 'w-10 h-5' : 'w-5 h-5'

  return (
    <img
      src={getEGOGiftEnhancementIconPath(enhancement)}
      alt={`+${enhancement}`}
      className={`absolute top-0 right-0 ${iconSize} pointer-events-none`}
    />
  )
}
