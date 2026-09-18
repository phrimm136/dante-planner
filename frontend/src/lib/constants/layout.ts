export const FILTER_SIDEBAR_WIDTH = 280

export const DETAIL_PAGE = {
  COLUMN_LEFT: 'lg:col-span-4',
  COLUMN_RIGHT: 'lg:col-span-6',
} as const

export const EXCLUSIVE_GIFT_ICONS = {
  ICON_SIZE: 32,
  GAP: 4,
} as const

export const CARD_MOBILE_SCALE = 0.8 as const

export const CARD_MOBILE_SCALE_DENSE = 0.6 as const

export const CARD_MOBILE_SCALE_NONE = 1 as const

export const CARD_GAP_PX = 16 as const

export const SM_BREAKPOINT_PX = 640 as const
export const MD_BREAKPOINT_PX = 768 as const
export const LG_BREAKPOINT_PX = 1024 as const

export const PROGRESSIVE_REVEAL = {
  STAGGER_DELAY: 50,
  CARD_BATCH: 10,
  KEYWORD_CARD_BATCH: 50,
} as const

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

export const LIGHTBOX = {
  MAX_WIDTH_VW: 95,
  MAX_HEIGHT_DVH: 95,
  MAX_ZOOM_SCALE: 4,
} as const
