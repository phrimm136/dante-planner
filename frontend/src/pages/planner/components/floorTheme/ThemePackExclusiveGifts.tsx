import { EGOGiftIcon } from '@/pages/egoGift'
import { EXCLUSIVE_GIFT_ICONS } from '@/lib/constants'
import type { EGOGiftId } from '@/shared/gameData'

interface ThemePackExclusiveGiftsProps {
  giftIds: readonly EGOGiftId[]
}

/**
 * Non-interactive row of a theme pack's exclusive EGO gift icons.
 * Renders nothing for packs without exclusive gifts.
 */
export function ThemePackExclusiveGifts({ giftIds }: ThemePackExclusiveGiftsProps) {
  if (giftIds.length === 0) return null

  return (
    <div className="flex flex-wrap justify-center" style={{ gap: EXCLUSIVE_GIFT_ICONS.GAP }}>
      {giftIds.map((giftId) => (
        <EGOGiftIcon
          key={giftId}
          giftId={giftId}
          style={{ width: EXCLUSIVE_GIFT_ICONS.ICON_SIZE, height: EXCLUSIVE_GIFT_ICONS.ICON_SIZE }}
        />
      ))}
    </div>
  )
}
