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
 * Semantic colors matching game UI conventions
 */
export const SANITY_INDICATOR_COLORS = {
  /** Red - Panic type indicator */
  PANIC: '#ef4444',
  /** Orange - Sanity increment condition */
  INCREMENT: '#f97316',
  /** Yellow - Sanity decrement condition */
  DECREMENT: '#eab308',
} as const

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
  container: 'bg-muted border border-border rounded-md p-6',

  /** Standard responsive grid (2→3→4→6 columns) */
  grid: 'grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6',

  /** Dense responsive grid (2→3→5 columns) - for compact layouts like StartBuff */
  gridDense: 'grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5',

  /** Semantic highlight classes for selection states (gold ring, theme-aware) */
  highlight: {
    /** Hover state - apply directly to className */
    hover: 'hover:ring-2 hover:ring-ring',
    /** Selected state - use with conditional: isSelected && SECTION_STYLES.highlight.selected */
    selected: 'ring-2 ring-ring bg-ring/10',
  },
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
 * Maximum number of draft planners for guest users
 * Oldest drafts are auto-deleted when limit is exceeded
 */
export const MAX_GUEST_DRAFTS = 3

/**
 * Current planner schema version for migration support
 * Increment when planner data structure changes
 */
export const PLANNER_SCHEMA_VERSION = 1

/**
 * IndexedDB storage key prefixes for planner data
 * All planner-related keys use these prefixes for namespacing
 */
export const PLANNER_STORAGE_KEYS = {
  /** Prefix for draft planners: drafts:{deviceId}:{plannerId} */
  DRAFTS: 'drafts',
  /** Prefix for saved planners: saved:{deviceId}:{plannerId} */
  SAVED: 'saved',
  /** Key for tracking current draft being edited */
  CURRENT_DRAFT_ID: 'currentDraftId',
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
export const SEASONS = Object.keys(seasonsJson).map(Number) as const

/**
 * Season type derived from SEASONS array
 */
export type Season = (typeof SEASONS)[number]

/**
 * Association identifiers for identity filtering
 * Derived from unitKeywords.json keys (organization/affiliation names)
 */
export const ASSOCIATIONS = Object.keys(unitKeywordsJson) as const

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
