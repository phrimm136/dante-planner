/**
 * The caches are the app's only toast sink, so these tests drive the real
 * `queryClient`'s cache config rather than a locally built stand-in.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import type { Mutation } from '@tanstack/react-query'

import {
  ServiceUpdatingError,
  BackendUnavailableError,
  AuthTemporarilyUnavailableError,
  ValidationError,
  WriteTemporarilyUnavailableError,
} from '../api'

vi.mock('@/lib/i18n', () => ({
  default: { t: (key: string) => key },
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

import { toast } from 'sonner'
import { queryClient } from '../queryClient'

/** The cache config is declared over the fully unknown mutation. */
type AnyMutation = Mutation<unknown, unknown, unknown, unknown>
type MutationMeta = NonNullable<AnyMutation['meta']>

/** The cache only ever reads `meta` off the mutation it is handed. */
function mutationWith(meta?: MutationMeta): AnyMutation {
  return { meta } as AnyMutation
}

function toastCount(): number {
  return (
    vi.mocked(toast.error).mock.calls.length +
    vi.mocked(toast.warning).mock.calls.length +
    vi.mocked(toast.success).mock.calls.length
  )
}

const queryOnError = queryClient.getQueryCache().config.onError
const mutationOnError = queryClient.getMutationCache().config.onError
const mutationOnSuccess = queryClient.getMutationCache().config.onSuccess

beforeEach(() => {
  vi.clearAllMocks()
  vi.spyOn(console, 'error').mockImplementation(() => undefined)
})

describe('window-focus refetching', () => {
  it('refetches on focus by default, which is what keeps server-backed data fresh', () => {
    expect(queryClient.getDefaultOptions().queries?.refetchOnWindowFocus).toBe(true)
  })
})

describe('QueryCache onError', () => {
  it('reports a planned deploy', () => {
    queryOnError?.(new ServiceUpdatingError('updating'), {} as never)

    expect(toast.warning).toHaveBeenCalledWith('common:errors.serviceUpdating', undefined)
  })

  it('reports an unreachable backend', () => {
    queryOnError?.(new BackendUnavailableError('down'), {} as never)

    expect(toast.warning).toHaveBeenCalledWith('common:errors.backendUnavailable', undefined)
  })

  it('reports unavailable auth', () => {
    queryOnError?.(new AuthTemporarilyUnavailableError('paused'), {} as never)

    expect(toast.warning).toHaveBeenCalledWith('common:errors.authUnavailable', undefined)
  })

  it('stays silent for anything the route error component already shows', () => {
    queryOnError?.(new Error('HTTP error! status: 500'), {} as never)
    queryOnError?.(new Error('Failed to fetch'), {} as never)
    queryOnError?.(new ValidationError('TITLE_TOO_LONG', 'invalid'), {} as never)

    expect(toastCount()).toBe(0)
  })
})

describe('MutationCache onError', () => {
  it('toasts exactly once for a failed mutation', () => {
    mutationOnError?.(new Error('boom'), {} as never, {} as never, mutationWith(), {} as never)

    expect(toastCount()).toBe(1)
    expect(toast.error).toHaveBeenCalledWith('common:errors.generic.message', {
      description: 'common:errors.contactOnRepeat',
    })
  })

  it('toasts nothing when the mutation renders its own failure surface', () => {
    mutationOnError?.(
      new Error('boom'),
      {} as never,
      {} as never,
      mutationWith({ suppressErrorToast: true }),
      {} as never,
    )

    expect(toastCount()).toBe(0)
  })

  it('still toasts when the opt-out is absent rather than false', () => {
    mutationOnError?.(
      new Error('boom'),
      {} as never,
      {} as never,
      mutationWith({ successMessage: 'common:saved' }),
      {} as never,
    )

    expect(toastCount()).toBe(1)
  })

  it('reports a paused write, which no banner owns', () => {
    mutationOnError?.(
      new WriteTemporarilyUnavailableError('paused'),
      {} as never,
      {} as never,
      mutationWith(),
      {} as never,
    )

    expect(toast.warning).toHaveBeenCalledWith('common:errors.writeUnavailable.message', undefined)
    expect(toastCount()).toBe(1)
  })
})

describe('MutationCache onSuccess', () => {
  it('toasts the message the mutation declared', () => {
    mutationOnSuccess?.(
      undefined,
      {} as never,
      {} as never,
      mutationWith({ successMessage: 'common:settings.username.saved' }),
      {} as never,
    )

    expect(toast.success).toHaveBeenCalledWith('common:settings.username.saved')
    expect(toastCount()).toBe(1)
  })

  it('stays silent for a mutation that declared none', () => {
    mutationOnSuccess?.(undefined, {} as never, {} as never, mutationWith(), {} as never)

    expect(toastCount()).toBe(0)
  })

  it('does not fire the success message on failure', () => {
    mutationOnError?.(
      new Error('boom'),
      {} as never,
      {} as never,
      mutationWith({ successMessage: 'common:settings.username.saved' }),
      {} as never,
    )

    expect(toast.success).not.toHaveBeenCalled()
  })
})
