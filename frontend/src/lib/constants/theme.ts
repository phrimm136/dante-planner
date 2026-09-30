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

export const IMPORTANCE_DECORATION = {
  ICON_ALPHA: { '1': 0.2, '2': 0.4, '3': 0.5 },
  FLICKER_PERIOD_S: { '1': 3.0, '2': 2.0, '3': 0.8 },
  FLICKER_ALPHA: { '1': [0.7, 1.0], '2': [0.7, 1.0], '3': [0.5, 1.0] },
  STRIPE_BLINK_ALPHA: { '1': [0.3, 0.4], '2': [0.4, 0.6], '3': [0.5, 0.8] },
  PASSIVE_OUTLINE_ALPHA: { '1': [0, 0.3], '2': [0.2, 0.5], '3': [0.5, 1.0] },
  GLOW_MIN_LEVEL: 2,
} as const

export const CLIENT_TEXT_SCALE = { FONT_UNITS: 40, FONT_REM: 0.875 } as const

export const COIN_CLIENT_WIDTH = 65

export const COIN_CLIENT_SPACING = -10

export const IMPORTANCE_BAND_UNITS = { THICKNESS: 52.168, TEXTURE_WIDTH: 1304.2 } as const

export const IMPORTANCE_PASSIVE_OVERHANG_UNITS = 40

export const IMPORTANCE_ICON_UNITS = {
  SIZE: 200,
  TOP: { skill: 37, passive: 37 },
  RIGHT: { skill: { unlock: 86, base: 286 }, passive: 0 },
} as const

export const SKILL_UPPER_UNITS = {
  HEIGHT: 270,
  CARD_TOP: 15,
  ICON: { LEFT: 65.4, TOP: 23.26, SIZE: 252 },
  ATTACK_ICON: { CENTER_X: 184.4, CENTER_Y: 218.7, SIZE: 62.4, GLOW: 216 },
  COIN_SCALE: { CENTER_X: 181.8, CENTER_Y: 50.9, FONT: 60 },
  COIN_BG: { CENTER_X: 185.7, CENTER_Y: 54, WIDTH: 97.68, HEIGHT: 87.96 },
  BASE_POWER: { RIGHT_EDGE_X: 104, CENTER_Y: 142.7 },
  COINS: { LEFT: 271.19, TOP: 14.3 },
  NAME_PLATE: {
    LEFT: 273.6,
    TOP: 76.3,
    HEIGHT: 69.33,
    RIGHT_MIN: 100,
    FONT: 45,
    PAD_LEFT: 20,
    PAD_RIGHT: 50,
    PAD_BOTTOM: 10,
  },
  LEVEL: {
    ICON_LEFT: 277.7,
    ICON_TOP: 150,
    ICON_WIDTH: 76.16,
    ICON_HEIGHT: 86.3,
    TEXT_LEFT: 364.7,
    TEXT_CENTER_Y: 192,
    FONT: 75,
    TEXT_MAX_WIDTH: 83.779,
    UNDERLINE: 5,
  },
  TARGET: {
    LEFT: 455.6,
    TOP: 174.1,
    WIDTH: 181.04,
    HEIGHT: 38,
    FONT: 33,
    COUNT_LEFT: 643.86,
    COUNT_WIDTH: 377.4,
    COUNT_FONT: 35.3,
  },
} as const

export const SKILL_LOWER_UNITS = {
  INSET: 30,
  COIN_LIST: {
    PAD_TOP: 12.4,
    PAD_BOTTOM: 42.6,
    SLOT_MIN: 92.159,
    ICON_SIZE: 92.159,
    ICON_LEFT: -5.2,
    TEXT_LEFT: 104.8,
    TEXT_RIGHT: 22,
    TEXT_TOP: 31.4,
    TEXT_BOTTOM: 8.6,
  },
  FLAVOR: { FONT: 33, TOP: 15, BOTTOM: 35 },
  BOTTOM: { plain: 15, importance: 45 },
} as const

export const SKILL_LOCK_UNITS = {
  RIGHT: 49,
  BOTTOM: 83,
  WIDTH: 250,
  HEIGHT: 355,
  ALPHA: 0.275,
} as const

export const SKILL_TAB_UNITS = { GAP: 15, LINE: 5, BOTTOM: 50 } as const

export const PASSIVE_TAB_UNITS = {
  TOP: 15,
  GAP: 80,
  LINE: 5,
  BOTTOM: 30,
  HEADER: { LEFT: 15, WIDTH: 379, HEIGHT: 79, FONT: 45, AFTER: 40, SUPPORT_BEFORE: 80 },
} as const

export const PASSIVE_BOX_UNITS = {
  MIN_HEIGHT: 300,
  PLATE: { LEFT: 28, HEIGHT: 72.74, RIGHT_MIN: 372, FONT: 40, PAD_LEFT: 30, PAD_RIGHT: 100 },
  HEADER_GAP: 20,
  SIN_ROW: {
    LEFT: 20,
    HEIGHT: 77,
    ITEM_MIN: 170,
    ICON: 70,
    TIMES: 40,
    COUNT_FONT: 70,
    LABEL_FONT: 50,
    LABEL_LEFT: 20,
  },
  INFO: { LEFT: 27, RIGHT: 20 },
  FLAVOR: { LEFT: 27, RIGHT: 20, TOP: 47, BOTTOM: 35, FONT: 33 },
  LOCK: { RIGHT: 20, BOTTOM: 20, WIDTH: 146.7, HEIGHT: 195.1, ALPHA: 0.298 },
} as const

export const SANITY_UNITS = {
  PANIC: {
    MIN_HEIGHT: 450,
    TAG_BG: { LEFT: 12.5, TOP: 36, WIDTH: 275, HEIGHT: 72.8 },
    TAG_TEXT: { LEFT: 30, TOP: 45, WIDTH: 240, HEIGHT: 60, FONT: 45 },
    ICON: { LEFT: 48.2, TOP: 135, WIDTH: 193.6, HEIGHT: 180.1 },
    NAME: { LEFT: 9.5, TOP: 325.6, WIDTH: 275, HEIGHT: 105.3, FONT: 51.2, PAD_X: 20, PAD_TOP: 5 },
    DESC: { LEFT: 300, SHIFT: 17, PAD_TOP: 120 },
  },
  SECTION_GAP: 50,
  CONDITION: {
    MIN_HEIGHT: 300,
    TAG: {
      LEFT: 12.5,
      HEIGHT: { add: 75, min: 74 },
      MIN_WIDTH: 405,
      MAX_WIDTH: 575,
      PAD_X: 25,
      FONT: 40,
    },
    DESC: { TOP: 100, add: { LEFT: 10.48, RIGHT: 0 }, min: { LEFT: 13.99, RIGHT: 30.99 } },
    ITEM_GAP: 33.43,
    ICON: {
      RIGHT: { add: 50, min: 49.98 },
      WIDTH: 280,
      HEIGHT: { add: 286.96, min: 287 },
      ALPHA: 0.275,
    },
  },
} as const

export const IMPORTANCE_PLATE_GLOW_UNITS = 6

export const PASSIVE_PANE_BLEED_REM = 1

export const IMPORTANCE_STRIPE_BLINK_SPEED = 2

export const IMPORTANCE_STRIPE_BLINK_PERIOD_S = (2 * Math.PI) / IMPORTANCE_STRIPE_BLINK_SPEED

export const IMPORTANCE_STRIPE_SCROLL_S = 10

export const ORANGE_MARK_STYLE = {
  color: '#ff6000',
  backgroundColor: '#ff000040',
  fontWeight: 'bold',
  textDecoration: 'underline',
} as const
