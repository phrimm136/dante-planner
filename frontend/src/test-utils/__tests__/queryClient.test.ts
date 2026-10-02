/**
 * A bare QueryClient in tests makes the toast sink unobservable, so component
 * tests silently pass over failures production would report.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { toast } from 'sonner'

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

import { BackendUnavailableError } from '@/lib/apiErrors'
import { createTestQueryClient } from '../queryClient'

beforeEach(() => {
  vi.clearAllMocks()
  vi.spyOn(console, 'error').mockImplementation(() => undefined)
})

describe('createTestQueryClient', () => {
  it('carries the mutation sink, so a failed mutation is observable', () => {
    const client = createTestQueryClient()

    client
      .getMutationCache()
      .config.onError?.(
        new Error('boom'),
        {} as never,
        {} as never,
        { meta: {} } as never,
        {} as never,
      )

    expect(toast.error).toHaveBeenCalledWith('common:errors.generic.message', {
      description: 'common:errors.contactOnRepeat',
    })
  })

  it('carries the query sink', () => {
    const client = createTestQueryClient()

    client.getQueryCache().config.onError?.(new Error('boom'), {} as never)

    expect(console.error).toHaveBeenCalled()
  })

  it('leaves a query unavailable error to the banner rather than a toast', () => {
    const client = createTestQueryClient()

    client.getQueryCache().config.onError?.(new BackendUnavailableError('down'), {} as never)

    expect(toast.warning).not.toHaveBeenCalled()
    expect(toast.error).not.toHaveBeenCalled()
  })
})
