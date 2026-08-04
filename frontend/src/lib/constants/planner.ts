/**
 * Planner-domain configuration: versioning, local persistence, export format,
 * list paging, and the comment thread attached to a published planner.
 */

/**
 * Recommended planner threshold (upvotes)
 * Planners with upvotes >= this value show star indicator
 */
export const RECOMMENDED_THRESHOLD = 10

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
 * Start Buff card dimensions; must match the actual pane image size used in
 * the card component.
 */
export const START_BUFF_CARD_SIZE = { width: 272, height: 320 } as const

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
