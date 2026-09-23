import type { CSSProperties } from 'react'

import { getEGOGiftIconPath } from '@/shared/assets'
import type { EGOGiftId } from '@/shared/gameData'

interface EGOGiftIconProps {
  giftId: EGOGiftId
  style?: CSSProperties
}

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
