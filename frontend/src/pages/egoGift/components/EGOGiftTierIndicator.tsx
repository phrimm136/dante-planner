import { useTranslation } from 'react-i18next'

import { getEGOGiftTierEXPath } from '@/shared/assets'
import { getDisplayFontForLabel } from '@/lib/utils'
import { ACCENT_COLORS } from '@/lib/constants'
import { EGO_GIFT_CARD, cqw, pct } from '../lib/cardLayout'

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
  const { t } = useTranslation()
  const isEXTier = tier === 'EX'

  if (isEXTier) {
    const { size, top, left } = EGO_GIFT_CARD.tierIcon
    return (
      <img
        src={getEGOGiftTierEXPath()}
        alt={t('a11y.exTier')}
        className="absolute pointer-events-none"
        style={{ width: pct(size), height: pct(size), top: pct(top), left: pct(left) }}
      />
    )
  }

  const tierText = tierTexts[parseInt(tier) - 1]
  const { fontSize, top, left, liftY } = EGO_GIFT_CARD.tierText

  return (
    <div
      className="absolute pointer-events-none"
      style={{
        top: pct(top),
        left: pct(left),
        fontSize: cqw(fontSize),
        transform: `translateY(-${cqw(liftY)})`,
        color: ACCENT_COLORS.TIER,
        fontFamily: getDisplayFontForLabel(),
      }}
    >
      {tierText}
    </div>
  )
}
