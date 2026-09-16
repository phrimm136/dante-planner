import type { CardLayer, PctRect, TrackingSpec } from '@/shared/cardLayout'

/** The composed pack art, which the static pipeline bakes every baseline layer into. */
export const THEME_PACK_ART: CardLayer = {
  rect: { left: 0, top: 0, width: 100, height: 100 },
  fit: 'fill',
  origin: 'center',
}

/**
 * The theme pack card's boxes, as percentages of the card root.
 *
 * The root is the game's `[Rect]Cards`, which occupies the bottom 90% of
 * `MirrorDungeonThemeListItemUI_Button`.
 */
export const THEME_PACK_LAYOUT: Record<
  'normal' | 'extreme',
  { name: PctRect; overlay: CardLayer }
> = {
  normal: {
    name: { left: 18.273, top: 74.56666666666666, width: 65.328, height: 8.5 },
    overlay: {
      rect: { left: -10, top: 0, width: 120, height: 99.4 },
      fit: 'contain',
      origin: 'center',
    },
  },
  extreme: {
    name: { left: 18, top: 81, width: 64, height: 10 },
    overlay: {
      rect: { left: -10, top: -3, width: 120, height: 105 },
      fit: 'contain',
      origin: 'center',
    },
  },
}

/** The name's ceiling in cqw of the card root; tracking is em, as TextMeshPro applies it. */
export const THEME_PACK_NAME_MAX_CQW = 9.7

/** The tracking the name is measured and drawn with, as TextMeshPro applies it. */
export const THEME_PACK_NAME_TRACKING: TrackingSpec = {
  letterSpacingEm: -0.02,
  wordSpacingEm: -0.05,
}

export const THEME_PACK_NAME_TEXT = {
  letterSpacing: `${String(THEME_PACK_NAME_TRACKING.letterSpacingEm)}em`,
  wordSpacing: `${String(THEME_PACK_NAME_TRACKING.wordSpacingEm)}em`,
} as const

const NEWLINE = /\n/g

/** A theme pack name as the card draws it: one line, whatever breaks the source carries. */
export function themePackCardName(raw: string): string {
  return raw.replace(NEWLINE, ' ')
}

/** `_fadeDuration` on the hover sprite, in milliseconds. */
export const THEME_PACK_HOVER_FADE_MS = 200
