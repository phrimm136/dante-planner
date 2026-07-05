/**
 * Darkens a hex color by a given amount
 * @param hex - Hex color string (e.g., "#A0392B" or "A0392B")
 * @param amount - Darkening factor (0-1, where 0.5 = 50% darker)
 * @returns Darkened hex color string with # prefix
 */
export function darkenColor(hex: string, amount: number): string {
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
