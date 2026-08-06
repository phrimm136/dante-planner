import { describe, it, expect, vi } from 'vitest'
import {
  BannedError,
  ConflictError,
  TimedOutError,
  WriteTemporarilyUnavailableError,
} from '@/lib/api'
import { toast } from '@/lib/toast'
import {
  classifySaveError,
  restrictionOf,
  saveErrorMessage,
  toastForError,
  validationSaveError,
} from '../plannerSaveErrors'
import type { SaveError } from '../plannerSaveErrors'

vi.mock('@/lib/i18n', () => ({
  default: {
    t: (key: string, options?: Record<string, unknown>) =>
      options && 'ns' in options ? `${String(options.ns)}:${key}` : key,
  },
}))

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
  // The classifier is pure, so every error class it understands is one row of a
  // table: a thrown value in, a SaveError out, with nothing mocked or spied on.
  const cases: { name: string; thrown: unknown; expected: SaveError }[] = [
    {
      name: 'a ban',
      thrown: new BannedError('banned'),
      expected: { kind: 'moderation', reason: 'banned' },
    },
    {
      name: 'a timeout',
      thrown: new TimedOutError('timed out'),
      expected: { kind: 'moderation', reason: 'timedOut' },
    },
    {
      name: 'a temporarily unavailable write',
      thrown: new WriteTemporarilyUnavailableError('retry'),
      expected: { kind: 'syncPaused' },
    },
    {
      name: 'an exhausted storage quota',
      thrown: new DOMException('The quota has been exceeded.', 'QuotaExceededError'),
      expected: { kind: 'quota' },
    },
    {
      name: 'a DOMException that is not about quota',
      thrown: new DOMException('closed', 'InvalidStateError'),
      expected: { kind: 'unknown' },
    },
    { name: 'a bare error', thrown: new Error('boom'), expected: { kind: 'unknown' } },
    { name: 'a thrown string', thrown: 'boom', expected: { kind: 'unknown' } },
    { name: 'a thrown null', thrown: null, expected: { kind: 'unknown' } },
  ]

  it.each(cases)('maps $name', ({ thrown, expected }) => {
    expect(classifySaveError(thrown)).toEqual(expected)
  })

  it('carries the server version out of a conflict', () => {
    const error = classifySaveError(new ConflictError('SYNC_CONFLICT', 'conflict', 7))

    if (error.kind !== 'conflict') throw new Error('expected a conflict')
    expect(error.state.serverVersion).toBe(7)
    expect(error.state.detectedAt).toEqual(expect.any(String))
  })

  it('carries a null server version out of a conflict that reported none', () => {
    const error = classifySaveError(new ConflictError('CONCURRENT_WRITE', 'conflict', null))

    if (error.kind !== 'conflict') throw new Error('expected a conflict')
    expect(error.state.serverVersion).toBeNull()
  })

  it('no longer reads the message of an error that only mentions a quota', () => {
    // Message sniffing classified a plain Error as a quota failure; only the
    // DOMException the storage layer actually throws does now.
    expect(classifySaveError(new Error('storage quota reached'))).toEqual({ kind: 'unknown' })
  })
})

describe('validationSaveError', () => {
  it('carries the validator key and params into the save vocabulary', () => {
    expect(
      validationSaveError({
        key: 'pages.plannerMD.publish.missingTitle',
        params: { gifts: 'A, B' },
      }),
    ).toEqual({
      kind: 'validation',
      key: 'pages.plannerMD.publish.missingTitle',
      params: { gifts: 'A, B' },
    })
  })

  it('leaves params undefined when the validator reported none', () => {
    expect(validationSaveError({ key: 'a.b.c' })).toEqual({
      kind: 'validation',
      key: 'a.b.c',
      params: undefined,
    })
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
    error.mockRestore()
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
    error.mockRestore()
  })
})
