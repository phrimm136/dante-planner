import { EGOGiftIcon } from '@/pages/egoGift'
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
    <div className="flex flex-wrap justify-center gap-1">
      {giftIds.map((giftId) => (
        <EGOGiftIcon key={giftId} giftId={giftId} className="size-8" />
      ))}
    </div>
  )
}
