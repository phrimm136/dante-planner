/**
 * Application configuration constants (timings, byte limits, UI tokens)
 */

/**
 * Search bar debounce delay in milliseconds
 */
export const SEARCH_DEBOUNCE_DELAY = 100

/**
 * Banner carousel auto-advance interval in milliseconds
 */
export const BANNER_CAROUSEL_INTERVAL = 5000

/**
 * Stale time for static JSON data queries in milliseconds (7 days).
 * Static data only changes on deploy, so it is effectively immutable at runtime.
 */
export const STATIC_DATA_STALE_TIME = 7 * 24 * 60 * 60 * 1000

/**
 * Discord server invite URL
 */
export const DISCORD_INVITE_URL = 'https://discord.gg/DMGGsP2EWS'

/**
 * Discord brand color (Blurple)
 */
export const DISCORD_BLURPLE = '#5865F2'

/**
 * Filter sidebar width in pixels (desktop view)
 * Used by FilterSidebar component for consistent layout
 */
export const FILTER_SIDEBAR_WIDTH = 280

/**
 * Recommended planner threshold (upvotes)
 * Planners with upvotes >= this value show star indicator
 */
export const RECOMMENDED_THRESHOLD = 10

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
 * Planner configuration for version management
 * Authoritative source: scripts/sync-planner-config.py
 * Also kept in backend application.properties for server-side validation
 *
 * @see PlannerConfigSchema for runtime validation
 */
export const PLANNER_CONFIG = {
  schemaVersion: 2,
  mdCurrentVersion: 7,
  mdAvailableVersions: [6, 7],
  rrAvailableVersions: [1, 5],
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
 * Triggers save 1 second after user stops making changes
 */
export const AUTO_SAVE_DEBOUNCE_MS = 1000

/**
 * Current planner schema version for migration support
 * Increment when planner data structure changes
 */
export const PLANNER_SCHEMA_VERSION = 2

/**
 * Current export file format version for migration support
 * Increment when export envelope structure changes
 */
export const EXPORT_VERSION = 1

/**
 * File extension for planner export files
 */
export const EXPORT_FILE_EXTENSION = '.danteplanner'

/**
 * Maximum file size for import in bytes (10MB)
 * Prevents memory exhaustion from large malicious files
 */
export const EXPORT_MAX_FILE_SIZE = 10 * 1024 * 1024

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
export type PlannerStorageKey = (typeof PLANNER_STORAGE_KEYS)[keyof typeof PLANNER_STORAGE_KEYS]

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
    /** KeywordCard: 96px (matches EGO gift card width) */
    KEYWORD: 96,
    /** PlannerCard: 280px for adequate text/metadata space */
    PLANNER: 280,
    /** StartBuffCard: w-68 (272px) */
    START_BUFF: 272,
    /** ThemePackCard: w-60 (240px) */
    THEME_PACK: 240,
    /** Keyword icon container: w-16 (64px) */
    KEYWORD_ICON: 64,
    /** SinnerSkillCard: p-2 (8px) + image (96px) + p-2 (8px) = 112px */
    SINNER_SKILL: 112,
    /** SkillImageSimple: w-32 h-32 (128px) */
    SKILL_IMAGE: 128,
    /** SkillExchangePane: skill (128px) + gap (8px) + arrow (64px) + gap (8px) + skill (128px) + padding (16px) */
    SKILL_EXCHANGE: 352,
    /** AbEventCard: ~3 columns on 1024px desktop (title above wide landscape image) */
    AB_EVENT: 308,
    /** CompactIdentityRow: ~1440px dialog - 48px padding = 1392px, 12 items with 8px gaps (11 × 8 = 88px) = 1304px / 12 ≈ 108px, rounded to 96px for comfortable spacing */
    COMPACT_IDENTITY: 96,
  },
  /** Card heights in pixels - matches actual card component dimensions */
  HEIGHT: {
    /** IdentityCard: h-56 (224px) */
    IDENTITY: 224,
    /** EGOCard: h-48 (196px) */
    EGO: 196,
    /** EGOGiftCard: 96px */
    EGO_GIFT: 96,
    /** KeywordCard: 120px (icon + name label) */
    KEYWORD: 120,
    /** PlannerCard: varies by content */
    PLANNER: 0,
    /** DeckBuilderCard: identity (224px) + skill row (28px) + ego row (28px) + gaps */
    DECK: 290,
    /** StartBuffCard: w-68 aspect ratio */
    START_BUFF: 320,
    /** ThemePackCard: h-104 (416px) */
    THEME_PACK: 416,
    /** AbEventCard: title (~30px) + 3:2 image (~205px) = ~235px */
    AB_EVENT: 235,
    /** Keyword icon container: h-16 (64px) */
    KEYWORD_ICON: 64,
    /** SinnerSkillCard: p-2 (8px) + image (96px) + gap-1 (4px) + skill row (28px) + p-2 (8px) = 144px */
    SINNER_SKILL: 144,
    /** SkillImageSimple: w-32 h-32 (128px) */
    SKILL_IMAGE: 128,
    /** SkillExchangePane: p-2 (8px) + skill image (128px) + p-2 (8px) = 144px */
    SKILL_EXCHANGE: 144,
    /** HomeIdentityCard/HomeEGOCard: h-28 (112px) + gap-1 (4px) + icons (20px) = ~136px */
    HOME_CARD: 136,
    /** CompactIdentityRow: square portrait (96px) + gap (4px) + skill row (28px) = 128px */
    COMPACT_IDENTITY: 128,
  },
  /** Default gap between cards in pixels (gap-4 = 16px) */
  DEFAULT_GAP: 16,
  /** Breakpoint for desktop layout in pixels (matches Tailwind lg: breakpoint) */
  LG_BREAKPOINT: 1024,
  /** Mobile scale factors for different card types */
  MOBILE_SCALE: {
    /** Standard scale for most cards (80%) */
    STANDARD: 0.8,
    /** Dense scale for compact layouts (60%) */
    DENSE: 0.6,
  },
} as const

/**
 * Card width type for ResponsiveCardGrid
 */
export type CardGridWidth = (typeof CARD_GRID.WIDTH)[keyof typeof CARD_GRID.WIDTH]

/**
 * Start Buff card dimensions keyed by MD version.
 * Each entry must match the actual pane image size used in the card component.
 */
export const START_BUFF_CARD_SIZE: Record<number, { width: number; height: number }> = {
  /** MD6: w-68 h-80 */
  6: { width: 272, height: 320 },
  /** MD7: w-68 h-80 */
  7: { width: 272, height: 320 },
}

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
export type PlannerListSortOption = (typeof PLANNER_LIST.SORT_OPTIONS)[number]

/**
 * Boolean filter options (N/Y) for toggle filters like fusioned, theme pack exclusive
 */
export const BOOLEAN_FILTER_OPTIONS = ['N', 'Y'] as const

/**
 * Difficulty badge styles for EGO Gift metadata display
 * Used in EGOGiftMetadata component for Hard/Extreme badges
 */
export const DIFFICULTY_BADGE_STYLES = {
  HARD: 'bg-orange-500/20 text-orange-500',
  EXTREME: 'bg-red-500/20 text-red-500',
} as const

/**
 * i18n language code to BCP 47 locale mapping
 * Used for date/time formatting with toLocaleString()
 */
export const I18N_LOCALE_MAP: Record<string, string> = {
  KR: 'ko-KR',
  JP: 'ja-JP',
  CN: 'zh-CN',
  EN: 'en-US',
} as const

/**
 * Comment System Constants
 * Used by CommentEditor and CommentSection components
 */

/**
 * Maximum character count for comments (matches backend validation)
 */
export const COMMENT_MAX_CHARS = 10000

/**
 * Comment thread indentation in pixels per depth level
 */
export const COMMENT_INDENT_PER_LEVEL = 2

/**
 * Maximum visual depth for comment indentation on mobile (< lg breakpoint)
 * Comments deeper than this still exist but don't indent further
 */
export const COMMENT_MAX_VISUAL_DEPTH_MOBILE = 2

/**
 * Maximum visual depth for comment indentation on desktop (>= lg breakpoint)
 */
export const COMMENT_MAX_VISUAL_DEPTH_DESKTOP = 10

/**
 * SSE Connection Constants
 * Used by useSseEngine hook for reconnection and token management
 */
export const SSE_CONNECTION = {
  /** Initial delay before first connection to let cookies settle (ms) */
  INITIAL_DELAY: 500,
  /** Base delay for reconnection in ms */
  BASE_DELAY: 1000,
  /** Maximum delay for reconnection in ms */
  MAX_DELAY: 8000,
  /** Maximum reconnection attempts before giving up */
  MAX_ATTEMPTS: 10,
  /** Time threshold (14 min) after which token is considered potentially stale */
  TOKEN_STALE_THRESHOLD: 14 * 60 * 1000,
  /** Proactive reconnect interval (13 min) - reconnect BEFORE token expires */
  PROACTIVE_RECONNECT_INTERVAL: 13 * 60 * 1000,
  /** Idle timeout (5 min) after which reconnect attempts reset */
  IDLE_RESET_TIMEOUT: 5 * 60 * 1000,
  /** Random reconnect jitter ceiling (0–5s) to avoid thundering-herd reconnects */
  MAX_JITTER: 5 * 1000,
} as const

/**
 * SSE Event Names
 * Used by useSseEngine / useAppSse for type-safe event handling
 */
export const SSE_EVENTS = {
  CONNECTED: 'connected',
  PLANNER_UPDATE: 'planner-update',
  SYNC_PLANNER: 'sync:planner',
  NOTIFY_COMMENT: 'notify:comment',
  NOTIFY_RECOMMENDED: 'notify:recommended',
  NOTIFY_PUBLISHED: 'notify:published',
} as const

/**
 * Progressive Reveal Constants
 * Used by useProgressiveReveal/useProgressiveCount hooks for staggered rendering
 */
export const PROGRESSIVE_REVEAL = {
  /** Delay between each section reveal (ms) */
  STAGGER_DELAY: 50,
  /** Cards revealed per animation frame in list grids */
  CARD_BATCH: 10,
  /** Lightweight keyword cards revealed per animation frame */
  KEYWORD_CARD_BATCH: 50,
} as const

/**
 * Announcement Section Constants
 * Used by AnnouncementContent on the home page
 */

/**
 * Number of announcements shown as preview on the home page
 */
export const ANNOUNCEMENT_PREVIEW_COUNT = 5

/**
 * Contact email shown in the footer and legal pages
 */
export const CONTACT_EMAIL = 'contact@dante-planner.com'
