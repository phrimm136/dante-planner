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
 * Tailwind classes for a warning callout: the panel, its heading and its list body.
 */
export const WARNING_CALLOUT_STYLES = {
  panel:
    'bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-md p-3',
  heading: 'text-sm font-medium text-yellow-800 dark:text-yellow-200 mb-2',
  list: 'text-sm text-yellow-700 dark:text-yellow-300 list-disc list-inside',
} as const

/**
 * Tailwind text colours that carry the severity of a moderation state or action.
 */
export const STATUS_TEXT_COLORS = {
  /** Irreversible or destructive */
  DANGER: 'text-red-500',
  /** Reversible restriction */
  WARNING: 'text-orange-500',
  /** Access restored */
  SUCCESS: 'text-green-500',
  /** Role change */
  INFO: 'text-blue-500',
} as const

/** Filled star marking a planner above the recommendation threshold. */
export const STAR_ICON_CLASS = 'fill-yellow-400 text-yellow-400'

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
export const SINNER_COLORS: Record<string, string> = {
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
 * Skill frame glow colors for coin power backgrounds
 * Extracted from skill frame BG images - these are bright neon colors
 * Used for visual prominence in coin power display
 */
export const SKILL_FRAME_GLOW_COLORS: Record<string, string> = {
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
 * Sin affinity colors from game ChoiceEventEffect color tags
 */
export const AFFINITY_COLORS: Record<string, string> = {
  CRIMSON: '#a0392b',
  SCARLET: '#bb521f',
  AMBER: '#e48801',
  SHAMROCK: '#61822b',
  AZURE: '#306471',
  INDIGO: '#185188',
  VIOLET: '#7d4e94',
}

/**
 * Difficulty colors for indicator display, keyed by difficulty label.
 * NORMAL: yellow, HARD: orange, INFINITY: red, EXTREME: white
 */
export const DIFFICULTY_COLORS: Record<string, string> = {
  NORMAL: '#ffd700',
  HARD: '#ff8c00',
  'INFINITY MIRROR': '#dc070c',
  'EXTREME MIRROR': '#ffffff',
} as const

/**
 * MD category background colors aligned with difficulty colors
 * 5F uses HARD orange, 10F uses INFINITY red, 15F uses EXTREME white
 */
export const MD_CATEGORY_COLORS: Record<string, string> = {
  '5F': DIFFICULTY_COLORS.HARD,
  '10F': DIFFICULTY_COLORS['INFINITY MIRROR'],
  '15F': DIFFICULTY_COLORS['EXTREME MIRROR'],
} as const

/**
 * Text colors for category badges (white for 5F/10F, black for 15F)
 */
export const MD_CATEGORY_TEXT_COLORS: Record<string, string> = {
  '5F': '#ffffff',
  '10F': '#ffffff',
  '15F': '#000000',
} as const

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
 * Flavor text color for skill / status-effect lore lines.
 * Mirrors in-game `*FlavorGlow` TMP material face color used by
 * `[Text]SkillInfoFlavor` and `[Text]BuffFlavor` GameObjects.
 */
export const FLAVOR_TEXT_COLOR = '#a16a3b'

/**
 * Section Styling Tokens
 * Consistent styling for complex pages like PlannerMDNewPage
 * Import: import { SECTION_STYLES } from '@/lib/constants'
 */
export const SECTION_STYLES = {
  /** Typography classes for section hierarchy */
  TEXT: {
    /** Page-level h1 */
    pageTitle: 'text-2xl font-bold',
    /** Major section titles - use for all h2 section headers */
    header: 'text-xl font-semibold',
    /** Subsection titles - use for nested headers */
    subHeader: 'text-lg font-medium',
    /** Subsection titles that carry the same weight as a section header */
    sectionTitle: 'text-lg font-semibold',
    /** Form field labels */
    label: 'text-sm font-medium',
    /** Helper text, counts, hints */
    caption: 'text-sm text-muted-foreground',
    /** Dense helper text (badges, footnotes) */
    captionSmall: 'text-xs text-muted-foreground',
    /** De-emphasised body text at the inherited size */
    muted: 'text-muted-foreground',
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

  /** Flex arrangements repeated across pages */
  LAYOUT: {
    /** Centred page shell */
    page: 'container mx-auto p-8',
    /** Inline row of related controls */
    row: 'flex items-center gap-2',
    /** Inline row with minimal separation (icon + label) */
    rowTight: 'flex items-center gap-1',
    /** Header row with leading content and trailing actions */
    rowBetween: 'flex items-center justify-between',
    /** Wrapping row of chips/badges */
    wrap: 'flex flex-wrap gap-2',
    /** Stacked block */
    column: 'flex flex-col gap-4',
  },

  /** Section container background with border */
  container: 'bg-card border border-border rounded-md p-6',

  /** Recessed container for read-only or secondary content */
  panel: 'bg-muted border border-border rounded-md p-6',
} as const

/**
 * Difficulty badge styles for EGO Gift metadata display
 * Used in EGOGiftMetadata component for Hard/Extreme badges
 */
export const DIFFICULTY_BADGE_STYLES = {
  HARD: 'bg-orange-500/20 text-orange-500',
  EXTREME: 'bg-red-500/20 text-red-500',
} as const
