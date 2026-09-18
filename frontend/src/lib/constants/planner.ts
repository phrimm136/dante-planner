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

export const PLANNER_SCHEMA_VERSION = 2

export const EXPORT_VERSION = 1

export const EXPORT_FILE_EXTENSION = '.danteplanner'

export const EXPORT_MAX_FILE_SIZE = 10 * 1024 * 1024

/**
 * Maximum characters an import may inflate to.
 *
 * pako has no output cap of its own, and the file-size gate bounds only the
 * compressed bytes — gzip reaches ratios past 1000:1, so a file inside the
 * 10MB gate can still exhaust memory on inflate.
 */
export const EXPORT_MAX_DECOMPRESSED_SIZE = EXPORT_MAX_FILE_SIZE * 20

export const INFLATE_INPUT_CHUNK_BYTES = 64 * 1024

export const DECK_CODE_MAX_LENGTH = 512

export const PLANNER_STORAGE_KEYS = {
  PLANNER: 'planner',
  TOMBSTONE: 'tombstone',
  MD: 'md',
} as const

export const PLANNER_LIST = {
  PAGE_SIZE: 20,
  MAX_KEYWORDS_DISPLAY: 3,
  SORT_OPTIONS: ['recent', 'popular', 'votes'] as const,
} as const

export const BATCH_PULL_MAX_IDS = 50

/**
 * The version a planner presents before the server has assigned one.
 * The wire type is a positive integer, so 0 is not a reachable server version.
 */
export const INITIAL_SYNC_VERSION = 1

export function calculatePlannerPages(totalCount: number): number {
  return Math.ceil(totalCount / PLANNER_LIST.PAGE_SIZE)
}

export const COMMENT_MAX_CHARS = 10000

export const COMMENT_INDENT_PER_LEVEL = 2

export const COMMENT_MAX_VISUAL_DEPTH_MOBILE = 2

export const COMMENT_MAX_VISUAL_DEPTH_DESKTOP = 10

export const MAX_DEPLOYED_ORDER = 7
