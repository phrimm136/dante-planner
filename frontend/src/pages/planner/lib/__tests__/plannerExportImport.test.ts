import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { gzip } from 'pako'
import {
  IMPORT_OUTCOME_TOASTS,
  RESOLVE_OUTCOME_TOASTS,
  buildExportEnvelope,
  classifyImportOutcome,
  classifyResolveOutcome,
  decompressImport,
  encodeExportEnvelope,
  exportFileName,
  getValidDeviceId,
  importErrorToast,
  parseImportJson,
  partitionImport,
  readGzipBytes,
  readImportEnvelope,
  sanitizePlannerTitle,
  toExportItem,
} from '../plannerExportImport'
import { GZIP_OS_BYTE_OFFSET, GZIP_OS_TOPS20 } from '../deckCode'

import { ok, err } from '@/lib/result'
import { EXPORT_FILE_EXTENSION, EXPORT_VERSION, INFLATE_INPUT_CHUNK_BYTES } from '@/lib/constants'
import { buildSaveablePlanner } from '@/test-utils'

import type { ImportEnvelope, ImportError } from '../plannerExportImport'
import type { Result } from '@/lib/result'

const VALID_UUID = '3f2504e0-4f89-41d3-9a0c-0305e82c3301'
const OTHER_UUID = 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d'
const FALLBACK_UUID = '11111111-2222-4333-8444-555555555555'

const TIMESTAMP = '2026-01-01T00:00:00.000Z'

const EXPORT_ITEM = {
  id: VALID_UUID,
  metadata: {
    id: VALID_UUID,
    title: 'Imported plan',
    status: 'saved',
    schemaVersion: 2,
    contentVersion: 6,
    plannerType: 'MIRROR_DUNGEON',
    syncVersion: 1,
    createdAt: TIMESTAMP,
    lastModifiedAt: TIMESTAMP,
    savedAt: TIMESTAMP,
    deviceId: '',
  },
  config: { type: 'MIRROR_DUNGEON', category: '5F' },
  content: {},
}

function envelope(planners: unknown[]) {
  return {
    exportVersion: 1,
    exportedAt: TIMESTAMP,
    sourceDeviceId: '',
    planners,
  }
}

function gzipped(payload: unknown): Uint8Array {
  return gzip(JSON.stringify(payload))
}

function expectOk<T>(result: Result<T, ImportError>): T {
  expect(result.ok).toBe(true)
  if (!result.ok) throw new Error('expected a successful stage')
  return result.value
}

function expectErr<T>(result: Result<T, ImportError>): ImportError {
  expect(result.ok).toBe(false)
  if (result.ok) throw new Error('expected a failed stage')
  return result.error
}

describe('getValidDeviceId', () => {
  beforeEach(() => {
    vi.spyOn(globalThis.crypto, 'randomUUID').mockReturnValue(FALLBACK_UUID)
    vi.spyOn(console, 'warn').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('returns the first id when it is a valid UUID, without retrying', async () => {
    const source = vi.fn(async () => ok(VALID_UUID))

    await expect(getValidDeviceId(source)).resolves.toEqual(ok(VALID_UUID))
    expect(source).toHaveBeenCalledTimes(1)
    expect(console.warn).not.toHaveBeenCalled()
  })

  it('retries once when the first id is empty', async () => {
    const source = vi.fn().mockResolvedValueOnce(ok('')).mockResolvedValueOnce(ok(OTHER_UUID))

    await expect(getValidDeviceId(source)).resolves.toEqual(ok(OTHER_UUID))
    expect(source).toHaveBeenCalledTimes(2)
    expect(console.warn).not.toHaveBeenCalled()
  })

  it('retries once when the first id is not a UUID', async () => {
    const source = vi
      .fn()
      .mockResolvedValueOnce(ok('not-a-uuid'))
      .mockResolvedValueOnce(ok(OTHER_UUID))

    await expect(getValidDeviceId(source)).resolves.toEqual(ok(OTHER_UUID))
    expect(source).toHaveBeenCalledTimes(2)
  })

  it('falls back to a generated UUID and warns when both attempts fail', async () => {
    const source = vi.fn(async () => ok('not-a-uuid'))

    await expect(getValidDeviceId(source)).resolves.toEqual(ok(FALLBACK_UUID))
    expect(source).toHaveBeenCalledTimes(2)
    expect(crypto.randomUUID).toHaveBeenCalledTimes(1)
    expect(console.warn).toHaveBeenCalledWith('Using fallback device ID:', FALLBACK_UUID)
  })

  it('reports a read that broke instead of minting over it', async () => {
    const failure = err({ kind: 'ioError' as const, cause: new Error('disk gone') })
    const source = vi.fn(async () => failure)

    await expect(getValidDeviceId(source)).resolves.toBe(failure)
    expect(source).toHaveBeenCalledTimes(1)
    expect(crypto.randomUUID).not.toHaveBeenCalled()
    expect(console.warn).not.toHaveBeenCalled()
  })

  it('reports a retry that broke after a first id that was not a UUID', async () => {
    const failure = err({ kind: 'ioError' as const, cause: new Error('disk gone') })
    const source = vi.fn().mockResolvedValueOnce(ok('not-a-uuid')).mockResolvedValueOnce(failure)

    await expect(getValidDeviceId(source)).resolves.toBe(failure)
    expect(source).toHaveBeenCalledTimes(2)
    expect(crypto.randomUUID).not.toHaveBeenCalled()
  })
})

describe('readGzipBytes', () => {
  it('passes bytes opening with the gzip header through', () => {
    const bytes = gzipped(envelope([]))

    expect(readGzipBytes(bytes)).toEqual({ ok: true, value: bytes })
  })

  it('rejects bytes that do not open with the gzip header', () => {
    expect(expectErr(readGzipBytes(new Uint8Array([0x50, 0x4b, 0x03, 0x04])))).toEqual({
      kind: 'invalidFileFormat',
    })
  })

  it('rejects a file too short to carry the header', () => {
    expect(expectErr(readGzipBytes(new Uint8Array([0x1f])))).toEqual({ kind: 'invalidFileFormat' })
  })
})

describe('decompressImport', () => {
  it('inflates an input spanning several compressed chunks', () => {
    // Larger than INFLATE_INPUT_CHUNK_BYTES once compressed, so the loop pushes
    // more than one slice and the reassembled output must still be exact.
    // Deterministic pseudo-random text: repeated filler compresses away to a few
    // KB, which would never span more than one input chunk.
    const chars: string[] = []
    let seed = 0x2545f491
    for (let i = 0; i < 400_000; i++) {
      // xorshift32 via Math.imul: a plain multiply overflows float precision and
      // degenerates into a short, highly compressible cycle.
      seed ^= seed << 13
      seed = Math.imul(seed, 1) | 0
      seed ^= seed >>> 17
      seed ^= seed << 5
      seed = seed | 0
      chars.push(String.fromCharCode(33 + (Math.abs(seed) % 90)))
    }
    const filler = chars.join('')
    const bulky = envelope([])
    ;(bulky as { filler?: string }).filler = filler
    const compressed = gzipped(bulky)
    expect(compressed.length).toBeGreaterThan(INFLATE_INPUT_CHUNK_BYTES)

    expect(JSON.parse(expectOk(decompressImport(compressed)))).toEqual(bulky)
  })

  it('inflates gzipped bytes back to their text', () => {
    const result = decompressImport(gzipped(envelope([])))

    expect(result).toEqual({ ok: true, value: JSON.stringify(envelope([])) })
  })

  it('rejects bytes that cannot be inflated', () => {
    expect(expectErr(decompressImport(new Uint8Array([0x1f, 0x8b, 0x08, 0x00])))).toEqual({
      kind: 'decompressFailed',
    })
  })
})

describe('parseImportJson', () => {
  it('parses the inflated text', () => {
    expect(parseImportJson('{"a":1}')).toEqual({ ok: true, value: { a: 1 } })
  })

  it('passes falsy parsed values through — only unparseable text fails', () => {
    expect(parseImportJson('null')).toEqual({ ok: true, value: null })
    expect(parseImportJson('0')).toEqual({ ok: true, value: 0 })
    expect(parseImportJson('""')).toEqual({ ok: true, value: '' })
  })

  it('rejects text that is not JSON', () => {
    expect(expectErr(parseImportJson('{ not json'))).toEqual({ kind: 'parseFailed' })
  })
})

describe('readImportEnvelope', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('returns the validated envelope', () => {
    const result = readImportEnvelope(envelope([EXPORT_ITEM]))

    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.value.planners).toHaveLength(1)
    expect(result.value.planners[0]?.metadata.title).toBe('Imported plan')
  })

  it('rejects a payload that does not match the envelope shape', () => {
    expect(expectErr(readImportEnvelope({ planners: 'nope' }))).toEqual({
      kind: 'invalidFileFormat',
    })
  })

  it('rejects an envelope carrying an unparseable planner', () => {
    expect(expectErr(readImportEnvelope(envelope([{ id: VALID_UUID }])))).toEqual({
      kind: 'invalidFileFormat',
    })
  })

  it('rejects an envelope carrying no planners', () => {
    expect(expectErr(readImportEnvelope(envelope([])))).toEqual({ kind: 'noPlannersInFile' })
  })
})

describe('import stage failures', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  const cases: {
    name: string
    stage: () => Result<unknown, ImportError>
    kind: ImportError['kind']
  }[] = [
    {
      name: 'a file that is not gzip',
      stage: () => readGzipBytes(new Uint8Array([0x00, 0x00])),
      kind: 'invalidFileFormat',
    },
    {
      name: 'an envelope of the wrong shape',
      stage: () => readImportEnvelope({}),
      kind: 'invalidFileFormat',
    },
    {
      name: 'gzip bytes that will not inflate',
      stage: () => decompressImport(new Uint8Array([0x1f, 0x8b, 0x08, 0x00, 0x00])),
      kind: 'decompressFailed',
    },
    {
      name: 'inflated text that is not JSON',
      stage: () => parseImportJson('<html>'),
      kind: 'parseFailed',
    },
    {
      name: 'an envelope with an empty planner list',
      stage: () => readImportEnvelope(envelope([])),
      kind: 'noPlannersInFile',
    },
  ]

  it.each(cases)('rejects $name with $kind', ({ stage, kind }) => {
    expect(expectErr(stage())).toEqual({ kind })
  })
})

describe('importErrorToast', () => {
  it('maps each import error to its i18n key and severity', () => {
    expect(importErrorToast({ kind: 'invalidFileFormat' })).toMatchObject({
      severity: 'error',
      key: 'exportImport.invalidFileFormat',
    })
    expect(importErrorToast({ kind: 'decompressFailed' })).toMatchObject({
      severity: 'error',
      key: 'exportImport.decompressFailed',
    })
    expect(importErrorToast({ kind: 'parseFailed' })).toMatchObject({
      severity: 'error',
      key: 'exportImport.parseFailed',
    })
    expect(importErrorToast({ kind: 'noPlannersInFile' })).toMatchObject({
      severity: 'info',
      key: 'exportImport.noPlannersInFile',
    })
  })
})

describe('classifyImportOutcome', () => {
  it('is silent when conflicts are pending and nothing else happened', () => {
    expect(classifyImportOutcome({ imported: 0, skipped: 0, conflicts: 2 })).toBeNull()
  })

  it('reports a partial import when conflicts are pending alongside imports', () => {
    expect(classifyImportOutcome({ imported: 3, skipped: 0, conflicts: 2 })).toBe('partialImport')
  })

  it('reports a partial import when conflicts are pending alongside skips', () => {
    expect(classifyImportOutcome({ imported: 0, skipped: 1, conflicts: 2 })).toBe('partialImport')
  })

  it('reports partial success when there are no conflicts but some skips', () => {
    expect(classifyImportOutcome({ imported: 3, skipped: 1, conflicts: 0 })).toBe('partialSuccess')
  })

  it('reports success when everything landed', () => {
    expect(classifyImportOutcome({ imported: 3, skipped: 0, conflicts: 0 })).toBe('success')
  })
})

describe('IMPORT_OUTCOME_TOASTS', () => {
  const counts = { imported: 3, skipped: 1, conflicts: 2 }

  it('raises an info toast naming imported and conflict counts for a partial import', () => {
    const descriptor = IMPORT_OUTCOME_TOASTS.partialImport
    expect(descriptor.severity).toBe('info')
    expect(descriptor.key).toBe('exportImport.partialImport')
    expect(descriptor.params(counts)).toEqual({ imported: 3, conflicts: 2 })
  })

  it('raises a success toast naming imported and skipped counts for a partial success', () => {
    const descriptor = IMPORT_OUTCOME_TOASTS.partialSuccess
    expect(descriptor.severity).toBe('success')
    expect(descriptor.key).toBe('exportImport.importPartialSuccess')
    expect(descriptor.params(counts)).toEqual({ imported: 3, skipped: 1 })
  })

  it('counts imports as the pluralization count on full success', () => {
    const descriptor = IMPORT_OUTCOME_TOASTS.success
    expect(descriptor.severity).toBe('success')
    expect(descriptor.key).toBe('exportImport.importSuccess')
    expect(descriptor.params(counts)).toEqual({ count: 3 })
  })
})

describe('classifyResolveOutcome', () => {
  it('reports partial resolution when any save errored', () => {
    expect(classifyResolveOutcome({ saved: 2, errors: 1 })).toBe('partial')
    expect(classifyResolveOutcome({ saved: 0, errors: 1 })).toBe('partial')
  })

  it('reports success when saves landed without errors', () => {
    expect(classifyResolveOutcome({ saved: 2, errors: 0 })).toBe('success')
  })

  it('reports kept-local when nothing was saved and nothing errored', () => {
    expect(classifyResolveOutcome({ saved: 0, errors: 0 })).toBe('keptLocal')
  })
})

describe('toExportItem', () => {
  it('lifts the id out and drops the owning device', () => {
    const planner = buildSaveablePlanner({ metadata: { id: VALID_UUID, deviceId: OTHER_UUID } })

    const item = toExportItem(planner)

    expect(item.id).toBe(VALID_UUID)
    expect(item.metadata.deviceId).toBe('')
    expect(item.metadata.title).toBe(planner.metadata.title)
    expect(item.config).toEqual(planner.config)
    expect(item.content).toEqual(planner.content)
  })
})

describe('buildExportEnvelope', () => {
  it('stamps the export version and the device the file came from', () => {
    const items = [toExportItem(buildSaveablePlanner())]

    expect(buildExportEnvelope(items, VALID_UUID, TIMESTAMP)).toEqual({
      exportVersion: EXPORT_VERSION,
      exportedAt: TIMESTAMP,
      sourceDeviceId: VALID_UUID,
      planners: items,
    })
  })
})

describe('encodeExportEnvelope', () => {
  const envelopeOf = () =>
    buildExportEnvelope([toExportItem(buildSaveablePlanner())], VALID_UUID, TIMESTAMP)

  it('stamps the OS byte the reader checks', () => {
    expect(encodeExportEnvelope(envelopeOf())[GZIP_OS_BYTE_OFFSET]).toBe(GZIP_OS_TOPS20)
  })

  it('produces bytes the import stages read back', () => {
    const bytes = expectOk(readGzipBytes(encodeExportEnvelope(envelopeOf())))
    const text = expectOk(decompressImport(bytes))

    expect(expectOk(parseImportJson(text))).toEqual(envelopeOf())
  })
})

describe('exportFileName', () => {
  it('names the file after the day the export was taken', () => {
    expect(exportFileName(TIMESTAMP)).toBe(`plans-2026-01-01${EXPORT_FILE_EXTENSION}`)
  })
})

describe('sanitizePlannerTitle', () => {
  it('reduces a title to its text', () => {
    expect(sanitizePlannerTitle('<b>Run</b>')).toBe('Run')
  })

  it('trims the surviving text', () => {
    expect(sanitizePlannerTitle('  Run  ')).toBe('Run')
  })

  it('falls back when nothing survives', () => {
    expect(sanitizePlannerTitle('<img src=x onerror=alert(1)>')).toBe('Untitled')
    expect(sanitizePlannerTitle('<p></p>')).toBe('Untitled')
    expect(sanitizePlannerTitle('   ')).toBe('Untitled')
  })
})

describe('partitionImport', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  function envelopeOf(items: unknown[]): ImportEnvelope {
    return expectOk(readImportEnvelope(envelope(items)))
  }

  it('rewrites an import with no local counterpart onto this device', () => {
    const { conflicting, fresh } = partitionImport(envelopeOf([EXPORT_ITEM]), new Set(), OTHER_UUID)

    expect(conflicting).toEqual([])
    expect(fresh).toHaveLength(1)
    expect(fresh[0]?.metadata.id).toBe(VALID_UUID)
    expect(fresh[0]?.metadata.deviceId).toBe(OTHER_UUID)
    expect(fresh[0]?.metadata.title).toBe('Imported plan')
  })

  it('holds back an import whose id the local store already carries', () => {
    const { conflicting, fresh } = partitionImport(
      envelopeOf([EXPORT_ITEM]),
      new Set([VALID_UUID]),
      OTHER_UUID,
    )

    expect(fresh).toEqual([])
    expect(conflicting).toHaveLength(1)
    expect(conflicting[0]?.id).toBe(VALID_UUID)
    expect(conflicting[0]?.incoming.metadata.deviceId).toBe(OTHER_UUID)
  })

  it('reduces an imported title to plain text', () => {
    const scripted = {
      ...EXPORT_ITEM,
      metadata: { ...EXPORT_ITEM.metadata, title: '<b>Run</b>' },
    }

    const { fresh } = partitionImport(envelopeOf([scripted]), new Set(), OTHER_UUID)

    expect(fresh[0]?.metadata.title).toBe('Run')
  })
})

describe('RESOLVE_OUTCOME_TOASTS', () => {
  const counts = { saved: 2, errors: 1 }

  it('warns with saved and error counts on a partial resolution', () => {
    const descriptor = RESOLVE_OUTCOME_TOASTS.partial
    expect(descriptor.severity).toBe('warning')
    expect(descriptor.key).toBe('exportImport.resolvePartial')
    expect(descriptor.params(counts)).toEqual({ saved: 2, errors: 1 })
  })

  it('counts saves as the pluralization count on success', () => {
    const descriptor = RESOLVE_OUTCOME_TOASTS.success
    expect(descriptor.severity).toBe('success')
    expect(descriptor.key).toBe('exportImport.resolveSuccess')
    expect(descriptor.params(counts)).toEqual({ count: 2 })
  })

  it('takes no interpolation params when all local versions were kept', () => {
    const descriptor = RESOLVE_OUTCOME_TOASTS.keptLocal
    expect(descriptor.severity).toBe('success')
    expect(descriptor.key).toBe('exportImport.resolveKeptLocal')
    expect(descriptor.params(counts)).toEqual({})
  })
})
