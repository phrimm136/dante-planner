/**
 * Planner Export/Import Support
 *
 * The abort protocol for the import pipeline, the device-id resolution used when
 * rewriting imported planners, and the outcome tables mapping an end state to its
 * user-facing toast (severity + i18n key + interpolation params).
 */

import { isValidUUID } from '@/lib/utils'
import { generateUUID } from '@/lib/uuid'

/** Toast method used to surface an outcome. */
export type ToastSeverity = 'info' | 'success' | 'warning' | 'error'

/** A toast to raise: which sonner method, which i18n key, and its inline default. */
export interface ToastDescriptor {
  severity: ToastSeverity
  key: string
  fallback: string
}

/** A toast whose message interpolates counts. */
export interface OutcomeToast<TCounts> extends ToastDescriptor {
  params: (counts: TCounts) => Record<string, number>
}

/* -------------------------------------------------------------------------- */
/* Device id                                                                   */
/* -------------------------------------------------------------------------- */

/**
 * Resolve a usable device id: one retry of the supplied source, then a generated
 * UUID so imported planners always carry a well-formed owner key.
 */
export async function getValidDeviceId(source: () => Promise<string>): Promise<string> {
  let deviceId = await source()
  if (!deviceId || !isValidUUID(deviceId)) {
    deviceId = await source()
  }
  if (!deviceId || !isValidUUID(deviceId)) {
    deviceId = generateUUID()
    console.warn('Using fallback device ID:', deviceId)
  }
  return deviceId
}

/* -------------------------------------------------------------------------- */
/* Pipeline aborts                                                             */
/* -------------------------------------------------------------------------- */

const IMPORT_ABORT_TOASTS = {
  invalidFileFormat: {
    severity: 'error',
    key: 'exportImport.invalidFileFormat',
    fallback: 'Invalid file format',
  },
  decompressFailed: {
    severity: 'error',
    key: 'exportImport.decompressFailed',
    fallback: 'Failed to decompress file',
  },
  parseFailed: {
    severity: 'error',
    key: 'exportImport.parseFailed',
    fallback: 'Failed to parse file',
  },
  noPlannersInFile: {
    severity: 'info',
    key: 'exportImport.noPlannersInFile',
    fallback: 'No planners in file',
  },
} as const satisfies Record<string, ToastDescriptor>

export type ImportAbortReason = keyof typeof IMPORT_ABORT_TOASTS

/** Signals that an import stage failed; carries the reason its toast is keyed by. */
export class ImportAbortError extends Error {
  readonly reason: ImportAbortReason

  constructor(reason: ImportAbortReason) {
    super(reason)
    this.name = 'ImportAbortError'
    this.reason = reason
  }
}

export function importAbortToast(reason: ImportAbortReason): ToastDescriptor {
  return IMPORT_ABORT_TOASTS[reason]
}

/** Returned by a stage to reject its input; distinct from any parsed JSON value. */
export const ABORT = Symbol('import-abort')

/**
 * Run one import stage. A thrown error or an {@link ABORT} result aborts the pipeline
 * with `reason`; anything else is passed through to the next stage.
 *
 * @throws ImportAbortError
 */
export function step<T>(stage: () => T | typeof ABORT, reason: ImportAbortReason): T {
  let value: T | typeof ABORT
  try {
    value = stage()
  } catch {
    throw new ImportAbortError(reason)
  }
  if (value === ABORT) {
    throw new ImportAbortError(reason)
  }
  return value as T
}

/* -------------------------------------------------------------------------- */
/* Import outcome                                                              */
/* -------------------------------------------------------------------------- */

export interface ImportCounts {
  imported: number
  skipped: number
  conflicts: number
}

export type ImportOutcome = 'partialImport' | 'partialSuccess' | 'success'

/** `null` when conflicts are pending and nothing else happened — no toast is due. */
export function classifyImportOutcome(counts: ImportCounts): ImportOutcome | null {
  if (counts.conflicts > 0) {
    return counts.imported > 0 || counts.skipped > 0 ? 'partialImport' : null
  }
  return counts.skipped > 0 ? 'partialSuccess' : 'success'
}

export const IMPORT_OUTCOME_TOASTS: Record<ImportOutcome, OutcomeToast<ImportCounts>> = {
  partialImport: {
    severity: 'info',
    key: 'exportImport.partialImport',
    fallback: 'Imported {{imported}}, {{conflicts}} conflicts',
    params: ({ imported, conflicts }) => ({ imported, conflicts }),
  },
  partialSuccess: {
    severity: 'success',
    key: 'exportImport.importPartialSuccess',
    fallback: 'Imported {{imported}}, skipped {{skipped}}',
    params: ({ imported, skipped }) => ({ imported, skipped }),
  },
  success: {
    severity: 'success',
    key: 'exportImport.importSuccess',
    fallback: 'Imported {{count}} planners',
    params: ({ imported }) => ({ count: imported }),
  },
}

/* -------------------------------------------------------------------------- */
/* Conflict resolution outcome                                                 */
/* -------------------------------------------------------------------------- */

export interface ResolveCounts {
  saved: number
  errors: number
}

export type ResolveOutcome = 'partial' | 'success' | 'keptLocal'

export function classifyResolveOutcome(counts: ResolveCounts): ResolveOutcome {
  if (counts.errors > 0) return 'partial'
  return counts.saved > 0 ? 'success' : 'keptLocal'
}

export const RESOLVE_OUTCOME_TOASTS: Record<ResolveOutcome, OutcomeToast<ResolveCounts>> = {
  partial: {
    severity: 'warning',
    key: 'exportImport.resolvePartial',
    fallback: 'Resolved {{saved}}, {{errors}} errors',
    params: ({ saved, errors }) => ({ saved, errors }),
  },
  success: {
    severity: 'success',
    key: 'exportImport.resolveSuccess',
    fallback: 'Resolved {{count}} conflicts',
    params: ({ saved }) => ({ count: saved }),
  },
  keptLocal: {
    severity: 'success',
    key: 'exportImport.resolveKeptLocal',
    fallback: 'Kept all local versions',
    params: () => ({}),
  },
}
