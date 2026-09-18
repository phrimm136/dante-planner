import seasonsJson from '@static/i18n/EN/seasons.json'
import unitKeywordsJson from '@static/i18n/EN/unitKeywords.json'
import { EGOGiftIdSchema, EGOIdSchema, IdentityIdSchema } from './ids'
import type { EGOGiftId, EGOId, IdentityId, SinnerScopedId } from './ids'

export const MAX_LEVEL = 60

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

export type Sinner = (typeof SINNERS)[number]

export const STATUS_EFFECTS = [
  'Combustion',
  'Laceration',
  'Vibration',
  'Burst',
  'Sinking',
  'Breath',
  'Charge',
] as const

export const AFFINITIES = [
  'CRIMSON',
  'SCARLET',
  'AMBER',
  'SHAMROCK',
  'AZURE',
  'INDIGO',
  'VIOLET',
] as const

export type Affinity = (typeof AFFINITIES)[number]

export const ATTRIBUTE_COLOR_TYPES = [...AFFINITIES, 'WHITE', 'BLACK', 'NEUTRAL', 'NONE'] as const

export type AttributeColorType = (typeof ATTRIBUTE_COLOR_TYPES)[number]

/**
 * Character names as the client's enum spells them (`Yisang`, `Merusault`), including
 * the non-playable cast; distinct from SINNERS, which carries the display spelling
 */
export const SINNER_NAMES = [
  'Yisang',
  'Faust',
  'DonQuixote',
  'Ryoshu',
  'Merusault',
  'HongLu',
  'Heathcliff',
  'Ishmael',
  'Rodion',
  'Sinclair',
  'Outis',
  'Gregor',
  'Dante',
  'Charon',
  'Vergilius',
  'Another',
] as const

export type SinnerName = (typeof SINNER_NAMES)[number]

export const PASSIVE_IMPORTANCE_LEVELS = ['1', '2', '3'] as const

export const COLLAB_SEASON_CODE = 8000

/**
 * Season codes of Walpurgisnacht units: 9100 + the event number
 */
export const WALPURGIS_SEASON_CODE_MIN = 9100
export const WALPURGIS_SEASON_CODE_MAX = 9199

export const SKILL_ATTRIBUTE_TYPES = ['NEUTRAL', ...AFFINITIES] as const

export type SkillAttributeType = (typeof SKILL_ATTRIBUTE_TYPES)[number]

export const ATK_TYPES = ['SLASH', 'PENETRATE', 'HIT'] as const

export type AtkType = (typeof ATK_TYPES)[number]

export const DEF_TYPES = [
  'GUARD',
  'EVADE',
  'COUNTER',
  'CLASHABLE_GUARD',
  'CLASHABLE_COUNTER',
] as const
export type DefType = (typeof DEF_TYPES)[number]

export const EGO_TYPES = ['ZAYIN', 'TETH', 'HE', 'WAW', 'ALEPH'] as const

export type EgoType = (typeof EGO_TYPES)[number]

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

export type Keyword = (typeof KEYWORD_ORDER)[number]

export const MD_CATEGORIES = ['5F', '10F', '15F'] as const

export type MDCategory = (typeof MD_CATEGORIES)[number]

export const RR_CATEGORIES = ['RR_PLACEHOLDER'] as const

export type RRCategory = (typeof RR_CATEGORIES)[number]

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

export const PLANNER_KEYWORDS = [
  ...KEYWORD_ORDER.filter((k) => k !== 'None'),
  ...AFFINITIES,
  '9154',
  ...SYNERGY_KEYWORDS,
] as const

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

export interface KeywordGrant {
  identityId: IdentityId
  keyword: (typeof STATUS_EFFECTS)[number]
}

function grant(identityId: string, keyword: KeywordGrant['keyword']): KeywordGrant {
  return { identityId: IdentityIdSchema.parse(identityId), keyword }
}

export const EGO_KEYWORD_GRANTS: ReadonlyMap<EGOId, KeywordGrant> = new Map([
  [EGOIdSchema.parse('20109'), grant('10110', 'Vibration')],
  [EGOIdSchema.parse('20509'), grant('10508', 'Laceration')],
])

export const GIFT_KEYWORD_GRANTS: ReadonlyMap<EGOGiftId, KeywordGrant> = new Map([
  [EGOGiftIdSchema.parse('9282'), grant('11009', 'Vibration')],
])

export const KEYWORD_GRANT_MIN_THREADSPIN = 2

export const DEFAULT_DEPLOYMENT_MAX = 7

export const MAX_OBSERVABLE_GIFTS = 3

export const ENHANCEMENT_LEVELS = [0, 1, 2] as const

export type EnhancementLevel = (typeof ENHANCEMENT_LEVELS)[number]

export const ENHANCEMENT_LABELS: Record<EnhancementLevel, string> = {
  0: '-',
  1: '+',
  2: '++',
} as const

export const OFFENSIVE_SKILL_SLOTS = [0, 1, 2] as const

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

export const EA_SURPLUS_THRESHOLD = 5

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

export type DungeonIdx = (typeof DUNGEON_IDX)[keyof typeof DUNGEON_IDX]

export const DIFFICULTY_LABELS = {
  NORMAL: 'NORMAL',
  HARD: 'HARD',
  INFINITY_MIRROR: 'INFINITY MIRROR',
  EXTREME_MIRROR: 'EXTREME MIRROR',
} as const

export type DifficultyLabel = (typeof DIFFICULTY_LABELS)[keyof typeof DIFFICULTY_LABELS]

export const FLOOR_COUNTS: Record<MDCategory, number> = {
  '5F': 5,
  '10F': 10,
  '15F': 15,
} as const

/**
 * Difficulties a floor may carry, indexed by 0-based floor within the category.
 * A 15F run repeats the 10F requirement over its first `FLOOR_COUNTS['10F']`
 * floors and demands Extreme above them.
 */
export const ALLOWED_FLOOR_DIFFICULTIES: Record<MDCategory, readonly (readonly DungeonIdx[])[]> = {
  '5F': Array.from({ length: FLOOR_COUNTS['5F'] }, () => [DUNGEON_IDX.NORMAL, DUNGEON_IDX.HARD]),
  '10F': Array.from({ length: FLOOR_COUNTS['10F'] }, () => [DUNGEON_IDX.HARD]),
  '15F': Array.from({ length: FLOOR_COUNTS['15F'] }, (_, floorIndex) =>
    floorIndex < FLOOR_COUNTS['10F'] ? [DUNGEON_IDX.HARD] : [DUNGEON_IDX.EXTREME],
  ),
}

export const DUNGEON_NAME_BY_IDX = new Map<DungeonIdx, string>(
  Object.entries(DUNGEON_IDX).map(([name, idx]) => [idx, name]),
)

export const PLANNER_TYPES = ['MIRROR_DUNGEON', 'REFRACTED_RAILWAY'] as const

export type PlannerType = (typeof PLANNER_TYPES)[number]

export type MDVersion = number

/**
 * Season identifiers for identity filtering
 * Derived from seasons.json keys (includes regular seasons, collabs, and Walpurgis Night events)
 */
export const SEASONS = Object.keys(seasonsJson).map(Number)

export type Season = (typeof SEASONS)[number]

/**
 * Association identifiers for identity filtering
 * Derived from unitKeywords.json keys (organization/affiliation names)
 */
export const ASSOCIATIONS = Object.keys(unitKeywordsJson)

export type Association = (typeof ASSOCIATIONS)[number]

export type DetailEntityType = 'identity' | 'ego' | 'egoGift'

/**
 * Maximum uptie/threadspin/enhancement levels by entity type (global ceiling).
 * Per-EGO threadspin max is carried by EGOEntity.maxThreadspin / EGOData.maxThreadspin.
 * - Identity: Uptie 1-4
 * - EGO: Threadspin 1-5
 * - EGO Gift: Enhancement 0-2 (displayed as base/+/++)
 */
export const MAX_ENTITY_TIER: Record<DetailEntityType, number> = {
  identity: 4,
  ego: 5,
  egoGift: 2,
}

export const MIN_ENTITY_TIER: Record<DetailEntityType, number> = {
  identity: 1,
  ego: 1,
  egoGift: 0,
}

export const BUFF_TYPES = ['Positive', 'Negative', 'Neutral'] as const

export type BuffType = (typeof BUFF_TYPES)[number]

export const SANITY_CONDITION_TYPE = {
  INCREMENT: 'inc',
  DECREMENT: 'dec',
} as const

export type SanityConditionType = (typeof SANITY_CONDITION_TYPE)[keyof typeof SANITY_CONDITION_TYPE]

/**
 * EGO Gift tiers (display format: Roman numerals)
 * Maps to TIER_1...TIER_EX tags in data
 */
export const EGO_GIFT_TIERS = ['I', 'II', 'III', 'IV', 'V', 'EX'] as const

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
 * EGO Gift difficulties for filtering
 * Maps to hardOnly/extremeOnly fields in data
 */
export const EGO_GIFT_DIFFICULTIES = ['normal', 'hard', 'extreme'] as const

export type EGOGiftDifficulty = (typeof EGO_GIFT_DIFFICULTIES)[number]

export const EGO_GIFT_ATTRIBUTE_TYPES = AFFINITIES

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
 */
export function getSinnerFromId(id: SinnerScopedId): string {
  const sinnerIndex = parseInt(id.substring(1, 3), 10) - 1
  return SINNERS[sinnerIndex] || 'Unknown'
}
