import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import {
  ABORT,
  IMPORT_OUTCOME_TOASTS,
  ImportAbortError,
  RESOLVE_OUTCOME_TOASTS,
  classifyImportOutcome,
  classifyResolveOutcome,
  getValidDeviceId,
  importAbortToast,
  step,
} from '../plannerExportImport'

import type { ImportAbortReason } from '../plannerExportImport'

const VALID_UUID = '3f2504e0-4f89-41d3-9a0c-0305e82c3301'
const OTHER_UUID = 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d'
const FALLBACK_UUID = '11111111-2222-4333-8444-555555555555'

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
    const source = vi.fn(async () => VALID_UUID)

    await expect(getValidDeviceId(source)).resolves.toBe(VALID_UUID)
    expect(source).toHaveBeenCalledTimes(1)
    expect(console.warn).not.toHaveBeenCalled()
  })

  it('retries once when the first id is empty', async () => {
    const source = vi.fn().mockResolvedValueOnce('').mockResolvedValueOnce(OTHER_UUID)

    await expect(getValidDeviceId(source)).resolves.toBe(OTHER_UUID)
    expect(source).toHaveBeenCalledTimes(2)
    expect(console.warn).not.toHaveBeenCalled()
  })

  it('retries once when the first id is not a UUID', async () => {
    const source = vi.fn().mockResolvedValueOnce('not-a-uuid').mockResolvedValueOnce(OTHER_UUID)

    await expect(getValidDeviceId(source)).resolves.toBe(OTHER_UUID)
    expect(source).toHaveBeenCalledTimes(2)
  })

  it('falls back to a generated UUID and warns when both attempts fail', async () => {
    const source = vi.fn(async () => 'not-a-uuid')

    await expect(getValidDeviceId(source)).resolves.toBe(FALLBACK_UUID)
    expect(source).toHaveBeenCalledTimes(2)
    expect(crypto.randomUUID).toHaveBeenCalledTimes(1)
    expect(console.warn).toHaveBeenCalledWith('Using fallback device ID:', FALLBACK_UUID)
  })
})

function captureAbort(
  stage: () => unknown,
  reason: ImportAbortReason,
): ImportAbortError & { reason: ImportAbortReason } {
  try {
    step(stage, reason)
  } catch (error) {
    return error as ImportAbortError
  }
  throw new Error(`step(${reason}) was expected to abort`)
}

describe('step', () => {
  it('passes a stage result through', () => {
    expect(step(() => 42, 'parseFailed')).toBe(42)
  })

  it('passes falsy and null-ish results through — only ABORT rejects', () => {
    expect(step<unknown>(() => null, 'parseFailed')).toBeNull()
    expect(step<unknown>(() => 0, 'parseFailed')).toBe(0)
    expect(step<unknown>(() => '', 'parseFailed')).toBe('')
  })

  it('aborts with the stage reason when the stage returns ABORT', () => {
    const error = captureAbort(() => ABORT, 'noPlannersInFile')

    expect(error).toBeInstanceOf(ImportAbortError)
    expect(error.reason).toBe('noPlannersInFile')
  })

  it('aborts with the stage reason when the stage throws', () => {
    const error = captureAbort(() => {
      throw new Error('boom')
    }, 'decompressFailed')

    expect(error).toBeInstanceOf(ImportAbortError)
    expect(error.reason).toBe('decompressFailed')
  })
})

describe('importAbortToast', () => {
  it('maps each abort reason to its i18n key and severity', () => {
    expect(importAbortToast('invalidFileFormat')).toMatchObject({
      severity: 'error',
      key: 'exportImport.invalidFileFormat',
    })
    expect(importAbortToast('decompressFailed')).toMatchObject({
      severity: 'error',
      key: 'exportImport.decompressFailed',
    })
    expect(importAbortToast('parseFailed')).toMatchObject({
      severity: 'error',
      key: 'exportImport.parseFailed',
    })
    expect(importAbortToast('noPlannersInFile')).toMatchObject({
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
