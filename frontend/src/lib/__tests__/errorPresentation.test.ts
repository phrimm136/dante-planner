import { describe, it, expect, vi, beforeEach } from 'vitest'
import { toast as sonnerToast } from 'sonner'

import {
  BannedError,
  ConflictError,
  RetryableUnavailableError,
  ServiceUpdatingError,
  WriteTemporarilyUnavailableError,
} from '@/lib/api'

import {
  presentError,
  showError,
  showSuccess,
  showUnavailable,
  showValidationError,
} from '../errorPresentation'
import type { ErrorPresentation } from '../errorPresentation'
import type { AppError } from '../apiErrorClassifier'

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
  it('stays silent for a conflict, which the resolution dialog owns', () => {
    expect(presentError({ kind: 'conflict', code: 'SYNC_CONFLICT', serverVersion: 4 })).toBeNull()
  })

  it('stays silent for an unavailable write, which the sync banner owns', () => {
    expect(presentError({ kind: 'unavailable', scope: 'write' })).toBeNull()
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

  it('says nothing for the errors a dedicated surface owns', () => {
    showError(new ConflictError('SYNC_CONFLICT', 'conflict', 4))
    showError(new WriteTemporarilyUnavailableError('paused'))

    expect(sonnerToast.error).not.toHaveBeenCalled()
    expect(sonnerToast.warning).not.toHaveBeenCalled()
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

  it('says nothing for the unavailable write the sync banner owns', () => {
    showUnavailable(new WriteTemporarilyUnavailableError('paused'))

    expect(sonnerToast.warning).not.toHaveBeenCalled()
  })
})

describe('showValidationError', () => {
  it('reports a client-side rule under its own key, with no support hint', () => {
    showValidationError('planner:exportImport.invalidFileFormat')

    expect(sonnerToast.error).toHaveBeenCalledWith(
      'planner:exportImport.invalidFileFormat',
      undefined,
    )
  })

  it('carries the rule params through to the message', () => {
    showValidationError('planner:exportImport.fileTooLarge', { max: '10MB' })

    expect(sonnerToast.error).toHaveBeenCalledOnce()
  })
})

describe('showSuccess', () => {
  it('resolves the key it is given', () => {
    showSuccess('common:settings.username.saved')

    expect(sonnerToast.success).toHaveBeenCalledWith('common:settings.username.saved')
  })
})
