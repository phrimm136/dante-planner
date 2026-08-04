import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  BannedError,
  ConflictError,
  TimedOutError,
  WriteTemporarilyUnavailableError,
} from '@/lib/api'
import { queryClient } from '@/lib/queryClient'
import { toast } from '@/lib/toast'
import {
  classifySaveError,
  restrictionOf,
  saveErrorMessage,
  toastForError,
  userFriendlyValidationError,
} from '../plannerSaveErrors'

vi.mock('@/lib/i18n', () => ({
  default: {
    t: (key: string, options?: Record<string, unknown>) =>
      options && 'ns' in options ? `${String(options.ns)}:${key}` : key,
  },
}))

beforeEach(() => {
  vi.restoreAllMocks()
  vi.spyOn(console, 'warn').mockImplementation(() => {})
  vi.spyOn(console, 'error').mockImplementation(() => {})
})

describe('restrictionOf', () => {
  it('names the restriction each moderation error reports', () => {
    expect(restrictionOf(new BannedError('banned'))).toBe('banned')
    expect(restrictionOf(new TimedOutError('timed out'))).toBe('timedOut')
  })

  it('returns null for anything else', () => {
    expect(restrictionOf(new Error('boom'))).toBeNull()
    expect(restrictionOf('boom')).toBeNull()
    expect(restrictionOf(null)).toBeNull()
  })
})

describe('classifySaveError', () => {
  it('carries the server version out of a conflict', () => {
    const error = classifySaveError(new ConflictError('SYNC_CONFLICT', 'conflict', 7))

    expect(error).toMatchObject({ kind: 'conflict' })
    if (error.kind !== 'conflict') throw new Error('expected a conflict')
    expect(error.state.serverVersion).toBe(7)
    expect(error.state.detectedAt).toEqual(expect.any(String))
  })

  it('carries a null server version out of a conflict that reported none', () => {
    const error = classifySaveError(new ConflictError('CONCURRENT_WRITE', 'conflict', null))

    if (error.kind !== 'conflict') throw new Error('expected a conflict')
    expect(error.state.serverVersion).toBeNull()
  })

  it('invalidates the cached account when a restriction blocks the write', () => {
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue()

    expect(classifySaveError(new BannedError('banned'))).toEqual({
      kind: 'moderation',
      reason: 'banned',
    })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['auth', 'me'] })
  })

  it('maps a timeout to the moderation kind', () => {
    vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue()

    expect(classifySaveError(new TimedOutError('timed out'))).toEqual({
      kind: 'moderation',
      reason: 'timedOut',
    })
  })

  it('treats a temporarily unavailable write as a paused sync, not a failure', () => {
    expect(classifySaveError(new WriteTemporarilyUnavailableError('retry'))).toEqual({
      kind: 'syncPaused',
    })
  })

  it('detects quota exhaustion by code and by message', () => {
    expect(classifySaveError(Object.assign(new Error('x'), { code: 'quotaExceeded' }))).toEqual({
      kind: 'quota',
    })
    expect(classifySaveError(new Error('QuotaExceededError'))).toEqual({ kind: 'quota' })
    expect(classifySaveError(new Error('storage quota reached'))).toEqual({ kind: 'quota' })
  })

  it('carries the i18n key and params of a user-facing validation failure', () => {
    const thrown = userFriendlyValidationError({
      key: 'pages.plannerMD.publish.missingTitle',
      params: { gifts: 'A, B' },
    })

    expect(classifySaveError(thrown)).toEqual({
      kind: 'validation',
      key: 'pages.plannerMD.publish.missingTitle',
      params: { gifts: 'A, B' },
    })
  })

  it('falls back to unknown for an internal validation failure and for a bare error', () => {
    expect(
      classifySaveError(Object.assign(new Error('bad'), { code: 'validationFailed' })),
    ).toEqual({ kind: 'unknown' })
    expect(classifySaveError(new Error('boom'))).toEqual({ kind: 'unknown' })
    expect(classifySaveError('boom')).toEqual({ kind: 'unknown' })
  })
})

describe('saveErrorMessage', () => {
  const FALLBACK = 'pages.plannerMD.save.failed'

  it('stays silent for errors that have their own surface', () => {
    expect(
      saveErrorMessage(
        { kind: 'conflict', state: { serverVersion: 1, detectedAt: 'now' } },
        FALLBACK,
      ),
    ).toBeNull()
    expect(saveErrorMessage({ kind: 'syncPaused' }, FALLBACK)).toBeNull()
  })

  it('uses the common-namespace moderation messages', () => {
    expect(saveErrorMessage({ kind: 'moderation', reason: 'banned' }, FALLBACK)).toBe(
      'common:moderation.banned',
    )
    expect(saveErrorMessage({ kind: 'moderation', reason: 'timedOut' }, FALLBACK)).toBe(
      'common:moderation.timedOut',
    )
  })

  it('uses the validation key rather than the fallback', () => {
    expect(saveErrorMessage({ kind: 'validation', key: 'a.b.c' }, FALLBACK)).toBe('planner:a.b.c')
  })

  it('uses the fallback for an unknown failure', () => {
    expect(saveErrorMessage({ kind: 'unknown' }, FALLBACK)).toBe(`planner:${FALLBACK}`)
  })

  it('has a message for quota exhaustion', () => {
    expect(saveErrorMessage({ kind: 'quota' }, FALLBACK)).toBe(
      'planner:pages.plannerMD.save.quotaExceeded',
    )
  })
})

describe('toastForError', () => {
  it('reports a restriction with its own message', () => {
    const error = vi.spyOn(toast, 'error').mockReturnValue('' as never)

    toastForError(new BannedError('banned'), 'pages.plannerMD.publish.failed')

    expect(error.mock.calls[0][0]).toBe('common:moderation.banned')
  })

  it('falls back for every non-restriction error, including a conflict', () => {
    const error = vi.spyOn(toast, 'error').mockReturnValue('' as never)

    toastForError(
      new ConflictError('SYNC_CONFLICT', 'conflict', 3),
      'pages.plannerMD.publish.failed',
    )
    toastForError(new Error('boom'), 'pages.plannerMD.publish.failed')

    expect(error.mock.calls.map((call) => call[0])).toEqual([
      'planner:pages.plannerMD.publish.failed',
      'planner:pages.plannerMD.publish.failed',
    ])
  })
})
