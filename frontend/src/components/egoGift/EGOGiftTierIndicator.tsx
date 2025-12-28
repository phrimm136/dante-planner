import { getEGOGiftTierEXPath } from '@/lib/assetPaths'

interface EGOGiftTierIndicatorProps {
  tier: string
}

const tierTexts = ['I', 'II', 'III', 'IV', 'V']

/**
 * Tier indicator for EGO gift cards
 * Shows EX icon or letter representation (I, II, III, IV, V)
 * Positioned in upper-left corner
 */
export function EGOGiftTierIndicator({ tier }: EGOGiftTierIndicatorProps) {
  const isEXTier = tier === 'EX'

  if (isEXTier) {
    const iconSize = 'w-8 h-8'
    return (
      <img
        src={getEGOGiftTierEXPath()}
        alt="EX Tier"
        className={`absolute top-0 left-0 ${iconSize} pointer-events-none`}
      />
    )
  }

  const tierText = tierTexts[parseInt(tier)-1]
  const textSize = 'text-2xl top-1 left-1'

  return (
    <div
      className={`absolute ${textSize} font-bold pointer-events-none`}
      style={{ color: '#fcba03' }}
    >
      {tierText}
    </div>
  )
}
