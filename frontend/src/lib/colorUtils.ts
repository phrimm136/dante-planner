import { z } from 'zod'

export type HexColor = `#${string}`

const HEX_COLOR_RE = /^#(?:[0-9a-f]{6}|[0-9a-f]{8})$/i

export const HexColorSchema = z.custom<HexColor>(
  (value) => typeof value === 'string' && HEX_COLOR_RE.test(value),
  'Expected a #rrggbb or #rrggbbaa color',
)

export function darkenColor(hex: string, amount: number): HexColor {
  const cleanHex = hex.replace('#', '')

  const r = parseInt(cleanHex.substring(0, 2), 16)
  const g = parseInt(cleanHex.substring(2, 4), 16)
  const b = parseInt(cleanHex.substring(4, 6), 16)

  const factor = 1 - amount
  const newR = Math.round(r * factor)
  const newG = Math.round(g * factor)
  const newB = Math.round(b * factor)

  const toHex = (n: number) => n.toString(16).padStart(2, '0')
  return `#${toHex(newR)}${toHex(newG)}${toHex(newB)}`
}

export function withAlpha(hex: string, alpha: number): HexColor {
  const rgb = hex.replace('#', '').substring(0, 6)
  const a = Math.round(alpha * 255)
    .toString(16)
    .padStart(2, '0')
  return `#${rgb}${a}`
}
