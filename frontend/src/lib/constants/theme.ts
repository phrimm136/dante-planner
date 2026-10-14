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

export const GAME_TEXT_CLASS = 'game-text'

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
  RIGHT: { skill: { unlock: 86, base: 286, partIcon: 486 }, passive: 0 },
} as const

// [Rect]AbPartsIconGroup pos (-100, -30) from the box top-right; five [Image]AbPartsIcon 200x200 at
// scale 1.3, columns x 300 / 100, rows y -100 / -300 / -500, colour #ebcaa2 alpha 0.275.
// UpdateLayout floors the box at min(n, 3) * 200 + 60.
export const PART_ICON_UNITS = {
  SIZE: 260,
  RIGHT: [70, 270],
  ROW_PITCH: 200,
  ROWS_PER_COLUMN: 3,
  MAX_ICONS: 5,
  BOX_FLOOR_PAD: 60,
  COLOR: '#ebcaa2',
  ALPHA: 0.275,
} as const

export const NAME_PLATE_UNITS = {
  SKILL: {
    FONT: 45,
    MIN_FONT: 0,
    PAD_LEFT: 20,
    PAD_RIGHT: 50,
    TEXT_HEIGHT: 60,
    // [Tmpro]SkillName margin B10: TMP Geo centres the ink 5u above the plate centre.
    TEXT_MARGIN_BOTTOM: 10,
    // [Image]SkillNameBg height; BattleUI_CoinPopup_skillName 148.5x25.5 px drawn at 2.5u/px
    // (ppu 50, ppuMul 0.8), borders L 4.5 / R 49.5 px, shipped as its 149x26 whole-texel cover.
    HEIGHT: 69.33,
    // [Script]SkillNameRect: (1304.2 - 273.6) area width - _rightPadding 100.
    TEXT_WIDTH_MAX: 930.6,
    SLICE: {
      LEFT: 11.25,
      RIGHT: 123.75,
      SPRITE_WIDTH: 371.25,
      IMAGE_WIDTH: 372.5,
      SPRITE_TEXELS_HEIGHT: 25.5,
      IMAGE_TEXELS_HEIGHT: 26,
    },
  },
  PASSIVE: {
    FONT: 40,
    MIN_FONT: 25,
    PAD_LEFT: 30,
    PAD_RIGHT: 100,
    TEXT_HEIGHT: 52.74,
    // [Script]SkillNameBg height; New_MainUI_PersonalityList_2_46 387x54 px at 1u/px (ppu 100),
    // borders L 112 / R 171 px.
    HEIGHT: 72.74,
    // UnitInformationNewPassiveBox.SetData sets the SkillNameBg color to
    // UIColorManager._uiTextDarkColor (+0xe0) unless an importance level colours it.
    TINT: '#a16a3b',
    // [Script]SkillNameBg rect_area is UnitInformationPassiveContent: 1304.2 - _rightPadding 400.
    TEXT_WIDTH_MAX: 904.2,
    SLICE: {
      LEFT: 112,
      RIGHT: 171,
      SPRITE_WIDTH: 387,
      IMAGE_WIDTH: 387,
      SPRITE_TEXELS_HEIGHT: 54,
      IMAGE_TEXELS_HEIGHT: 54,
    },
  },
  LETTER_SPACING_EM: -0.02,
  WORD_SPACING_EM: -0.05,
} as const

/**
 * Downward shift of the attack-weight label, in em: TMP Geo centres the label's glyph box
 * ([Text]TargetNumLabel face SDF glyph table) where CSS centres the web face's content area.
 * KR SCDream5 SDF 0.3945 vs SCDream5.otf hhea 0.9/0.251 (0.3245); EN Pretendard-Regular SDF
 * 0.2600 vs Pretendard typo 0.9517/0.2412 (0.3552); JP HigashiOme-Gothic-C-1 0.3729 vs
 * HigashiOme hhea 0.9521/0.2368 (0.3577). CN has no client label face.
 */
export const TARGET_LABEL_GEO_SHIFT_EM: Record<string, number> = {
  KR: 0.07,
  EN: -0.0952,
  JP: 0.0153,
} as const

export const SKILL_UPPER_UNITS = {
  HEIGHT: 270,
  CARD_TOP: 15,
  ICON: { LEFT: 65.4, TOP: 23.26, SIZE: 252 },
  ATTACK_ICON: { CENTER_X: 184.4, CENTER_Y: 218.7, SIZE: 62.4 },
  COIN_SCALE: { CENTER_X: 181.8, CENTER_Y: 50.9, FONT: 60 },
  COIN_BG: { CENTER_X: 185.7, CENTER_Y: 54, WIDTH: 97.68, SPRITE_WIDTH: 73.5, SPRITE_HEIGHT: 45.5 },
  COIN_SCALE_LETTER_SPACING_EM: -0.06,
  BASE_POWER: { RIGHT_EDGE_X: 104, CENTER_Y: 142.7, FONT: 102.54 },
  NUMERIC_FACE: {
    FAMILY: 'var(--font-numerals-ink)',
    // planner-numerals.ttf digit ink top 700 / 1000; globals.css ascent-override 70%.
    DIGIT_HEIGHT_EM: 0.7,
  },
  COINS: { LEFT: 271.19, TOP: 14.3 },
  NAME_PLATE: { LEFT: 273.6, CENTER_Y: 111.3, RIGHT_MIN: 100 },
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
    // [Text]TargetNumCount pos (7.221, 4.3), sizeDelta (377.416, 17.528), margin top 6,
    // auto size 15-50, charSp 5.
    COUNT_LEFT: 643.864,
    COUNT_WIDTH: 377.416,
    COUNT_POS_Y: 4.3,
    COUNT_HEIGHT_DELTA: 17.528,
    COUNT_MARGIN_TOP: 6,
    COUNT_FONT_MAX: 50,
    COUNT_FONT_MIN: 15,
    COUNT_LETTER_SPACING_EM: 0.05,
    // KOTRA_BOLD SDF ascent 47.314 / descent 19.922 at 51pt; U+25A0 advance 43.078.
    COUNT_FACE_EM: { ASCENT: 0.927734, DESCENT: 0.390625, SQUARE_ADVANCE: 0.844669 },
    COUNT_GLYPH: '■',
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

export const SKILL_TAB_UNITS = { GAP: 15, LINE: 5, EDGE: 15 } as const

export const PASSIVE_TAB_UNITS = {
  TOP: 15,
  GAP: 80,
  LINE: 5,
  BOTTOM: 15,
  HEADER: { LEFT: 15, WIDTH: 379, HEIGHT: 79, FONT: 45, AFTER: 40, SUPPORT_BEFORE: 80 },
} as const

export const PASSIVE_BOX_UNITS = {
  MIN_HEIGHT: 300,
  PLATE: { LEFT: 28, RIGHT_MIN: 372 },
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
    TAG_TEXT: {
      LEFT: 30,
      TOP: 45,
      WIDTH: 240,
      HEIGHT: 60,
      FONT: 45,
      MIN_FONT: 18,
      PAD_X: 10,
      LETTER_SPACING_EM: -0.03,
      WORD_SPACING_EM: -0.05,
    },
    ICON: { LEFT: 48.2, TOP: 135, WIDTH: 193.6, HEIGHT: 180.1 },
    NAME: {
      LEFT: 9.5,
      TOP: 325.6,
      WIDTH: 275,
      HEIGHT: 105.274,
      FONT: 55,
      MIN_FONT: 18,
      PAD_X: 20,
      PAD_TOP: 5,
      LETTER_SPACING_EM: -0.03,
      WORD_SPACING_EM: -0.05,
      LINE_SPACING_EM: -0.15,
    },
    DESC: { LEFT: 300, SHIFT: 17, PAD_TOP: 120 },
    FLAVOR: { FONT: 33 },
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
      MIN_FONT: 18,
      LETTER_SPACING_EM: -0.02,
      WORD_SPACING_EM: -0.05,
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

export const IMPORTANCE_STRIPE_BLINK_SPEED = 2

export const IMPORTANCE_STRIPE_BLINK_PERIOD_S = (2 * Math.PI) / IMPORTANCE_STRIPE_BLINK_SPEED

export const IMPORTANCE_STRIPE_SCROLL_S = 10

export const UNIT_PANEL_COLORS = {
  NAME: '#ebcaa2',
  DESCRIPTION: '#e6c69e',
  COIN_TEXT: '#ebcaa2',
  PASSIVE_INFO: '#ebcaa2',
  PANIC_DESC: '#ebcaa2',
  FLAVOR: '#724b2b',
  COIN_SCALE: '#e8b87f',
  BASE_POWER: '#e9b880',
  TARGET_LABEL: '#f8c200',
  LEVEL_UNDERLINE: '#ebcaa2',
  PANIC_TAG: '#ace3ff',
  PANIC_NAME: '#94b4d8',
  PASSIVE_HEADER: '#fcbf67',
  CONDITION_TAG: { add: '#80c9ff', min: '#f94b48' },
} as const

export const UNIT_PANEL_UNDERLAYS_EM = {
  NAME: {
    KR: { dx: 0.047111, dy: 0.047111, dilate: 0.016444 },
    EN: { dx: 0.045778, dy: 0.045778, dilate: 0 },
    JP: { dx: 0.040667, dy: 0.035778, dilate: 0 },
  },
  LEVEL: { dx: 0.034933, dy: 0.034933, dilate: 0 },
  COIN_NUMBER: { dx: 0, dy: 0, dilate: 0.019 },
} as const

export const UNIT_PANEL_UNDERLAY_COLORS = { NAME: '#040001', COIN_NUMBER: '#000000' } as const

export const PANEL_TEXT_FACE = { FIRST_LINE_EM: 1.151, PITCH_EM: 1.194 } as const

export const PANEL_TEXT_NODES = {
  SKILL_DESCRIPTION: { LINE_SPACING_EM: 0.2, LETTER_SPACING_EM: -0.02, WORD_SPACING_EM: -0.05 },
  COIN_TEXT: { LINE_SPACING_EM: 0, LETTER_SPACING_EM: 0, WORD_SPACING_EM: 0 },
  SKILL_FLAVOR: { LINE_SPACING_EM: 0.2, LETTER_SPACING_EM: -0.02, WORD_SPACING_EM: -0.05 },
  PASSIVE_INFO: { LINE_SPACING_EM: 0.2, LETTER_SPACING_EM: -0.02, WORD_SPACING_EM: -0.05 },
  PASSIVE_FLAVOR: { LINE_SPACING_EM: 0.25, LETTER_SPACING_EM: 0, WORD_SPACING_EM: -0.05 },
  PANIC_DESC: { LINE_SPACING_EM: 0.2, LETTER_SPACING_EM: -0.03, WORD_SPACING_EM: -0.05 },
  CONDITION_ADD: { LINE_SPACING_EM: 0.2, LETTER_SPACING_EM: -0.02, WORD_SPACING_EM: -0.05 },
  CONDITION_MIN: { LINE_SPACING_EM: 0.2, LETTER_SPACING_EM: -0.02, WORD_SPACING_EM: -0.1 },
} as const

// Prefab_UnitInformationAbnormalityNameTag/[Image]AbClassRank, in client units.
export const RISK_PLATE_UNITS = {
  WIDTH: 188.46,
  HEIGHT: 80.409,
  // [Text]HazardGrade: anchoredPosition (70, 14), sizeDelta 50x40, font size 40.
  GRADE: { CENTER_X: 70, CENTER_Y: 14, WIDTH: 50, HEIGHT: 40, FONT: 40 },
  // [Text]HazardGrade_NoneClassType: anchoredPosition (58, 8), sizeDelta 65x55, font size 55.
  NONE_CLASS_GRADE: { CENTER_X: 58, CENTER_Y: 8, WIDTH: 65, HEIGHT: 55, FONT: 55 },
  GRADE_FACE: {
    FAMILY: 'var(--font-bebas-tmp)',
    LINE_HEIGHT: 1.2,
    UNDERLAY: { X_EM: 0.023, Y_EM: 0.029, COLOR: '#000' },
  },
} as const

export const RISK_PLATE_GRADE_COLORS = {
  ZAYIN: '#f8c200',
  TETH: '#f8c200',
  HE: '#f8c200',
  WAW: '#ff9600',
  ALEPH: '#ff0000',
  SUPPLEMENT: '#f8c200',
} as const
