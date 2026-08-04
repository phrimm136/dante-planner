/**
 * Colour tokens applied inline (SVG fills, computed styles) where a Tailwind
 * class cannot reach.
 */

/**
 * Sanity Section Indicator Colors
 * Used in Identity detail page for mental condition display
 * Colors match game wiki conventions
 */
export const SANITY_INDICATOR_COLORS = {
  /** Blue - Sanity increment condition (wiki convention) */
  INCREMENT: '#80c9ff',
  /** Red - Sanity decrement condition (wiki convention) */
  DECREMENT: '#fe4b48',
  /** Border colors with 50% opacity for section headers */
  INCREMENT_BORDER: 'rgba(128, 201, 255, 0.5)',
  DECREMENT_BORDER: 'rgba(254, 75, 72, 0.5)',
} as const

/**
 * Passive Section Indicator Colors
 * Used in Identity detail page for battle/support passive section headers
 * Brown/copper tone to differentiate from sanity section (blue/red)
 */
export const PASSIVE_INDICATOR_COLORS = {
  /** Copper brown - passive section headers */
  TEXT: '#c9a86c',
  /** Border color with 50% opacity for section headers */
  BORDER: 'rgba(201, 168, 108, 0.5)',
} as const

/**
 * Discord brand color (Blurple)
 */
export const DISCORD_BLURPLE = '#5865F2'

/**
 * Inline accent colours shared by cards, indicators and event branches.
 */
export const ACCENT_COLORS = {
  /** Gold applied to an enhanced / upgraded value */
  ENHANCED: '#f8c200',
  /** Gold applied to an EGO gift tier marker */
  TIER: '#fcba03',
  /** Ab-event judgement succeeded */
  SUCCESS: '#00ff9c',
  /** Ab-event judgement failed */
  FAILURE: '#e30000',
} as const
