import type { ReactNode } from 'react'

import { CARD_MOBILE_SCALE } from '@/lib/constants'
import { useSlotSizePx } from './useSlotSizePx'
import { aspectOf, type CardSizePx } from './geometry'

interface CardSlotProps {
  /** The card's box. */
  size: CardSizePx
  /** The share of the desktop width the slot takes below the desktop breakpoint. */
  mobileScale?: number
  className?: string | undefined
  /** The card. */
  children: ReactNode
}

/**
 * The box a width-driven card is laid out in.
 *
 * The slot is the only element carrying a pixel size: it shrinks below the desktop
 * breakpoint and the card fills it.
 *
 * It is also the query container the card's own `cqw` lengths resolve against — an
 * element never queries itself, so a card root that declares `container-type` cannot
 * anchor its own padding, gap or border.
 *
 * @example
 * <CardSlot size={EGO_GEOMETRY.size}>
 *   <EGOCard ego={ego} />
 * </CardSlot>
 */
export function CardSlot({
  size,
  mobileScale = CARD_MOBILE_SCALE,
  className,
  children,
}: CardSlotProps) {
  const { widthPx } = useSlotSizePx(size, mobileScale)

  return (
    <div
      className={className}
      style={{
        containerType: 'inline-size',
        width: `${String(widthPx)}px`,
        aspectRatio: String(aspectOf(size)),
      }}
    >
      {children}
    </div>
  )
}
