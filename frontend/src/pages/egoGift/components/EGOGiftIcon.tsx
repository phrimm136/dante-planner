import type { CSSProperties } from 'react'

import { getEGOGiftIconPath } from '@/shared/assets'
import type { EGOGiftId } from '@/shared/gameData'

interface EGOGiftIconProps {
  giftId: EGOGiftId
  /** Sizing only; positioning belongs to the caller's layout */
  style?: CSSProperties
}

/**
 * Bare EGO gift icon. Owns the icon policy — asset path, alt text, lazy
 * loading, and self-hide when the art is not shipped — while geometry
 * stays with the caller.
 */
export function EGOGiftIcon({ giftId, style }: EGOGiftIconProps) {
  return (
    <img
      src={getEGOGiftIconPath(giftId)}
      alt={`EGO Gift ${giftId}`}
      style={style}
      loading="lazy"
      onError={(e) => {
        e.currentTarget.style.display = 'none'
      }}
    />
  )
}
