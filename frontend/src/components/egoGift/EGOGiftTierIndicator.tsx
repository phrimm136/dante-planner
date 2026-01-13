import { getEGOGiftTierEXPath } from '@/lib/assetPaths'
import { getDisplayFontForLabel } from '@/lib/utils'

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
    const iconSize = 'w-7 h-7'
    return (
      <img
        src={getEGOGiftTierEXPath()}
        alt="EX Tier"
        className={`absolute top-1.5 left-1 ${iconSize} pointer-events-none`}
      />
    )
  }

  const tierText = tierTexts[parseInt(tier)-1]
  const textSize = 'text-[34px]'

  return (
    <div
      className={`absolute ${textSize} font-bold pointer-events-none top-0 left-2 -translate-y-1`}
      style={{ color: '#fcba03', fontFamily: getDisplayFontForLabel() }}
    >
      {tierText}
    </div>
  )
}
