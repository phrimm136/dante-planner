import { STAGGER_STEP_MS } from '@/lib/constants'

/**
 * Entrance-animation delay for the item at `index`, as an inline style.
 * Spread it into a `style` object when the element carries other inline
 * properties.
 */
export function staggerDelay(
  index: number,
  step: number = STAGGER_STEP_MS.TIGHT,
): { animationDelay: string } {
  return { animationDelay: `${String(index * step)}ms` }
}
