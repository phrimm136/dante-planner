/**
 * Game vocabulary constants for Limbus Company domain facts
 */

import seasonsJson from '@static/i18n/EN/seasons.json'
import unitKeywordsJson from '@static/i18n/EN/unitKeywords.json'

/**
 * Max level - used to calculate actual defense values and cap level inputs
 * Current value: 60
 */
export const MAX_LEVEL = 60

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
 * Sinner type derived from SINNERS array
 */
export type Sinner = (typeof SINNERS)[number]

/**
 * Sinner signature colors (extracted from sinner icons, adjusted for text readability)
 * Used for sinner name display in identity/EGO detail headers
 *
 * Color derivation:
 * - YiSang: Light blue-gray (icon is white/ethereal)
 * - Faust: Soft pink (rose icon)
 * - DonQuixote: Golden yellow (carousel horse icon)
 * - Ryoshu: Deep red (sakura/blood icon)
 * - Meursault: Navy blue (refined, stoic)
 * - HongLu: Cyan/turquoise (bright, playful)
 * - Heathcliff: Purple (broken moon icon)
 * - Ishmael: Orange (warm, fiery)
 * - Rodion: Dark crimson (bleeding heart icon)
 * - Sinclair: Olive/lime green (plant motif)
 * - Outis: Forest green (web/net icon)
 * - Gregor: Brown (insect/earth tones)
 */
export const SINNER_COLORS: Record<Sinner, string> = {
  YiSang: '#a8c4d8',
  Faust: '#f0a8ac',
  DonQuixote: '#e8d840',
  Ryoshu: '#c82020',
  Meursault: '#4858a8',
  HongLu: '#48d0b8',
  Heathcliff: '#6850a0',
  Ishmael: '#e89020',
  Rodion: '#982828',
  Sinclair: '#98a830',
  Outis: '#487858',
  Gregor: '#886030',
} as const

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
export const AFFINITIES = [
  'CRIMSON',
  'SCARLET',
  'AMBER',
  'SHAMROCK',
  'AZURE',
  'INDIGO',
  'VIOLET',
] as const

/**
 * Affinity type derived from AFFINITIES array
 */
export type Affinity = (typeof AFFINITIES)[number]

/**
 * Skill attribute types including NEUTRAL (for defense skills) and all affinities
 */
export const SKILL_ATTRIBUTE_TYPES = ['NEUTRAL', ...AFFINITIES] as const

/**
 * Skill attribute type derived from SKILL_ATTRIBUTE_TYPES array
 */
export type SkillAttributeType = (typeof SKILL_ATTRIBUTE_TYPES)[number]

/**
 * Skill frame glow colors for coin power backgrounds
 * Extracted from skill frame BG images - these are bright neon colors
 * Used for visual prominence in coin power display
 */
export const SKILL_FRAME_GLOW_COLORS: Record<SkillAttributeType, string> = {
  CRIMSON: '#fe1a1a',
  SCARLET: '#fb4201',
  AMBER: '#fbfa03',
  SHAMROCK: '#44ff03',
  AZURE: '#01fdfb',
  INDIGO: '#0243fc',
  VIOLET: '#fe02fd',
  NEUTRAL: '#e8c89f',
} as const

/**
 * Attack types
 */
export const ATK_TYPES = ['SLASH', 'PENETRATE', 'HIT'] as const

/**
 * Attack type derived from ATK_TYPES array
 */
export type AtkType = (typeof ATK_TYPES)[number]

export const DEF_TYPES = [
  'GUARD',
  'EVADE',
  'COUNTER',
  'CLASHABLE_GUARD',
  'CLASHABLE_COUNTER',
] as const
export type DefType = (typeof DEF_TYPES)[number]

/**
 * EGO types (Hebrew letters from Lobotomy Corp lore)
 * Order: Lowest risk to highest risk
 */
export const EGO_TYPES = ['ZAYIN', 'TETH', 'HE', 'WAW', 'ALEPH'] as const

/**
 * EGO type derived from EGO_TYPES array
 */
export type EgoType = (typeof EGO_TYPES)[number]

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
 * Refracted Railway categories (placeholder for future implementation)
 */
export const RR_CATEGORIES = ['RR_PLACEHOLDER'] as const

/**
 * RR Category type
 */
export type RRCategory = (typeof RR_CATEGORIES)[number]

/**
 * MD category badge styles for planner list display
 * Maps category to Tailwind classes for consistent badge styling
 * Colors: 5F=orange (entry), 10F=red (mid), 15F=white (endgame)
 * @deprecated Use MD_CATEGORY_COLORS with inline styles instead for exact color matching
 */
export const MD_CATEGORY_STYLES: Record<MDCategory, string> = {
  '5F': 'bg-orange-500 text-white',
  '10F': 'bg-red-500 text-white',
  '15F': 'bg-white text-black',
} as const

/**
 * Synergy keywords for planner
 */
export const SYNERGY_KEYWORDS = [
  'Assemble',
  'KnowledgeExplored',
  'AaCePcBt',
  'SwordPlayOfTheHomeland',
  'EchoOfMansion',
  'TimeSuspend',
  'EmergencyChargeForceField',
  'BloodDinner',
  'BlackCloud',
  'RetaliationBook',
  'HeishouSynergy',
  'BlessingOfIndexPrescriptAlly',
  'Bullet',
  'Inspire',
  '9828',
  'SojiRyoshuEntangle',
  'DawnTeam',
] as const

/**
 * Planner keywords for MD - combines KEYWORD_ORDER (excluding None), AFFINITIES, and SYNERGY_KEYWORDS
 */
export const PLANNER_KEYWORDS = [
  ...KEYWORD_ORDER.filter((k) => k !== 'None'),
  ...AFFINITIES,
  '9154',
  ...SYNERGY_KEYWORDS,
] as const

/**
 * Planner keyword type
 */
export type PlannerKeyword = (typeof PLANNER_KEYWORDS)[number]

/**
 * Legacy keyword aliases → current ids. Mirrors the backend `RENAME_MAP`
 * (KeywordSetConverter). Outdated clients (stale IndexedDB, cached bundles) still
 * hold pre-rename ids; the read-side normalizer remaps them before use so their
 * icons/filters resolve. Keys are intentionally absent from PLANNER_KEYWORDS.
 * @see backend KeywordSetConverter.RENAME_MAP — must stay in lockstep.
 */
export const KEYWORD_RENAME_MAP: Readonly<Record<string, string>> = {
  AccelBullet: '9828',
  ChargeLoad: 'EmergencyChargeForceField',
} as const

/**
 * Max number of deployment
 */
export const DEFAULT_DEPLOYMENT_MAX = 7

/**
 * Max number of observable EGO gifts
 */
export const MAX_OBSERVABLE_GIFTS = 3

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

/**
 * Offensive skill slots for skill replacement (0=S1, 1=S2, 2=S3)
 * Defense skill (slot 3) is not part of skill replacement
 */
export const OFFENSIVE_SKILL_SLOTS = [0, 1, 2] as const

/**
 * Offensive skill slot type
 */
export type OffensiveSkillSlot = (typeof OFFENSIVE_SKILL_SLOTS)[number]

/**
 * Default EA (Exchange Allowance) values per offensive skill slot
 * Skill 1 = 3 EA, Skill 2 = 2 EA, Skill 3 = 1 EA
 */
export const DEFAULT_SKILL_EA: Record<OffensiveSkillSlot, number> = {
  0: 3,
  1: 2,
  2: 1,
} as const

/**
 * Dungeon difficulty indices from themePackList.json
 * Maps to internal game data (0=normal, 1=hard, 2=parallel, 3=extreme)
 */
export const DUNGEON_IDX = {
  NORMAL: 0,
  HARD: 1,
  PARALLEL: 2,
  EXTREME: 3,
} as const

/**
 * Dungeon difficulty index type
 */
export type DungeonIdx = (typeof DUNGEON_IDX)[keyof typeof DUNGEON_IDX]

/**
 * Difficulty labels for floor indicator display (not i18n - game terminology)
 */
export const DIFFICULTY_LABELS = {
  NORMAL: 'NORMAL',
  HARD: 'HARD',
  INFINITY_MIRROR: 'INFINITY MIRROR',
  EXTREME_MIRROR: 'EXTREME MIRROR',
} as const

/**
 * Difficulty label type
 */
export type DifficultyLabel = (typeof DIFFICULTY_LABELS)[keyof typeof DIFFICULTY_LABELS]

/**
 * Difficulty colors for indicator display
 * NORMAL: yellow, HARD: orange, INFINITY: red, EXTREME: white
 */
export const DIFFICULTY_COLORS: Record<DifficultyLabel, string> = {
  [DIFFICULTY_LABELS.NORMAL]: '#ffd700',
  [DIFFICULTY_LABELS.HARD]: '#ff8c00',
  [DIFFICULTY_LABELS.INFINITY_MIRROR]: '#dc070c',
  [DIFFICULTY_LABELS.EXTREME_MIRROR]: '#ffffff',
} as const

/**
 * MD category background colors aligned with difficulty colors
 * 5F uses HARD orange, 10F uses INFINITY red, 15F uses EXTREME white
 */
export const MD_CATEGORY_COLORS: Record<MDCategory, string> = {
  '5F': DIFFICULTY_COLORS[DIFFICULTY_LABELS.HARD],
  '10F': DIFFICULTY_COLORS[DIFFICULTY_LABELS.INFINITY_MIRROR],
  '15F': DIFFICULTY_COLORS[DIFFICULTY_LABELS.EXTREME_MIRROR],
} as const

/**
 * Text colors for category badges (white for 5F/10F, black for 15F)
 */
export const MD_CATEGORY_TEXT_COLORS: Record<MDCategory, string> = {
  '5F': '#ffffff',
  '10F': '#ffffff',
  '15F': '#000000',
} as const

/**
 * Floor counts per MD category
 */
export const FLOOR_COUNTS: Record<MDCategory, number> = {
  '5F': 5,
  '10F': 10,
  '15F': 15,
} as const

/**
 * Selectable floors mapping from themePackList.json
 * 0 → 1F, 1 → 2F, 2 → 3F, 3 → 4F, 4 → 5-10F (represented as 5F for filtering)
 */
export const SELECTABLE_FLOOR_MAP = [1, 2, 3, 4, 5] as const

/**
 * Flavor text color for skill / status-effect lore lines.
 * Mirrors in-game `*FlavorGlow` TMP material face color used by
 * `[Text]SkillInfoFlavor` and `[Text]BuffFlavor` GameObjects.
 */
export const FLAVOR_TEXT_COLOR = '#a16a3b'

/**
 * Planner types for different game content
 * - MIRROR_DUNGEON: Mirror Dungeon mode (single current version)
 * - REFRACTED_RAILWAY: Refracted Railway mode (multiple parallel versions)
 */
export const PLANNER_TYPES = ['MIRROR_DUNGEON', 'REFRACTED_RAILWAY'] as const

/**
 * Planner type derived from PLANNER_TYPES array
 */
export type PlannerType = (typeof PLANNER_TYPES)[number]

/**
 * Default planner type for new planners
 */
export const DEFAULT_PLANNER_TYPE: PlannerType = 'MIRROR_DUNGEON'

/**
 * Mirror Dungeon accent colors by content version
 * Used for version-specific UI elements like StartBuffMiniCard text
 */
export const MD_ACCENT_COLORS: Record<number, string> = {
  5: '#ff9933',
  6: '#00ffcc',
  7: '#e5d7d7',
} as const

/**
 * Mirror Dungeon version type
 * Valid versions are determined at runtime by the backend config (mdAvailableVersions)
 */
export type MDVersion = number

/**
 * Season identifiers for identity filtering
 * Derived from seasons.json keys (includes regular seasons, collabs, and Walpurgis Night events)
 */
export const SEASONS = Object.keys(seasonsJson).map(Number)

/**
 * Season type derived from SEASONS array
 */
export type Season = (typeof SEASONS)[number]

/**
 * Association identifiers for identity filtering
 * Derived from unitKeywords.json keys (organization/affiliation names)
 */
export const ASSOCIATIONS = Object.keys(unitKeywordsJson)

/**
 * Association type derived from ASSOCIATIONS array
 */
export type Association = (typeof ASSOCIATIONS)[number]

/**
 * Entity types for detail pages
 */
export type DetailEntityType = 'identity' | 'ego' | 'egoGift'

/**
 * Maximum uptie/threadspin/enhancement levels by entity type (global ceiling).
 * Per-EGO threadspin max is carried by EGOListItem.maxThreadspin / EGOData.maxThreadspin.
 * - Identity: Uptie 1-4
 * - EGO: Threadspin 1-5
 * - EGO Gift: Enhancement 0-2 (displayed as base/+/++)
 */
export const MAX_ENTITY_TIER: Record<DetailEntityType, number> = {
  identity: 4,
  ego: 5,
  egoGift: 2,
}

/**
 * Minimum tier/enhancement levels by entity type
 * - Identity/EGO: Start at 1
 * - EGO Gift: Start at 0 (base level)
 */
export const MIN_ENTITY_TIER: Record<DetailEntityType, number> = {
  identity: 1,
  ego: 1,
  egoGift: 0,
}

/**
 * Selector labels by entity type (for i18n keys)
 */
export const ENTITY_TIER_LABELS: Record<DetailEntityType, string> = {
  identity: 'uptie',
  ego: 'threadspin',
  egoGift: 'enhancement',
}

/**
 * Buff type categories for battle keywords
 */
export const BUFF_TYPES = ['Positive', 'Negative', 'Neutral'] as const

/**
 * Buff type derived from BUFF_TYPES array
 */
export type BuffType = (typeof BUFF_TYPES)[number]

/**
 * Sanity condition types for increment/decrement conditions
 * Used in formatSanityCondition and related hooks
 */
export const SANITY_CONDITION_TYPE = {
  /** Sanity increase condition */
  INCREMENT: 'inc',
  /** Sanity decrease condition */
  DECREMENT: 'dec',
} as const

/**
 * Sanity condition type derived from SANITY_CONDITION_TYPE
 */
export type SanityConditionType = (typeof SANITY_CONDITION_TYPE)[keyof typeof SANITY_CONDITION_TYPE]

/**
 * EGO Gift tiers (display format: Roman numerals)
 * Maps to TIER_1...TIER_EX tags in data
 */
export const EGO_GIFT_TIERS = ['I', 'II', 'III', 'IV', 'V', 'EX'] as const

/**
 * EGO Gift tier type (display format)
 */
export type EGOGiftTier = (typeof EGO_GIFT_TIERS)[number]

/**
 * EGO Gift tier tags (data format)
 * Matches tag values in egoGiftSpecList.json
 */
export const EGO_GIFT_TIER_TAGS = [
  'TIER_1',
  'TIER_2',
  'TIER_3',
  'TIER_4',
  'TIER_5',
  'TIER_EX',
] as const

/**
 * EGO Gift tier tag type (data format)
 */
export type EGOGiftTierTag = (typeof EGO_GIFT_TIER_TAGS)[number]

/**
 * EGO Gift difficulties for filtering
 * Maps to hardOnly/extremeOnly fields in data
 */
export const EGO_GIFT_DIFFICULTIES = ['normal', 'hard', 'extreme'] as const

/**
 * EGO Gift difficulty type
 */
export type EGOGiftDifficulty = (typeof EGO_GIFT_DIFFICULTIES)[number]

/**
 * EGO Gift attribute types for filtering
 * All 7 affinities - reuses AFFINITIES constant
 */
export const EGO_GIFT_ATTRIBUTE_TYPES = AFFINITIES

/**
 * EGO Gift attribute type (same as Affinity)
 */
export type EGOGiftAttributeType = Affinity

/**
 * Theme pack dungeon difficulties for filtering
 * Maps to dungeonIdx values in exceptionConditions
 */
export const THEME_PACK_DIFFICULTIES = [
  DUNGEON_IDX.NORMAL,
  DUNGEON_IDX.HARD,
  DUNGEON_IDX.PARALLEL,
  DUNGEON_IDX.EXTREME,
] as const

/**
 * Display labels for theme pack dungeon difficulties
 */
export const THEME_PACK_DIFFICULTY_LABELS: Record<DungeonIdx, string> = {
  [DUNGEON_IDX.NORMAL]: 'Normal',
  [DUNGEON_IDX.HARD]: 'Hard',
  [DUNGEON_IDX.PARALLEL]: 'Infinity',
  [DUNGEON_IDX.EXTREME]: 'Extreme',
}

/**
 * Theme pack selectable floors for filtering (0-indexed in data, display as 1F-5F)
 */
export const THEME_PACK_FLOORS = [0, 1, 2, 3, 4] as const

export type ThemePackFloor = (typeof THEME_PACK_FLOORS)[number]

/**
 * Display labels for theme pack floors
 */
export const THEME_PACK_FLOOR_LABELS: Record<ThemePackFloor, string> = {
  0: '1F',
  1: '2F',
  2: '3F',
  3: '4F',
  4: '5F',
}

/**
 * Absolute floor range per dungeon mode for packs that carry no
 * selectableFloors entry. Infinity occupies 6-10F, Extreme 11-15F.
 * Normal/Hard are intentionally absent — those modes use per-pack
 * selectableFloors indexed into 1F-5F.
 */
export const DUNGEON_FIXED_FLOOR_RANGE: Partial<Record<DungeonIdx, readonly number[]>> = {
  [DUNGEON_IDX.PARALLEL]: [6, 7, 8, 9, 10],
  [DUNGEON_IDX.EXTREME]: [11, 12, 13, 14, 15],
} as const

/**
 * EGO Gift enhancement base costs by tier
 * Tier 5 and EX gifts cannot be enhanced
 * Level 1 (+) = base cost, Level 2 (++) = 2x base cost
 */
export const EGO_GIFT_ENHANCEMENT_BASE_COSTS: Record<string, number> = {
  '1': 50,
  '2': 60,
  '3': 75,
  '4': 100,
} as const

/**
 * Extracts sinner name from entity ID
 * ID format: T SS II (5 digits)
 *   T: Type (1=identity, 2=ego)
 *   SS: Sinner index (01-12)
 *   II: Entity index within sinner
 * Example: 10101 -> type 1, sinner 01 -> YiSang
 * Example: 20305 -> type 2, sinner 03 -> DonQuixote
 * @param id - Entity ID (identity or EGO)
 * @returns Sinner name (e.g., "YiSang", "Faust")
 */
export function getSinnerFromId(id: string): string {
  const sinnerIndex = parseInt(id.substring(1, 3), 10) - 1
  return SINNERS[sinnerIndex] || 'Unknown'
}
