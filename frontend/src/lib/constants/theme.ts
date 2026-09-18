export const SANITY_INDICATOR_COLORS = {
  INCREMENT: '#80c9ff',
  DECREMENT: '#fe4b48',
  INCREMENT_BORDER: 'rgba(128, 201, 255, 0.5)',
  DECREMENT_BORDER: 'rgba(254, 75, 72, 0.5)',
} as const

export const PASSIVE_INDICATOR_COLORS = {
  TEXT: '#c9a86c',
  BORDER: 'rgba(201, 168, 108, 0.5)',
} as const

export const DISCORD_BLURPLE = '#5865F2'

export const WARNING_CALLOUT_STYLES = {
  panel:
    'bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-md p-3',
  heading: 'text-sm font-medium text-yellow-800 dark:text-yellow-200 mb-2',
  list: 'text-sm text-yellow-700 dark:text-yellow-300 list-disc list-inside',
} as const

export const STATUS_TEXT_COLORS = {
  DANGER: 'text-red-500',
  WARNING: 'text-orange-500',
  SUCCESS: 'text-green-500',
  INFO: 'text-blue-500',
} as const

export const STAR_ICON_CLASS = 'fill-yellow-400 text-yellow-400'

export const ACCENT_COLORS = {
  ENHANCED: '#f8c200',
  TIER: '#fcba03',
  SUCCESS: '#00ff9c',
  FAILURE: '#e30000',
} as const

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

const DIFFICULTY_COLOR_BY_LABEL = {
  NORMAL: '#ffd700',
  HARD: '#ff8c00',
  'INFINITY MIRROR': '#dc070c',
  'EXTREME MIRROR': '#ffffff',
} as const

export const DIFFICULTY_COLORS: Record<string, string> = DIFFICULTY_COLOR_BY_LABEL

export const MD_CATEGORY_COLORS: Record<string, string> = {
  '5F': DIFFICULTY_COLOR_BY_LABEL.HARD,
  '10F': DIFFICULTY_COLOR_BY_LABEL['INFINITY MIRROR'],
  '15F': DIFFICULTY_COLOR_BY_LABEL['EXTREME MIRROR'],
} as const

export const MD_CATEGORY_TEXT_COLORS: Record<string, string> = {
  '5F': '#ffffff',
  '10F': '#ffffff',
  '15F': '#000000',
} as const

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

export const SECTION_STYLES = {
  TEXT: {
    pageTitle: 'text-2xl font-bold',
    header: 'text-xl font-semibold',
    subHeader: 'text-lg font-medium',
    sectionTitle: 'text-lg font-semibold',
    label: 'text-sm font-medium',
    caption: 'text-sm text-muted-foreground',
    captionSmall: 'text-xs text-muted-foreground',
    muted: 'text-muted-foreground',
  },

  SPACING: {
    section: 'space-y-6',
    content: 'space-y-4',
    elements: 'space-y-2',
    gap: 'gap-4',
  },

  LAYOUT: {
    page: 'container mx-auto p-8',
    row: 'flex items-center gap-2',
    rowTight: 'flex items-center gap-1',
    rowBetween: 'flex items-center justify-between',
    wrap: 'flex flex-wrap gap-2',
    column: 'flex flex-col gap-4',
  },

  container: 'bg-card border border-border rounded-md p-6',

  panel: 'bg-muted border border-border rounded-md p-6',
} as const

export const DIFFICULTY_BADGE_STYLES = {
  HARD: 'bg-orange-500/20 text-orange-500',
  EXTREME: 'bg-red-500/20 text-red-500',
} as const
