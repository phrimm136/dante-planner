import { getEGOGiftIconPath } from '@/shared/assets'
import type { EGOGiftId } from '@/shared/gameData'

interface EGOGiftIconProps {
  giftId: EGOGiftId
  /** Sizing classes only; positioning belongs to the caller's layout */
  className?: string
}

/**
 * Bare EGO gift icon. Owns the icon policy — asset path, alt text, lazy
 * loading, and self-hide when the art is not shipped — while geometry
 * stays with the caller.
 */
export function EGOGiftIcon({ giftId, className }: EGOGiftIconProps) {
  return (
    <img
      src={getEGOGiftIconPath(giftId)}
      alt={`EGO Gift ${giftId}`}
      className={className}
      loading="lazy"
      onError={(e) => {
        e.currentTarget.style.display = 'none'
      }}
    />
  )
}
