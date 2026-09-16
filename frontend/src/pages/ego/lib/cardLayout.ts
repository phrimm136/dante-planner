import type { CSSProperties } from 'react'

import {
  aspectOf,
  EGO_GEOMETRY,
  lineMetrics,
  pctStyle,
  type CardLayer,
  type FitSpec,
  type FontAdvanceTable,
  type LineMetrics,
  type PctRect,
  type TrackingSpec,
} from '@/shared/cardLayout'

/**
 * The EGO card's sprite layers, as percentages of the card root.
 *
 * Transcribed from `[Script]FormationSwitchableEgoScrollViewItem` (350 x 450). Unity
 * offsets a `preserveAspect` sprite by the node's pivot, so `[Rect]CharacterIcon` (0.5, 1)
 * parks at its rect's top.
 */
export const EGO_CARD_LAYERS = {
  /** `[Image]MaskArea` — drawn as a sprite and used as the portrait's stencil. */
  portraitWindow: {
    rect: { left: 0.857, top: 0.667, width: 98.286, height: 98.667 },
    fit: 'contain',
    origin: 'center',
  },
  /** `[Image]Frame` */
  frame: {
    rect: { left: -1.429, top: -1.111, width: 102.857, height: 102.222 },
    fit: 'contain',
    origin: 'center',
  },
  /** `[Image]ClickedFrame` */
  hoverRing: {
    rect: { left: -2, top: -1.556, width: 104, height: 103.111 },
    fit: 'contain',
    origin: 'center',
  },
  /** `[Image]Ego]NameBG` */
  namePlate: {
    rect: { left: -7.143, top: 69.333, width: 114.286, height: 31.111 },
    fit: 'contain',
    origin: 'center',
  },
  /** `[Image]GacksungLevel` */
  threadspinBadge: {
    rect: { left: 77.2, top: 82.622, width: 14.286, height: 11.111 },
    fit: 'contain',
    origin: 'center',
  },
  /** `[Image]EgoGrade` */
  gradeBadge: {
    rect: { left: 10.143, top: 80.867, width: 10, height: 7.778 },
    fit: 'contain',
    origin: 'center',
  },
  /** `img_egoTypeText` */
  typeLabel: {
    rect: { left: 31.429, top: 63.778, width: 37.143, height: 22.222 },
    fit: 'contain',
    origin: 'center',
  },
  /** `[Rect]CharacterIcon` */
  iconRing: {
    rect: { left: 35.714, top: 0, width: 28.571, height: 22.222 },
    fit: 'contain',
    origin: 'center top',
  },
  /** `[Image]Icon` — stretched to its rect, so it carries no fit of its own in the game. */
  face: {
    rect: { left: 38.571, top: 2.222, width: 22.857, height: 17.778 },
    fit: 'fill',
    origin: 'center',
  },
} satisfies Record<string, CardLayer>

/**
 * `img_selected`, the SELECTED plate a picked EGO draws over its portrait.
 *
 * A child of the same root as `EGO_CARD_LAYERS`, drawn only where the card is pickable, and
 * stretched to its rect: the node carries no `preserveAspect`.
 */
export const EGO_SELECTED_TAG: CardLayer = {
  rect: { left: 10.571, top: 40.667, width: 78.857, height: 18.667 },
  fit: 'fill',
  origin: 'center',
}

/** `[Text]EgoName`, the box the name is fitted into. */
export const EGO_NAME_RECT: PctRect = { left: 24.286, top: 79.556, width: 51.429, height: 17.778 }

/** The card root's own scale in the game's scroll view. */
export const EGO_CARD_ROOT_SCALE = 1.02

/**
 * The badges' shear, in degrees of CSS `skewY`.
 *
 * `SkewedImage.skewY` carries the opposite sign: 22 on `[Image]GacksungLevel`, -22 on
 * `[Image]EgoGrade`.
 */
export const EGO_CARD_BADGE_SKEW = {
  threadspin: -22,
  grade: 22,
} as const

/**
 * Where each badge's shear pivots, as a CSS `transform-origin`.
 *
 * `SkewedImage` shears `[Image]GacksungLevel` about the rect's left edge and
 * `[Image]EgoGrade` about its centre.
 */
export const EGO_CARD_BADGE_SKEW_ORIGIN = {
  threadspin: 'left center',
  grade: 'center',
} as const

/** The hover ring's grey tint while the card is hovered. */
export const EGO_HOVER_RING_BRIGHTNESS = 0.784

/**
 * The name's band, in cqw of the card root; tracking and line spacing are em.
 *
 * `lineSpacing` is `m_lineSpacing` on `[Text]EgoName`, -20 in hundredths of an em; every
 * other vertical number comes from the faces the shipped advance table names.
 */
export const EGO_NAME_CQW = {
  maxSize: 8.571,
  minSize: 7.143,
  step: 0.0143,
  letterSpacingEm: -0.02,
  wordSpacingEm: -0.05,
  lineSpacing: -0.2,
} as const

/** The tracking the EGO name is measured and drawn with. */
export const EGO_NAME_TRACKING: TrackingSpec = {
  letterSpacingEm: EGO_NAME_CQW.letterSpacingEm,
  wordSpacingEm: EGO_NAME_CQW.wordSpacingEm,
}

/** The band and box an EGO name is fitted into, in cqw of the card root. */
export function egoNameFitSpec(table: FontAdvanceTable): FitSpec {
  return {
    max: EGO_NAME_CQW.maxSize,
    min: EGO_NAME_CQW.minSize,
    step: EGO_NAME_CQW.step,
    width: EGO_NAME_RECT.width,
    height: EGO_NAME_RECT.height / aspectOf(EGO_GEOMETRY.size),
    lineSpacingEm: EGO_NAME_CQW.lineSpacing,
    metricsOf: (line) => lineMetrics(table, line),
  }
}

/** One line's pitch, as a CSS `line-height`. */
export function egoNameLineHeight(metrics: LineMetrics): number {
  return metrics.lineHeight + EGO_NAME_CQW.lineSpacing
}

/** The stencilled box the EGO portrait is drawn inside, masked by the window sprite. */
export function egoPortraitWindowStyle(maskUrl: string): CSSProperties {
  return {
    ...pctStyle(EGO_CARD_LAYERS.portraitWindow.rect),
    maskImage: `url(${maskUrl})`,
    WebkitMaskImage: `url(${maskUrl})`,
    maskSize: 'contain',
    WebkitMaskSize: 'contain',
    maskPosition: 'center',
    WebkitMaskPosition: 'center',
    maskRepeat: 'no-repeat',
    WebkitMaskRepeat: 'no-repeat',
  }
}
