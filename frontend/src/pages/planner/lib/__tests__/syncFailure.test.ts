import { describe, it, expect, vi, beforeEach } from 'vitest'
import { toast as sonnerToast } from 'sonner'

import { BannedError, ConflictError, WriteTemporarilyUnavailableError } from '@/lib/api'

import { showSyncFailure } from '../syncFailure'

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

beforeEach(() => {
  vi.clearAllMocks()
})

describe('showSyncFailure', () => {
  it('names the stale version, since no dialog is mounted to render it', () => {
    showSyncFailure(new ConflictError('SYNC_CONFLICT', 'conflict', 7))

    expect(sonnerToast.error).toHaveBeenCalledWith('planner:sync.changedElsewhere', undefined)
  })

  it('leaves every other failure to the classifier', () => {
    showSyncFailure(new BannedError('banned'))

    expect(sonnerToast.error).toHaveBeenCalledWith('common:moderation.banned', undefined)
  })

  it('still reports a paused write', () => {
    showSyncFailure(new WriteTemporarilyUnavailableError('paused'))

    expect(sonnerToast.warning).toHaveBeenCalledWith(
      'common:errors.writeUnavailable.message',
      undefined,
    )
  })

  it('never leaves an imperative failure unreported', () => {
    const failures: unknown[] = [
      new ConflictError('SYNC_CONFLICT', 'conflict', 1),
      new BannedError('banned'),
      new WriteTemporarilyUnavailableError('paused'),
      new Error('boom'),
    ]

    for (const failure of failures) {
      vi.clearAllMocks()
      showSyncFailure(failure)

      const reported =
        vi.mocked(sonnerToast.error).mock.calls.length +
        vi.mocked(sonnerToast.warning).mock.calls.length
      expect(reported).toBe(1)
    }
  })
})
