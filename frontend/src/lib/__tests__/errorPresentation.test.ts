import { describe, it, expect, vi, beforeEach } from 'vitest'
import { toast as sonnerToast } from 'sonner'

import {
  BannedError,
  ConflictError,
  RetryableUnavailableError,
  ServiceUpdatingError,
  WriteTemporarilyUnavailableError,
} from '@/lib/apiErrors'

import {
  presentError,
  showError,
  showSuccess,
  showUnavailable,
  showErrorMessage,
} from '../errorPresentation'
import type { ErrorPresentation } from '../errorPresentation'
import type { AppError, RestrictionKind, UnavailableScope } from '../apiErrorClassifier'

const CONTACT_MESSAGE = 'If this error persists, please contact contact@dante-planner.com.'

vi.mock('@/lib/i18n', () => ({
  default: {
    t: (key: string) => (key === 'common:errors.contactOnRepeat' ? CONTACT_MESSAGE : key),
  },
}))

vi.mock('@/components/ui/LinkifyText', () => ({
  linkifyText: (text: string) => text,
}))

vi.mock('sonner', () => {
  const toastFn = Object.assign(vi.fn(), {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    dismiss: vi.fn(),
  })
  return { toast: toastFn }
})

beforeEach(() => {
  vi.clearAllMocks()
})

describe('presentError', () => {
  it('stays silent for a sync conflict, which the resolution dialog owns', () => {
    expect(presentError({ kind: 'conflict', code: 'SYNC_CONFLICT', serverVersion: 4 })).toBeNull()
  })

  it('speaks for an unavailable write, which no surface owns', () => {
    expect(presentError({ kind: 'unavailable', scope: 'write' })).toEqual({
      key: 'common:errors.writeUnavailable.message',
      severity: 'warning',
      supportHint: false,
    })
  })

  // Keyed by kind and scope, so a case added to either union fails to compile
  // here rather than slipping through as a silently unpresented failure.
  const RESTRICTED: Record<RestrictionKind, AppError> = {
    banned: { kind: 'restricted', reason: 'banned' },
    timedOut: { kind: 'restricted', reason: 'timedOut' },
  }

  const UNAVAILABLE: Record<UnavailableScope, AppError> = {
    service: { kind: 'unavailable', scope: 'service' },
    backend: { kind: 'unavailable', scope: 'backend' },
    auth: { kind: 'unavailable', scope: 'auth' },
    write: { kind: 'unavailable', scope: 'write' },
  }

  const EVERY_CASE: Record<AppError['kind'], AppError[]> = {
    conflict: [
      { kind: 'conflict', code: 'SYNC_CONFLICT', serverVersion: 4 },
      { kind: 'conflict', code: 'PLANNER_LIMIT_EXCEEDED', serverVersion: null },
    ],
    validation: [{ kind: 'validation', key: 'planner:k' }],
    restricted: Object.values(RESTRICTED),
    rateLimit: [{ kind: 'rateLimit' }],
    forbidden: [{ kind: 'forbidden', code: 'PLANNER_FORBIDDEN' }],
    notFound: [{ kind: 'notFound' }],
    unavailable: Object.values(UNAVAILABLE),
    retryable: [{ kind: 'retryable' }],
    quota: [{ kind: 'quota' }],
    unknown: [{ kind: 'unknown' }],
  }

  const everyError = Object.values(EVERY_CASE).flat()

  it('delegates to a mounted owner for the sync conflict alone', () => {
    const silent = everyError.filter((error) => presentError(error) === null)

    expect(silent).toEqual([{ kind: 'conflict', code: 'SYNC_CONFLICT', serverVersion: 4 }])
  })

  it('gives every case it speaks for a key to render', () => {
    const keyless = everyError
      .map((error) => presentError(error))
      .filter((presentation) => presentation !== null)
      .filter((presentation) => presentation.key === '')

    expect(keyless).toEqual([])
  })

  const presented: { name: string; error: AppError; expected: ErrorPresentation }[] = [
    {
      name: 'a ban',
      error: { kind: 'restricted', reason: 'banned' },
      expected: { key: 'common:moderation.banned', severity: 'error', supportHint: false },
    },
    {
      name: 'a timeout',
      error: { kind: 'restricted', reason: 'timedOut' },
      expected: { key: 'common:moderation.timedOut', severity: 'error', supportHint: false },
    },
    {
      name: 'a planned deploy',
      error: { kind: 'unavailable', scope: 'service' },
      expected: { key: 'common:errors.serviceUpdating', severity: 'warning', supportHint: false },
    },
    {
      name: 'an unreachable backend',
      error: { kind: 'unavailable', scope: 'backend' },
      expected: {
        key: 'common:errors.backendUnavailable',
        severity: 'warning',
        supportHint: false,
      },
    },
    {
      name: 'unavailable auth',
      error: { kind: 'unavailable', scope: 'auth' },
      expected: { key: 'common:errors.authUnavailable', severity: 'warning', supportHint: false },
    },
    {
      name: 'an exhausted quota',
      error: { kind: 'quota' },
      expected: {
        key: 'planner:pages.plannerMD.save.quotaExceeded',
        severity: 'error',
        supportHint: false,
      },
    },
    {
      name: 'a rate limit',
      error: { kind: 'rateLimit' },
      expected: { key: 'common:errors.rateLimit.message', severity: 'warning', supportHint: false },
    },
    {
      name: 'a retryable failure',
      error: { kind: 'retryable' },
      expected: { key: 'common:errors.retryable.message', severity: 'warning', supportHint: true },
    },
    {
      name: 'a forbidden action',
      error: { kind: 'forbidden', code: 'PLANNER_FORBIDDEN' },
      expected: { key: 'common:errors.forbidden.message', severity: 'error', supportHint: false },
    },
    {
      name: 'a missing resource',
      error: { kind: 'notFound' },
      expected: {
        key: 'common:errors.resourceNotFound.message',
        severity: 'error',
        supportHint: false,
      },
    },
    {
      name: 'an unrecognized failure',
      error: { kind: 'unknown' },
      expected: { key: 'common:errors.generic.message', severity: 'error', supportHint: true },
    },
    {
      name: 'a write the server refused because a concurrent one landed first',
      error: { kind: 'conflict', code: 'CONCURRENT_WRITE', serverVersion: null },
      expected: { key: 'planner:sync.changedElsewhere', severity: 'warning', supportHint: false },
    },
    {
      name: 'an exhausted server-side planner allowance',
      error: { kind: 'conflict', code: 'PLANNER_LIMIT_EXCEEDED', serverVersion: null },
      expected: { key: 'common:errors.generic.message', severity: 'error', supportHint: true },
    },
    {
      name: 'a duplicate vote',
      error: { kind: 'conflict', code: 'VOTE_ALREADY_EXISTS', serverVersion: null },
      expected: { key: 'common:errors.generic.message', severity: 'error', supportHint: true },
    },
    {
      name: 'a 409 whose body could not be read, which carries the bare code',
      error: { kind: 'conflict', code: 'CONFLICT', serverVersion: null },
      expected: { key: 'common:errors.generic.message', severity: 'error', supportHint: true },
    },
    {
      name: 'a conflict code this client has never heard of',
      error: { kind: 'conflict', code: 'A_CODE_ADDED_LATER', serverVersion: null },
      expected: { key: 'common:errors.generic.message', severity: 'error', supportHint: true },
    },
  ]

  it.each(presented)('presents $name', ({ error, expected }) => {
    expect(presentError(error)).toEqual(expected)
  })

  it('carries the validator key and params through untouched', () => {
    expect(
      presentError({
        kind: 'validation',
        key: 'planner:pages.plannerMD.publish.missingTitle',
        params: { gifts: 'A, B' },
      }),
    ).toEqual({
      key: 'planner:pages.plannerMD.publish.missingTitle',
      params: { gifts: 'A, B' },
      severity: 'error',
      supportHint: false,
    })
  })
})

describe('showError', () => {
  it('appends the contact description where the error opts into support', () => {
    showError(new Error('boom'))

    expect(sonnerToast.error).toHaveBeenCalledWith('common:errors.generic.message', {
      description: CONTACT_MESSAGE,
    })
  })

  it('omits the contact description where the error does not', () => {
    showError(new BannedError('banned'))

    expect(sonnerToast.error).toHaveBeenCalledWith('common:moderation.banned', undefined)
  })

  it('warns rather than errors for a transient failure', () => {
    showError(new ServiceUpdatingError('updating'))

    expect(sonnerToast.warning).toHaveBeenCalledWith('common:errors.serviceUpdating', undefined)
    expect(sonnerToast.error).not.toHaveBeenCalled()
  })

  it('says nothing for the conflict the resolution dialog owns', () => {
    showError(new ConflictError('SYNC_CONFLICT', 'conflict', 4))

    expect(sonnerToast.error).not.toHaveBeenCalled()
    expect(sonnerToast.warning).not.toHaveBeenCalled()
  })

  it('reports a conflict no dialog owns rather than leaving the user with silence', () => {
    showError(new ConflictError('PLANNER_LIMIT_EXCEEDED', 'too many planners', null))

    expect(sonnerToast.error).toHaveBeenCalledWith('common:errors.generic.message', {
      description: CONTACT_MESSAGE,
    })
  })

  it('reports a paused write rather than swallowing the failed save', () => {
    showError(new WriteTemporarilyUnavailableError('paused'))

    expect(sonnerToast.warning).toHaveBeenCalledWith(
      'common:errors.writeUnavailable.message',
      undefined,
    )
  })
})

describe('showUnavailable', () => {
  it('reports the unavailable family', () => {
    showUnavailable(new ServiceUpdatingError('updating'))

    expect(sonnerToast.warning).toHaveBeenCalledWith('common:errors.serviceUpdating', undefined)
  })

  it('says nothing for a failure outside that family', () => {
    showUnavailable(new Error('boom'))
    showUnavailable(new BannedError('banned'))
    showUnavailable(new RetryableUnavailableError('deadlock'))

    expect(sonnerToast.error).not.toHaveBeenCalled()
    expect(sonnerToast.warning).not.toHaveBeenCalled()
  })

  it('reports the unavailable write like the rest of its family', () => {
    showUnavailable(new WriteTemporarilyUnavailableError('paused'))

    expect(sonnerToast.warning).toHaveBeenCalledWith(
      'common:errors.writeUnavailable.message',
      undefined,
    )
  })
})

describe('showErrorMessage', () => {
  it('reports a client-side rule under its own key, with no support hint', () => {
    showErrorMessage('planner:exportImport.invalidFileFormat')

    expect(sonnerToast.error).toHaveBeenCalledWith(
      'planner:exportImport.invalidFileFormat',
      undefined,
    )
  })

  it('carries the rule params through to the message', () => {
    showErrorMessage('planner:exportImport.fileTooLarge', { max: '10MB' })

    expect(sonnerToast.error).toHaveBeenCalledOnce()
  })
})

describe('showSuccess', () => {
  it('resolves the key it is given', () => {
    showSuccess('common:settings.username.saved')

    expect(sonnerToast.success).toHaveBeenCalledWith('common:settings.username.saved')
  })
})
