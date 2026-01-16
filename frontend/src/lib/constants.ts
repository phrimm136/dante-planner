/**
 * Global constants for game mechanics
 */

import seasonsJson from '@static/i18n/EN/seasons.json'
import unitKeywordsJson from '@static/i18n/EN/unitKeywords.json'

/**
 * Max level - used to calculate actual defense values and cap level inputs
 * Current value: 55
 */
export const MAX_LEVEL = 55

/**
 * Search bar debounce delay in milliseconds
 */
export const SEARCH_DEBOUNCE_DELAY = 100

/**
 * Filter sidebar width in pixels (desktop view)
 * Used by FilterSidebar component for consistent layout
 */
export const FILTER_SIDEBAR_WIDTH = 280

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
export type AtkType = typeof ATK_TYPES[number]

/**
 * EGO types (Hebrew letters from Lobotomy Corp lore)
 * Order: Lowest risk to highest risk
 */
export const EGO_TYPES = ['ZAYIN', 'TETH', 'HE', 'WAW', 'ALEPH'] as const

/**
 * EGO type derived from EGO_TYPES array
 */
export type EgoType = typeof EGO_TYPES[number]

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
 */
export const MD_CATEGORY_STYLES: Record<MDCategory, string> = {
  '5F': 'bg-green-600 text-white',
  '10F': 'bg-blue-600 text-white',
  '15F': 'bg-purple-600 text-white',
} as const


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
 * Maps to internal game data (0=normal, 1=hard, 3=extreme - no 2)
 */
export const DUNGEON_IDX = {
  NORMAL: 0,
  HARD: 1,
  EXTREME: 3,
} as const

/**
 * Dungeon difficulty index type
 */
export type DungeonIdx = typeof DUNGEON_IDX[keyof typeof DUNGEON_IDX]

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
export type DifficultyLabel = typeof DIFFICULTY_LABELS[keyof typeof DIFFICULTY_LABELS]

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
 * UI Colors for consistent styling across components
 * These are hex values used in Tailwind arbitrary values (e.g., ring-[#fcba03])
 */
export const UI_COLORS = {
  /** Golden yellow for hover/selection highlights - use with ring- or border- */
  SELECTION_HIGHLIGHT: '#fcba03',
} as const

/**
 * Sanity Section Indicator Colors
 * Used in Identity detail page for mental condition display
 * Colors match game wiki conventions
 */
export const SANITY_INDICATOR_COLORS = {
  /** Red - Panic type indicator */
  PANIC: '#ef4444',
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
 * Planner types for different game content
 * - MIRROR_DUNGEON: Mirror Dungeon mode (single current version from backend)
 * - REFRACTED_RAILWAY: Refracted Railway mode (multiple parallel versions from backend)
 *
 * Version numbers are fetched from backend /api/planner/md/config endpoint
 * @see usePlannerConfig hook
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
} as const

/**
 * Mirror Dungeon versions
 * Used for version-specific data loading (buffs, gifts)
 */
export const MD_VERSIONS = [5, 6] as const

/**
 * Mirror Dungeon version type
 */
export type MDVersion = (typeof MD_VERSIONS)[number]

/**
 * Section Styling Tokens
 * Consistent styling for complex pages like PlannerMDNewPage
 * Import: import { SECTION_STYLES } from '@/lib/constants'
 */
export const SECTION_STYLES = {
  /** Typography classes for section hierarchy */
  TEXT: {
    /** Major section titles - use for all h2 section headers */
    header: 'text-xl font-semibold',
    /** Subsection titles - use for nested headers */
    subHeader: 'text-lg font-medium',
    /** Form field labels */
    label: 'text-sm font-medium',
    /** Helper text, counts, hints */
    caption: 'text-sm text-muted-foreground',
  },

  /** Spacing classes for consistent layout */
  SPACING: {
    /** Between major page sections */
    section: 'space-y-6',
    /** Between elements inside a section */
    content: 'space-y-4',
    /** Between form elements (label + input) */
    elements: 'space-y-2',
    /** Standard grid/flex gap */
    gap: 'gap-4',
  },

  /** Section container background with border */
  container: 'bg-card border border-border rounded-md p-6',

  /** Standard responsive grid (2→3→4→6 columns) */
  grid: 'bg-muted grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6',

  /** Dense responsive grid (2→3→5 columns) - for compact layouts like StartBuff */
  gridDense: 'bg-muted grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5',
} as const

/**
 * Empty State Styling Tokens
 * Consistent styling for clickable empty state placeholders in planner sections
 * Import: import { EMPTY_STATE } from '@/lib/constants'
 */
export const EMPTY_STATE = {
  /** Minimum height for empty state container (matches min-h-28 = 7rem) */
  MIN_HEIGHT: 'min-h-28',
  /** Dashed border styling for empty state visual indicator */
  DASHED_BORDER: 'border-2 border-dashed border-muted-foreground/50 rounded-lg',
} as const

/**
 * Note Editor Constants
 */

/**
 * Maximum number of note editors allowed on a single page
 * Used to prevent performance issues with too many Tiptap instances
 */
export const MAX_NOTE_EDITORS = 20

/**
 * Maximum byte length for note content (matches backend validation)
 * Backend limit: application.properties planner.validation.max-note-size=2048
 * Counts JSON-serialized bytes of Tiptap JSONContent, not character count
 *
 * Note: Frontend uses JSON.stringify, backend uses Jackson ObjectMapper.
 * These may produce slightly different output (whitespace, key ordering).
 * No safety margin applied - frontend shows exact backend limit for transparency.
 * Users should stay below red threshold to avoid save failures.
 */
export const MAX_NOTE_BYTES = 2048

/**
 * Toolbar formatting options for note editor
 * Order matches the toolbar button layout
 */
export const NOTE_TOOLBAR_ITEMS = [
  'bold',
  'italic',
  'strike',
  'heading1',
  'heading2',
  'heading3',
  'bulletList',
  'orderedList',
  'blockquote',
  'code',
  'codeBlock',
  'link',
  'image',
  'spoiler',
] as const

/**
 * Note toolbar item type
 */
export type NoteToolbarItem = (typeof NOTE_TOOLBAR_ITEMS)[number]

/**
 * Planner Storage Constants
 */

/**
 * Auto-save debounce delay in milliseconds
 * Triggers save 2 seconds after user stops making changes
 */
export const AUTO_SAVE_DEBOUNCE_MS = 2000


/**
 * Current planner schema version for migration support
 * Increment when planner data structure changes
 */
export const PLANNER_SCHEMA_VERSION = 1

/**
 * Current export file format version for migration support
 * Increment when export envelope structure changes
 */
export const EXPORT_VERSION = 1

/**
 * IndexedDB storage key prefixes for planner data
 * All planner-related keys use these prefixes for namespacing
 */
export const PLANNER_STORAGE_KEYS = {
  /** Common prefix for all planner types */
  PLANNER: 'planner',
  /** Mirror Dungeon planner type suffix */
  MD: 'md',
  /** Key for unique device identifier */
  DEVICE_ID: 'deviceId',
} as const

/**
 * Planner storage key type
 */
export type PlannerStorageKey = typeof PLANNER_STORAGE_KEYS[keyof typeof PLANNER_STORAGE_KEYS]

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
 * Detail Page Layout Constants
 * Used for modular two-column detail page layout (Identity, EGO, EGO Gift)
 */
export const DETAIL_PAGE = {
  /** Desktop breakpoint in pixels (1024px = lg: in Tailwind) */
  BREAKPOINT_LG: 1024,
  /** Column ratio: 4:6 (left:right) using 10-column grid */
  COLUMN_LEFT: 'lg:col-span-4',
  COLUMN_RIGHT: 'lg:col-span-6',
  /** Right panel max height for scroll containment */
  RIGHT_PANEL_MAX_HEIGHT: 'calc(100vh - 12rem)',
  /** Selector sticky offset from top */
  SELECTOR_STICKY_TOP: 'top-0',
} as const

/**
 * Entity types for detail pages
 */
export type DetailEntityType = 'identity' | 'ego' | 'egoGift'

/**
 * Maximum uptie/threadspin/enhancement levels by entity type
 * - Identity: Uptie 1-4
 * - EGO: Threadspin 1-4
 * - EGO Gift: Enhancement 0-2 (displayed as base/+/++)
 */
export const MAX_ENTITY_TIER: Record<DetailEntityType, number> = {
  identity: 4,
  ego: 4,
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
 * Card Grid Layout Constants
 * Used by ResponsiveCardGrid for consistent card sizing across browser pages
 */
export const CARD_GRID = {
  /** Card widths in pixels - matches actual card component dimensions */
  WIDTH: {
    /** IdentityCard: w-40 (160px) */
    IDENTITY: 160,
    /** EGOCard: w-40 (160px) */
    EGO: 160,
    /** EGOGiftCard: 96px (from minmax in selection list) */
    EGO_GIFT: 96,
    /** PlannerCard: 280px for adequate text/metadata space */
    PLANNER: 280,
  },
  /** Default gap between cards in pixels (gap-4 = 16px) */
  DEFAULT_GAP: 16,
} as const

/**
 * Card width type for ResponsiveCardGrid
 */
export type CardGridWidth = typeof CARD_GRID.WIDTH[keyof typeof CARD_GRID.WIDTH]

/**
 * Virtual Grid Rendering Constants
 * Used by VirtualCardGrid for row-based virtualization configuration
 */
export const VIRTUAL_GRID = {
  /** Number of rows to render outside visible area for smooth scrolling */
  OVERSCAN: 1,
  /** Container height in pixels for observation pane */
  CONTAINER_HEIGHT: 350,
  /** Skip virtualization if item count is below this threshold */
  THRESHOLD: 5,
} as const

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
export type SanityConditionType = typeof SANITY_CONDITION_TYPE[keyof typeof SANITY_CONDITION_TYPE]

/**
 * Planner List Constants
 * Used by PlannerListPage and related components
 */
export const PLANNER_LIST = {
  /** Number of planners per page */
  PAGE_SIZE: 20,
  /** Maximum keywords to display on a card before truncating */
  MAX_KEYWORDS_DISPLAY: 3,
  /** Available sort options */
  SORT_OPTIONS: ['recent', 'popular', 'votes'] as const,
} as const

/**
 * Planner status badge styles for card display
 * - Draft: Never manually saved (yellowish)
 * - Unsynced: Has local changes not pushed (blue)
 * - Unpublished: Published planner with local changes (orange)
 */
export const PLANNER_STATUS_BADGE_STYLES = {
  DRAFT: 'bg-yellow-500/20 text-yellow-600 dark:text-yellow-400',
  UNSYNCED: 'bg-blue-500/20 text-blue-600 dark:text-blue-400',
  UNPUBLISHED: 'bg-orange-500/20 text-orange-600 dark:text-orange-400',
} as const

/**
 * Planner status badge type
 */
export type PlannerStatusBadge = keyof typeof PLANNER_STATUS_BADGE_STYLES

/**
 * Calculate total pages from item count
 * Uses PLANNER_LIST.PAGE_SIZE as divisor
 */
export function calculatePlannerPages(totalCount: number): number {
  return Math.ceil(totalCount / PLANNER_LIST.PAGE_SIZE)
}

/**
 * Planner list sort option type
 */
export type PlannerListSortOption = typeof PLANNER_LIST.SORT_OPTIONS[number]

/**
 * EGO Gift Filter Constants
 * Used by EGO Gift browser filter sidebar and filtering utilities (lib/egoGiftFilter.ts)
 *
 * Filtering Logic:
 * - Difficulty: Derived from hardOnly/extremeOnly boolean flags in data
 *   - normal: neither flag set
 *   - hard: hardOnly=true
 *   - extreme: extremeOnly=true
 * - Tier: Extracted from tag array (TIER_1...TIER_EX)
 * - Attribute Types: Same as Affinity types (CRIMSON, AMBER, SCARLET, etc.)
 *
 * @see EGOGiftPage.tsx - Filter sidebar integration
 * @see EGOGiftList.tsx - Filter application
 * @see egoGiftFilter.ts - Filter utility functions
 */

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
export const EGO_GIFT_TIER_TAGS = ['TIER_1', 'TIER_2', 'TIER_3', 'TIER_4', 'TIER_5', 'TIER_EX'] as const

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
 * Difficulty badge styles for EGO Gift metadata display
 * Used in EGOGiftMetadata component for Hard/Extreme badges
 */
export const DIFFICULTY_BADGE_STYLES = {
  HARD: 'bg-orange-500/20 text-orange-500',
  EXTREME: 'bg-red-500/20 text-red-500',
} as const

/**
 * Extraction (Gacha) Rate Constants
 * Used by extraction probability calculator
 *
 * Rate mechanics:
 * - Standard rates apply when banner has EGO available
 * - "All EGO Collected" shifts EGO rate to ID pool
 * - Announcer rate takes from 1★ ID pool when featured
 * - Rate-up is split evenly among featured items of same type
 *
 * @see ExtractionPlannerPage for usage
 */
export const EXTRACTION_RATES = {
  /**
   * Base rates for standard banner (no modifiers)
   * Total: 2.9% 3★ID + 12.8% 2★ID + 83.0% 1★ID + 1.3% EGO = 100%
   *
   * EGO breakdown:
   * - Rate-up EGO: 0.65% (split among featured)
   * - Non-rate-up (pik-tteul): 0.65%
   */
  STANDARD: {
    THREE_STAR_ID: 0.029,
    TWO_STAR_ID: 0.128,
    ONE_STAR_ID: 0.830,
    EGO: 0.013,
    ANNOUNCER: 0,
  },
  /** Rates when Announcer is featured (takes from 1★ pool) */
  WITH_ANNOUNCER: {
    THREE_STAR_ID: 0.029,
    TWO_STAR_ID: 0.128,
    ONE_STAR_ID: 0.817,
    EGO: 0.013,
    ANNOUNCER: 0.013,
  },
  /**
   * Rates when "All EGO Collected" (owned all non-featured EGO)
   *
   * When user owns all non-rate-up EGO:
   * - Non-rate-up (pik-tteul) EGO 0.65% becomes impossible
   * - Rate-up EGO gets FULL 1.3% (doubled from 0.65%)
   * - ID rates also increase slightly (3★: 2.9% → 3.0%)
   * - 1★ absorbs remaining probability to maintain 100% total
   */
  ALL_EGO_COLLECTED: {
    THREE_STAR_ID: 0.03,
    TWO_STAR_ID: 0.13,
    ONE_STAR_ID: 0.84,
    EGO: 0.0, // No pik-tteul possible; rate-up handled separately
    ANNOUNCER: 0,
  },
  /** Rates with both All EGO + Announcer */
  ALL_EGO_WITH_ANNOUNCER: {
    THREE_STAR_ID: 0.03,
    TWO_STAR_ID: 0.13,
    ONE_STAR_ID: 0.827,
    EGO: 0.0, // No pik-tteul possible; rate-up handled separately
    ANNOUNCER: 0.013,
  },
  /**
   * Rate-up totals (split among featured items)
   *
   * For EGO:
   * - Standard: 0.65% (half of 1.3% EGO pool)
   * - All EGO Collected: 1.3% (full EGO pool, no pik-tteul)
   */
  RATE_UP: {
    THREE_STAR_ID: 0.0145,
    /** Standard rate-up EGO (when pik-tteul exists) */
    EGO: 0.0065,
    /** Rate-up EGO when all EGO collected (no pik-tteul, full pool) */
    EGO_ALL_COLLECTED: 0.013,
    ANNOUNCER: 0.013,
  },
  /** Pity system - guaranteed at this pull count */
  PITY_PULLS: 200,
  /** Lunacy cost per single pull */
  LUNACY_PER_PULL: 130,
} as const

/**
 * Extraction rate table type - base rates for each item type
 * Values are 0-1 probabilities
 */
export interface ExtractionRateTable {
  THREE_STAR_ID: number
  TWO_STAR_ID: number
  ONE_STAR_ID: number
  EGO: number
  ANNOUNCER: number
}
