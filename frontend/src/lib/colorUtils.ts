import { z } from 'zod'

/**
 * CSS hex color: `#rrggbb` or `#rrggbbaa`
 */
export type HexColor = `#${string}`

const HEX_COLOR_RE = /^#(?:[0-9a-f]{6}|[0-9a-f]{8})$/i

export const HexColorSchema = z.custom<HexColor>(
  (value) => typeof value === 'string' && HEX_COLOR_RE.test(value),
  'Expected a #rrggbb or #rrggbbaa color',
)

/**
 * Darkens a hex color by a given amount; an alpha channel is dropped
 * @param hex - Hex color string (e.g., "#A0392B" or "#A0392Bff")
 * @param amount - Darkening factor (0-1, where 0.5 = 50% darker)
 * @returns Opaque darkened hex color string with # prefix
 */
export function darkenColor(hex: string, amount: number): HexColor {
  // Remove # if present
  const cleanHex = hex.replace('#', '')

  // Parse RGB components
  const r = parseInt(cleanHex.substring(0, 2), 16)
  const g = parseInt(cleanHex.substring(2, 4), 16)
  const b = parseInt(cleanHex.substring(4, 6), 16)

  // Darken each component
  const factor = 1 - amount
  const newR = Math.round(r * factor)
  const newG = Math.round(g * factor)
  const newB = Math.round(b * factor)

  // Convert back to hex
  const toHex = (n: number) => n.toString(16).padStart(2, '0')
  return `#${toHex(newR)}${toHex(newG)}${toHex(newB)}`
}
