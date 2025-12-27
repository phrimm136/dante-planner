import {
  getEGOGiftBackgroundPath,
  getEGOGiftEnhancedBackgroundPath,
  getEGOGiftEnhanced2BackgroundPath,
} from '@/lib/assetPaths'

interface EGOGiftCardBackgroundProps {
  enhancement: 0 | 1 | 2
  size: 'full' | 'mini'
}

/**
 * Background layers for EGO gift cards
 * Handles base background and enhanced overlays based on enhancement level
 */
export function EGOGiftCardBackground({ enhancement, size }: EGOGiftCardBackgroundProps) {
  const overlaySize = size === 'full' ? 'w-24 h-24' : 'w-18 h-18'

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
          className={`absolute inset-0 ${overlaySize} m-auto object-contain`}
          loading="lazy"
        />
      )}
    </>
  )
}
