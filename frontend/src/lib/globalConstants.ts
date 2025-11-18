/**
 * Global constants for game mechanics
 */

/**
 * Base defense level - used to calculate actual defense values
 * Current value: 55 (max level)
 */
export const BASE_LEVEL = 55

/**
 * Search bar debounce delay in milliseconds
 */
export const SEARCH_DEBOUNCE_DELAY = 100

/**
 * Sinner names
 */
export const SINNERS = [
  'YiSang',
  'Faust',
  'DonQuixote',
  'RyoShu',
  'Meursault',
  'HongLu',
  'Heathcliff',
  'Ishmael',
  'Rodion',
  'Sinclair',
  'Outis',
  'Gregor',
] as const

/**
 * Status effects (keywords)
 */
export const STATUS_EFFECTS = [
  'Burn',
  'Bleed',
  'Tremor',
  'Rupture',
  'Sinking',
  'Poise',
  'Charge',
] as const
