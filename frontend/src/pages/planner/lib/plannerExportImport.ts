/**
 * Planner Export/Import Support
 *
 * The decode stages of the import pipeline, the device-id resolution used when
 * rewriting imported planners, and the outcome tables mapping an end state to its
 * user-facing toast (severity + i18n key + interpolation params).
 */

import { Inflate } from 'pako'

import { ok, err } from '@/lib/result'
import { EXPORT_MAX_DECOMPRESSED_SIZE, INFLATE_INPUT_CHUNK_BYTES } from '@/lib/constants'
import { isValidUUID } from '@/lib/utils'
import { generateUUID } from '@/lib/uuid'
import { ExportEnvelopeSchema } from '../schemas/PlannerSchemas'

import type { Result } from '@/lib/result'
import type { StorageReadError } from '@/lib/storage'
import type { z } from 'zod'

/** Toast method used to surface an outcome. */
export type ToastSeverity = 'info' | 'success' | 'warning' | 'error'

/** A toast to raise: at which severity, under which i18n key. */
export interface ToastDescriptor {
  severity: ToastSeverity
  key: string
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
 *
 * A read that could not be performed is reported instead. Minting over it would
 * orphan every row already written under the id the read failed to see.
 */
export async function getValidDeviceId(
  source: () => Promise<Result<string, StorageReadError>>,
): Promise<Result<string, StorageReadError>> {
  let read = await source()
  if (!read.ok) return read

  if (!isValidUUID(read.value)) {
    read = await source()
    if (!read.ok) return read
  }

  if (!isValidUUID(read.value)) {
    const fallback = generateUUID()
    console.warn('Using fallback device ID:', fallback)
    return ok(fallback)
  }
  return read
}

/* -------------------------------------------------------------------------- */
/* Import stages                                                               */
/* -------------------------------------------------------------------------- */

/** Why an import stage rejected the file. */
export type ImportError =
  | { kind: 'invalidFileFormat' }
  | { kind: 'decompressFailed' }
  | { kind: 'decompressedTooLarge' }
  | { kind: 'parseFailed' }
  | { kind: 'noPlannersInFile' }

const IMPORT_ERROR_TOASTS = {
  invalidFileFormat: {
    severity: 'error',
    key: 'exportImport.invalidFileFormat',
  },
  decompressFailed: {
    severity: 'error',
    key: 'exportImport.decompressFailed',
  },
  decompressedTooLarge: {
    severity: 'error',
    key: 'exportImport.fileTooLarge',
  },
  parseFailed: {
    severity: 'error',
    key: 'exportImport.parseFailed',
  },
  noPlannersInFile: {
    severity: 'info',
    key: 'exportImport.noPlannersInFile',
  },
} as const satisfies Record<ImportError['kind'], ToastDescriptor>

export function importErrorToast(error: ImportError): ToastDescriptor {
  return IMPORT_ERROR_TOASTS[error.kind]
}

/** Gzip magic bytes: 0x1f 0x8b */
const GZIP_MAGIC_BYTES = [0x1f, 0x8b]

/** An envelope that passed structural validation; content stays loosely typed. */
export type ImportEnvelope = z.infer<typeof ExportEnvelopeSchema>

/** Accept a file only when it opens with a gzip header. */
export function readGzipBytes(data: Uint8Array): Result<Uint8Array, ImportError> {
  const isGzip =
    data.length >= 2 && data[0] === GZIP_MAGIC_BYTES[0] && data[1] === GZIP_MAGIC_BYTES[1]
  return isGzip ? ok(data) : err<ImportError>({ kind: 'invalidFileFormat' })
}

/**
 * Inflate the export file into its JSON text, abandoning it once the output
 * passes {@link EXPORT_MAX_DECOMPRESSED_SIZE}. The input is fed in chunks so the
 * cap is reached before the whole bomb is materialised.
 */
export function decompressImport(data: Uint8Array): Result<string, ImportError> {
  const inflater = new Inflate()
  const parts: Uint8Array[] = []
  let produced = 0
  let exceeded = false

  inflater.onData = (chunk) => {
    produced += chunk.length
    if (produced > EXPORT_MAX_DECOMPRESSED_SIZE) {
      exceeded = true
      return
    }
    parts.push(chunk)
  }

  try {
    for (let offset = 0; offset < data.length; offset += INFLATE_INPUT_CHUNK_BYTES) {
      const end = Math.min(offset + INFLATE_INPUT_CHUNK_BYTES, data.length)
      inflater.push(data.subarray(offset, end), end === data.length)
      if (exceeded) return err<ImportError>({ kind: 'decompressedTooLarge' })
    }
  } catch {
    return err<ImportError>({ kind: 'decompressFailed' })
  }

  if (inflater.err) return err<ImportError>({ kind: 'decompressFailed' })

  const inflated = new Uint8Array(produced)
  let at = 0
  for (const part of parts) {
    inflated.set(part, at)
    at += part.length
  }
  return ok(new TextDecoder().decode(inflated))
}

/** Parse the inflated text; the shape is checked by {@link readImportEnvelope}. */
export function parseImportJson(text: string): Result<unknown, ImportError> {
  try {
    return ok(JSON.parse(text) as unknown)
  } catch {
    return err<ImportError>({ kind: 'parseFailed' })
  }
}

/** Validate the envelope structure and reject one carrying no planners. */
export function readImportEnvelope(parsed: unknown): Result<ImportEnvelope, ImportError> {
  const validation = ExportEnvelopeSchema.safeParse(parsed)
  if (!validation.success) {
    console.error('Validation failed:', validation.error)
    return err<ImportError>({ kind: 'invalidFileFormat' })
  }
  if (validation.data.planners.length === 0) {
    return err<ImportError>({ kind: 'noPlannersInFile' })
  }
  return ok(validation.data)
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
    params: ({ imported, conflicts }) => ({ imported, conflicts }),
  },
  partialSuccess: {
    severity: 'success',
    key: 'exportImport.importPartialSuccess',
    params: ({ imported, skipped }) => ({ imported, skipped }),
  },
  success: {
    severity: 'success',
    key: 'exportImport.importSuccess',
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
    params: ({ saved, errors }) => ({ saved, errors }),
  },
  success: {
    severity: 'success',
    key: 'exportImport.resolveSuccess',
    params: ({ saved }) => ({ count: saved }),
  },
  keptLocal: {
    severity: 'success',
    key: 'exportImport.resolveKeptLocal',
    params: () => ({}),
  },
}
