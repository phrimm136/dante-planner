/**
 * Layout geometry and the shared class-name vocabulary for page chrome.
 */

/**
 * Filter sidebar width in pixels (desktop view)
 * Used by FilterSidebar component for consistent layout
 */
export const FILTER_SIDEBAR_WIDTH = 280

/**
 * Detail Page Layout Constants
 * Used for modular two-column detail page layout (Identity, EGO, EGO Gift)
 */
export const DETAIL_PAGE = {
  /** Column ratio: 4:6 (left:right) using 10-column grid */
  COLUMN_LEFT: 'lg:col-span-4',
  COLUMN_RIGHT: 'lg:col-span-6',
} as const

/**
 * Exclusive gift icon row under a theme pack card
 * ThemePackExclusiveGifts: size-8 (32px) icons, gap-1 (4px)
 */
export const EXCLUSIVE_GIFT_ICONS = {
  ICON_SIZE: 32,
  GAP: 4,
} as const

/** The share of the desktop width a card takes below the desktop breakpoint. */
export const CARD_MOBILE_SCALE = 0.8 as const

/** The share for dense layouts. */
export const CARD_MOBILE_SCALE_DENSE = 0.6 as const

/** Full size at every width, for grids whose columns do not shrink. */
export const CARD_MOBILE_SCALE_NONE = 1 as const

/** Default gap between cards in pixels (gap-4 = 16px). */
export const CARD_GAP_PX = 16 as const

/** The Tailwind breakpoints a card grid reads, in pixels. */
export const SM_BREAKPOINT_PX = 640 as const
export const MD_BREAKPOINT_PX = 768 as const
export const LG_BREAKPOINT_PX = 1024 as const

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

/**
 * CSS aspect-ratio reserving the detail-page character image box before the
 * image loads. Values follow the shipped assets: identity CGs are 16:9, EGO
 * CGs 1:1 (a few deviate by one pixel; object-contain absorbs it).
 */
export const DETAIL_IMAGE_ASPECT_RATIO = {
  IDENTITY: '16 / 9',
  EGO: '1 / 1',
} as const

/**
 * Expanded-image lightbox (ExpandImageButton)
 */
export const LIGHTBOX = {
  /** Fit-mode cap on the image width, as a fraction of the viewport width */
  MAX_WIDTH_VW: 95,
  /** Fit-mode cap on the image height, as a fraction of the dynamic viewport height */
  MAX_HEIGHT_DVH: 95,
  /** Upper bound for pinch/wheel zoom, relative to the fit size */
  MAX_ZOOM_SCALE: 4,
} as const
