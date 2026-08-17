/**
 * Layout geometry and the shared class-name vocabulary for page chrome.
 */

/**
 * Filter sidebar width in pixels (desktop view)
 * Used by FilterSidebar component for consistent layout
 */
export const FILTER_SIDEBAR_WIDTH = 280

/**
 * Frames the auto-sizing text components keep retrying a measurement that
 * returned a zero-sized box before giving up.
 */
export const AUTOSIZE_MEASURE_RETRY_FRAMES = 60

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
 * Detail Page Layout Constants
 * Used for modular two-column detail page layout (Identity, EGO, EGO Gift)
 */
export const DETAIL_PAGE = {
  /** Desktop breakpoint in pixels (1024px = lg: in Tailwind) */
  BREAKPOINT_LG: 1024,
  /** Column ratio: 4:6 (left:right) using 10-column grid */
  COLUMN_LEFT: 'lg:col-span-4',
  COLUMN_RIGHT: 'lg:col-span-6',
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
  /** Tailwind md: breakpoint in pixels */
  MD_BREAKPOINT: 768,
  /** Tailwind sm: breakpoint in pixels */
  SM_BREAKPOINT: 640,
  /** Mobile scale factors for different card types */
  MOBILE_SCALE: {
    /** Standard scale for most cards (80%) */
    STANDARD: 0.8,
    /** Dense scale for compact layouts (60%) */
    DENSE: 0.6,
  },
} as const

const EGO_INFO_ICON_SLOT = 26
const EGO_INFO_NAME_SLOT = 76

/**
 * EGO card info panel row geometry in pixels.
 * `WIDTH` is derived so the track always equals the sum of its slots.
 */
export const EGO_CARD_INFO_ROW = {
  /** Rank icon slot (left) and tier icon slot (right) */
  ICON_SLOT: EGO_INFO_ICON_SLOT,
  /** EGO name slot, and the width EGOName measures its text against */
  NAME_SLOT: EGO_INFO_NAME_SLOT,
  /** Full row track */
  WIDTH: EGO_INFO_ICON_SLOT * 2 + EGO_INFO_NAME_SLOT,
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
 * Delay step (ms) between staggered entrance animations, by how much
 * separation the sequence needs.
 */
export const STAGGER_STEP_MS = {
  TIGHT: 40,
  NORMAL: 60,
  LOOSE: 80,
} as const
