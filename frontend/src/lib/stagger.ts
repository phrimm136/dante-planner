import { STAGGER_STEP_MS } from '@/lib/constants'

export function staggerDelay(
  index: number,
  step: number = STAGGER_STEP_MS.TIGHT,
): { animationDelay: string } {
  return { animationDelay: `${String(index * step)}ms` }
}
