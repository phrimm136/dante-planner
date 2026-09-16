import {
  getEGOGiftBackgroundPath,
  getEGOGiftEnhancedBackgroundPath,
  getEGOGiftEnhanced2BackgroundPath,
} from '@/shared/assets'
import { EGO_GIFT_CARD, pct } from '../lib/cardLayout'

interface EGOGiftCardBackgroundProps {
  enhancement: 0 | 1 | 2
}

/**
 * Background layers for EGO gift cards
 * Handles base background and enhanced overlays based on enhancement level
 */
export function EGOGiftCardBackground({ enhancement }: EGOGiftCardBackgroundProps) {
  return (
    <>
      {/* Base background - bg.webp for enhancement 0 and 1, bgEnhanced2.webp for enhancement 2 */}
      <img
        src={enhancement === 2 ? getEGOGiftEnhanced2BackgroundPath() : getEGOGiftBackgroundPath()}
        alt=""
        className="absolute inset-0 w-full h-full object-contain"
        loading="lazy"
      />

      {/* Enhanced overlay - bgEnhanced.webp for enhancement 1 and 2 */}
      {(enhancement === 1 || enhancement === 2) && (
        <img
          src={getEGOGiftEnhancedBackgroundPath()}
          alt=""
          className="absolute inset-0 m-auto object-contain"
          style={{
            width: pct(EGO_GIFT_CARD.enhancedOverlay),
            height: pct(EGO_GIFT_CARD.enhancedOverlay),
          }}
          loading="lazy"
        />
      )}
    </>
  )
}
