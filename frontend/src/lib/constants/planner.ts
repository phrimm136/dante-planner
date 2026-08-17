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
 * One interval for every editor-side debounce, so a flush window is never additive.
 * The editor to store hop and the store to IndexedDB autosave both wait this long.
 */
export const AUTO_SAVE_DEBOUNCE_MS = 200

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
 * Maximum characters an import may inflate to.
 *
 * pako has no output cap of its own, and the file-size gate bounds only the
 * compressed bytes — gzip reaches ratios past 1000:1, so a file inside the
 * 10MB gate can still exhaust memory on inflate.
 */
export const EXPORT_MAX_DECOMPRESSED_SIZE = EXPORT_MAX_FILE_SIZE * 20

/** Compressed bytes fed to the inflater per step, so the cap is checked as it grows. */
export const INFLATE_INPUT_CHUNK_BYTES = 64 * 1024

/**
 * Maximum characters a pasted deck code may carry.
 *
 * A real deck code is around 150 characters; the clipboard path bounded nothing
 * before handing the string to atob and then to the inflater.
 */
export const DECK_CODE_MAX_LENGTH = 512

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
 * Maximum planner ids accepted by one batch pull request.
 * Mirrors PlannerConstants.BATCH_PULL_MAX_IDS; the server rejects a longer list
 * outright, so callers chunk to this size rather than truncate.
 */
export const BATCH_PULL_MAX_IDS = 50

/**
 * The version a planner presents before the server has assigned one.
 * The wire type is a positive integer, so 0 is not a reachable server version.
 */
export const INITIAL_SYNC_VERSION = 1

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
