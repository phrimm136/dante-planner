/**
 * Global constants for game mechanics
 */

/**
 * Max level - used to calculate actual defense values and cap level inputs
 * Current value: 55
 */
export const MAX_LEVEL: number = 55

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
  'Ryoshu',
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
  'Combustion',
  'Laceration',
  'Vibration',
  'Burst',
  'Sinking',
  'Breath',
  'Charge',
] as const

/**
 * Affinity types for internal computation (data format names)
 */
export const AFFINITIES = ['CRIMSON', 'SCARLET', 'AMBER', 'SHAMROCK', 'AZURE', 'INDIGO', 'VIOLET'] as const

/**
 * Affinity type derived from AFFINITIES array
 */
export type Affinity = typeof AFFINITIES[number]

/**
 * Skill attribute types including NEUTRAL (for defense skills) and all affinities
 */
export const SKILL_ATTRIBUTE_TYPES = ['NEUTRAL', ...AFFINITIES] as const

/**
 * Skill attribute type derived from SKILL_ATTRIBUTE_TYPES array
 */
export type SkillAttributeType = typeof SKILL_ATTRIBUTE_TYPES[number]

/**
 * Attack types
 */
export const ATK_TYPES = ['SLASH', 'PENETRATE', 'HIT'] as const

/**
 * Attack type derived from ATK_TYPES array
 */
export type AtkType = typeof ATK_TYPES[number]

/**
 * EGO Gift keyword order for filtering and sorting (PascalCase internal format)
 */
export const KEYWORD_ORDER = [
  'Combustion',
  'Laceration',
  'Vibration',
  'Burst',
  'Sinking',
  'Breath',
  'Charge',
  'Slash',
  'Penetrate',
  'Hit',
  'None',
] as const

/**
 * Keyword type derived from KEYWORD_ORDER array
 */
export type Keyword = (typeof KEYWORD_ORDER)[number]

/**
 * Mirror Dungeon categories (floor counts)
 */
export const MD_CATEGORIES = ['5F', '10F', '15F'] as const

/**
 * MD Category type
 */
export type MDCategory = (typeof MD_CATEGORIES)[number]

/**
 * Planner keywords for MD - combines KEYWORD_ORDER (excluding None) and AFFINITIES
 */
export const PLANNER_KEYWORDS = [
  ...KEYWORD_ORDER.filter((k) => k !== 'None'),
  ...AFFINITIES,
] as const

/**
 * Planner keyword type
 */
export type PlannerKeyword = (typeof PLANNER_KEYWORDS)[number]


/**
 * Max number of deployment
 */
export const DEFAULT_DEPLOYMENT_MAX = 7

/**
 * Max number of observable EGO gifts
 */
export const MAX_OBSERVABLE_GIFTS = 3

/**
 * Default threadspin tier for EGO display (4 = max tier indicator)
 * Used in EGO cards when showing the tier icon
 */
export const EGO_DEFAULT_THREADSPIN_TIER = 4

/**
 * EGO Gift enhancement levels for comprehensive gift selection
 * 0 = base, 1 = +1, 2 = +2
 */
export const ENHANCEMENT_LEVELS = [0, 1, 2] as const

/**
 * Enhancement level type
 */
export type EnhancementLevel = (typeof ENHANCEMENT_LEVELS)[number]

/**
 * Display labels for enhancement levels
 * Used in EGOGiftEnhancementSelector overlay
 */
export const ENHANCEMENT_LABELS: Record<EnhancementLevel, string> = {
  0: '-',
  1: '+',
  2: '++',
} as const
