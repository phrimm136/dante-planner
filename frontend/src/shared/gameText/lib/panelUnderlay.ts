import { UNIT_PANEL_UNDERLAYS_EM, UNIT_PANEL_UNDERLAY_COLORS } from '@/lib/constants'

export interface EmUnderlay {
  dx: number
  dy: number
  dilate: number
}

type NameUnderlayLanguage = keyof typeof UNIT_PANEL_UNDERLAYS_EM.NAME

const NAME_UNDERLAY_LANGUAGE: Record<string, NameUnderlayLanguage> = {
  KR: 'KR',
  EN: 'EN',
  JP: 'JP',
  CN: 'KR',
}

const RING: readonly (readonly [number, number])[] = [
  [1, 0],
  [-1, 0],
  [0, 1],
  [0, -1],
  [0.7071, 0.7071],
  [0.7071, -0.7071],
  [-0.7071, 0.7071],
  [-0.7071, -0.7071],
]

function layer(dx: number, dy: number, color: string): string {
  return `${String(Number(dx.toFixed(6)))}em ${String(Number(dy.toFixed(6)))}em 0 ${color}`
}

export function emUnderlayShadow(underlay: EmUnderlay, color: string): string {
  const layers = [layer(underlay.dx, underlay.dy, color)]
  if (underlay.dilate > 0) {
    for (const [x, y] of RING) {
      layers.push(
        layer(underlay.dx + x * underlay.dilate, underlay.dy + y * underlay.dilate, color),
      )
    }
  }
  return layers.join(', ')
}

export function panelNameShadow(language: string): string | undefined {
  const row = NAME_UNDERLAY_LANGUAGE[language]
  if (row === undefined) return undefined
  return emUnderlayShadow(UNIT_PANEL_UNDERLAYS_EM.NAME[row], UNIT_PANEL_UNDERLAY_COLORS.NAME)
}

export const PANEL_LEVEL_SHADOW = emUnderlayShadow(
  UNIT_PANEL_UNDERLAYS_EM.LEVEL,
  UNIT_PANEL_UNDERLAY_COLORS.NAME,
)

export const PANEL_COIN_NUMBER_SHADOW = emUnderlayShadow(
  UNIT_PANEL_UNDERLAYS_EM.COIN_NUMBER,
  UNIT_PANEL_UNDERLAY_COLORS.COIN_NUMBER,
)
