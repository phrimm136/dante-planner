/** The cards whose name carries the game's TMP underlay. */
type UnderlayCard = 'identity' | 'ego' | 'themePack'

/** The languages the game ships a display face, and therefore an underlay row, for. */
type UnderlayLanguage = 'KR' | 'EN' | 'JP'

/** One underlay, in cqw of the card root, on CSS axes: x to the right, y downward. */
export interface Underlay {
  dx: number
  dy: number
  /** `_UnderlaySoftness`, drawn as the shadow's blur radius. */
  softness: number
  /** `_UnderlayDilate`, the distance the shadow spreads outward from the glyph. */
  dilate: number
}

/** One card's underlays: the name's row per language, and the level's where it draws one. */
interface CardUnderlays {
  name: Partial<Record<UnderlayLanguage, Underlay>>
  level?: Underlay
}

const UNDERLAY_COLOR = '#040001'

const CARD_UNDERLAYS: Record<UnderlayCard, CardUnderlays> = {
  identity: {
    name: {
      KR: { dx: 0.53295, dy: 0.53295, softness: 0, dilate: 0.18653 },
      EN: { dx: 0.51747, dy: 0.51747, softness: 0, dilate: 0 },
      JP: { dx: 0.45867, dy: 0.40471, softness: 0, dilate: 0 },
    },
    level: { dx: 0.50679, dy: 0.50679, softness: 0, dilate: 0 },
  },
  ego: {
    name: {
      KR: { dx: 0.40461, dy: 0.40461, softness: 0, dilate: 0.14161 },
      EN: { dx: 0.39286, dy: 0.39286, softness: 0, dilate: 0 },
      JP: { dx: 0.34821, dy: 0.30725, softness: 0, dilate: 0 },
    },
  },
  themePack: {
    name: {
      KR: { dx: 0.44159, dy: 0.44159, softness: 0, dilate: 0.15456 },
      EN: { dx: 0.42876, dy: 0.42876, softness: 0, dilate: 0 },
      JP: { dx: 0.38004, dy: 0.33533, softness: 0, dilate: 0 },
    },
  },
}

const UNDERLAY_LANGUAGE: Record<string, UnderlayLanguage> = {
  KR: 'KR',
  EN: 'EN',
  JP: 'JP',
  CN: 'KR',
}

const DILATE_RING: readonly (readonly [number, number])[] = [
  [1, 0],
  [-1, 0],
  [0, 1],
  [0, -1],
  [0.7071, 0.7071],
  [0.7071, -0.7071],
  [-0.7071, 0.7071],
  [-0.7071, -0.7071],
]

function layer(dx: number, dy: number, softness: number, color: string): string {
  return `${String(dx)}cqw ${String(dy)}cqw ${String(softness)}cqw ${color}`
}

export function underlayShadow(underlay: Underlay, color: string): string {
  const ringRadius = Math.max(0, underlay.dilate - underlay.softness)
  const layers = [layer(underlay.dx, underlay.dy, underlay.softness, color)]
  if (ringRadius > 0) {
    for (const [x, y] of DILATE_RING) {
      layers.push(
        layer(
          Number((underlay.dx + x * ringRadius).toFixed(5)),
          Number((underlay.dy + y * ringRadius).toFixed(5)),
          underlay.softness,
          color,
        ),
      )
    }
  }

  return layers.join(', ')
}

function cardUnderlayShadow(underlay: Underlay | undefined): string | undefined {
  return underlay === undefined ? undefined : underlayShadow(underlay, UNDERLAY_COLOR)
}

export function nameShadow(card: UnderlayCard, language: string): string | undefined {
  const row = UNDERLAY_LANGUAGE[language]
  if (row === undefined) return undefined

  return cardUnderlayShadow(CARD_UNDERLAYS[card].name[row])
}

export function levelShadow(card: UnderlayCard): string | undefined {
  return cardUnderlayShadow(CARD_UNDERLAYS[card].level)
}
