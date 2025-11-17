/**
 * Global constants for game mechanics
 */

/**
 * Base defense level - used to calculate actual defense values
 * Current value: 55 (max level)
 */
export const BASE_LEVEL = 55

/**
 * Sin color configurations (Foreground, Background)
 */
export const SIN_COLORS = {
  wrath: { fg: '#fe0000', bg: '#fe0000' },
  lust: { fg: '#f86300', bg: '#fe4000' },
  sloth: { fg: '#f4c528', bg: '#fefe00' },
  gluttony: { fg: '#9dfe00', bg: '#40fe00' },
  gloom: { fg: '#0dc1eb', bg: '#00fefe' },
  pride: { fg: '#0048cc', bg: '#0040fe' },
  envy: { fg: '#9300db', bg: '#fe00fe' },
  defense: { fg: '#9f693a', bg: '#e9c99f' },
} as const

export type SinType = keyof typeof SIN_COLORS
