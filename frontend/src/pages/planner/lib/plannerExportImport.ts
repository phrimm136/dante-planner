import { Inflate, gzip } from 'pako'

import { ok, err } from '@/lib/result'
import {
  EXPORT_FILE_EXTENSION,
  EXPORT_MAX_DECOMPRESSED_SIZE,
  EXPORT_VERSION,
  INFLATE_INPUT_CHUNK_BYTES,
} from '@/lib/constants'
import { sanitizeToPlainText } from '@/shared/sanitize'
import { ExportEnvelopeSchema, toSaveablePlanner } from '../schemas/PlannerSchemas'
import { GZIP_OS_BYTE_OFFSET, GZIP_OS_TOPS20 } from './deckCode'
import { withNormalizedIds } from './plannerIdNormalize'
import type { IdMigrationTable } from './idMigrationTable'

import type { Result } from '@/lib/result'
import type { ExportEnvelope, PlannerExportItem, SaveablePlanner } from '../types/PlannerTypes'
import type { z } from 'zod'

export type ToastSeverity = 'info' | 'success' | 'warning' | 'error'

export interface ToastDescriptor {
  severity: ToastSeverity
  key: string
}

export interface OutcomeToast<TCounts> extends ToastDescriptor {
  params: (counts: TCounts) => Record<string, number>
}

const EXPORT_FILE_PREFIX = 'plans-'

export function toExportItem(planner: SaveablePlanner): PlannerExportItem {
  return {
    id: planner.metadata.id,
    metadata: planner.metadata,
    config: planner.config,
    content: planner.content,
  }
}

export function buildExportEnvelope(
  planners: PlannerExportItem[],
  exportedAt: string,
): ExportEnvelope {
  return {
    exportVersion: EXPORT_VERSION,
    exportedAt,
    planners,
  }
}

export function encodeExportEnvelope(envelope: ExportEnvelope): Uint8Array<ArrayBuffer> {
  // pako 3 ignores a `header` option on the one-shot gzip(), so the OS byte is
  // written directly into the emitted header instead.
  const compressed = gzip(JSON.stringify(envelope))
  compressed[GZIP_OS_BYTE_OFFSET] = GZIP_OS_TOPS20
  return compressed
}

export function exportFileName(exportedAt: string): string {
  return `${EXPORT_FILE_PREFIX}${exportedAt.split('T')[0]}${EXPORT_FILE_EXTENSION}`
}

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

export type ImportEnvelope = z.infer<typeof ExportEnvelopeSchema>

export function readGzipBytes(data: Uint8Array): Result<Uint8Array, ImportError> {
  const isGzip =
    data.length >= 2 && data[0] === GZIP_MAGIC_BYTES[0] && data[1] === GZIP_MAGIC_BYTES[1]
  return isGzip ? ok(data) : err<ImportError>({ kind: 'invalidFileFormat' })
}

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

export function parseImportJson(text: string): Result<unknown, ImportError> {
  try {
    return ok(JSON.parse(text) as unknown)
  } catch {
    return err<ImportError>({ kind: 'parseFailed' })
  }
}

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

const UNTITLED_IMPORT_TITLE = 'Untitled'

export function sanitizePlannerTitle(title: string): string {
  return sanitizeToPlainText(title).trim() || UNTITLED_IMPORT_TITLE
}

export interface ImportConflictCandidate {
  id: string
  incoming: SaveablePlanner
}

export interface SkippedImport {
  id: string
  title: string
}

export type ImportValidator = (planner: SaveablePlanner) => object | null

export interface PartitionedImport {
  conflicting: ImportConflictCandidate[]
  fresh: SaveablePlanner[]
  skipped: SkippedImport[]
}

function isRejected(planner: SaveablePlanner, validate: ImportValidator): boolean {
  try {
    return validate(planner) !== null
  } catch {
    return true
  }
}

function toImportedPlanner(item: ImportEnvelope['planners'][number]): SaveablePlanner {
  return toSaveablePlanner(
    {
      ...item.metadata,
      title: sanitizePlannerTitle(item.metadata.title),
    },
    item.config,
    item.content,
  )
}

export function partitionImport(
  envelope: ImportEnvelope,
  existingIds: ReadonlySet<string>,
  table: IdMigrationTable,
  validate: ImportValidator,
): PartitionedImport {
  const conflicting: ImportConflictCandidate[] = []
  const fresh: SaveablePlanner[] = []
  const skipped: SkippedImport[] = []

  for (const item of envelope.planners) {
    const incoming = withNormalizedIds(toImportedPlanner(item), table)
    if (isRejected(incoming, validate)) {
      skipped.push({ id: item.id, title: incoming.metadata.title })
    } else if (existingIds.has(item.id)) {
      conflicting.push({ id: item.id, incoming })
    } else {
      fresh.push(incoming)
    }
  }

  return { conflicting, fresh, skipped }
}

export interface ImportCounts {
  imported: number
  skipped: number
  conflicts: number
}

export type ImportOutcome = 'partialImport' | 'partialSuccess' | 'success'

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
