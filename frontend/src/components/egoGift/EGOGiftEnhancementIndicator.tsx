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

  const iconPosition = enhancement === 2 ? 'h-[26px] top-1.5 right-1.5' : 'h-[22px] top-2 right-2'

  return (
    <img
      src={getEGOGiftEnhancementIconPath(enhancement)}
      alt={`+${enhancement}`}
      className={`absolute ${iconPosition} pointer-events-none`}
    />
  )
}
