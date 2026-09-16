import { LG_BREAKPOINT_PX } from '@/lib/constants'
import { useIsBreakpoint } from '@/components/hooks/use-is-breakpoint'
import type { CardSizePx } from './geometry'

/** The box a slot takes at the current breakpoint: `size` on desktop, scaled below it. */
export function useSlotSizePx(size: CardSizePx, mobileScale: number): CardSizePx {
  const isDesktop = useIsBreakpoint('min', LG_BREAKPOINT_PX)
  const scale = isDesktop ? 1 : mobileScale

  return { widthPx: scaledPx(size.widthPx, scale), heightPx: scaledPx(size.heightPx, scale) }
}

/** A box side scaled and settled to a hundredth of a pixel, so `304 * 0.8` is `243.2`. */
function scaledPx(px: number, scale: number): number {
  return Math.round(px * scale * 100) / 100
}
