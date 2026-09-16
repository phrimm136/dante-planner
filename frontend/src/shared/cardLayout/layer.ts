import type { CSSProperties } from 'react'

import { pctStyle, type PctRect } from './rect'

/**
 * One sprite layer of a card: the box it occupies and how its sprite meets that box.
 *
 * `fit` is `contain` where the game's node carries `preserveAspect` and `fill` where it
 * stretches the sprite; `origin` is where a contained sprite parks, which Unity offsets by
 * the node's pivot.
 */
export interface CardLayer {
  rect: PctRect
  fit: 'contain' | 'fill'
  origin: string
}

/** Absolute positioning and sprite fit for one card layer. */
export function layerStyle(layer: CardLayer): CSSProperties {
  return {
    ...pctStyle(layer.rect),
    objectFit: layer.fit,
    objectPosition: layer.origin,
  }
}
