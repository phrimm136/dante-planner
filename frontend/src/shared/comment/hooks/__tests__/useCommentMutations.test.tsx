/**
 * Failure reporting for the comment mutations that carry their own copy.
 *
 * Run over the caches production ships, because the question is how many
 * voices a single rejection gets: the hook's own, the mutation cache's, or both.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import React from 'react'

vi.mock('@/lib/api', () => ({
  ApiClient: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

vi.mock('sonner', () => {
  const toastFn = Object.assign(vi.fn(), {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
    dismiss: vi.fn(),
  })
  return { toast: toastFn }
})

// Translation as the identity, so the key a call site chose is what the sink sees.
vi.mock('@/lib/i18n', () => ({ default: { t: (key: string) => key } }))

vi.mock('@/shared/notifications', () => ({
  requestNotificationPermission: vi.fn(),
}))

import { ApiClient } from '@/lib/api'
import { toast as sonnerToast } from 'sonner'
import { ConflictError } from '@/lib/apiErrors'
import { createTestQueryClient } from '@/test-utils/queryClient'
import { useUpvoteComment, useReportComment } from '../useCommentMutations'

const COMMENT_ID = '4f9a0e2c-1b6d-4a2e-9d1f-2c7b3a5e8d10'
const PLANNER_ID = '123e4567-e89b-12d3-a456-426614174000'

function renderOverRealCaches<T>(hook: () => T) {
  const queryClient = createTestQueryClient()
  return renderHook(hook, {
    wrapper: function Wrapper({ children }: { children: React.ReactNode }) {
      return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    },
  })
}

async function runAndSwallow(run: () => Promise<unknown>) {
  await act(async () => {
    try {
      await run()
    } catch {
      // The rejection is the subject; the hook reports it.
    }
  })
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('useUpvoteComment', () => {
  it('names the duplicate upvote once, not alongside a generic report', async () => {
    vi.mocked(ApiClient.post).mockRejectedValue(
      new ConflictError('VOTE_ALREADY_EXISTS', 'already upvoted', null),
    )
    const { result } = renderOverRealCaches(() => useUpvoteComment())

    await runAndSwallow(() =>
      result.current.mutateAsync({ commentId: COMMENT_ID, plannerId: PLANNER_ID }),
    )

    await waitFor(() => {
      expect(sonnerToast.error).toHaveBeenCalledWith(
        'common:comments.toast.alreadyUpvoted',
        undefined,
      )
    })
    expect(sonnerToast.error).toHaveBeenCalledOnce()
  })

  it('reports a failure it has no copy for exactly once', async () => {
    vi.mocked(ApiClient.post).mockRejectedValue(new Error('network down'))
    const { result } = renderOverRealCaches(() => useUpvoteComment())

    await runAndSwallow(() =>
      result.current.mutateAsync({ commentId: COMMENT_ID, plannerId: PLANNER_ID }),
    )

    await waitFor(() => {
      expect(sonnerToast.error).toHaveBeenCalledOnce()
    })
    expect(sonnerToast.error).toHaveBeenCalledWith('common:errors.generic.message', {
      description: expect.anything(),
    })
  })
})

describe('useReportComment', () => {
  it('names the duplicate report once, not alongside a generic report', async () => {
    vi.mocked(ApiClient.post).mockRejectedValue(
      new ConflictError('COMMENT_REPORT_ALREADY_EXISTS', 'already reported', null),
    )
    const { result } = renderOverRealCaches(() => useReportComment())

    await runAndSwallow(() =>
      result.current.mutateAsync({
        commentId: COMMENT_ID,
        plannerId: PLANNER_ID,
        reason: 'SPAM',
      }),
    )

    await waitFor(() => {
      expect(sonnerToast.error).toHaveBeenCalledWith(
        'common:comments.toast.alreadyReported',
        undefined,
      )
    })
    expect(sonnerToast.error).toHaveBeenCalledOnce()
  })

  it('reports a failure it has no copy for exactly once', async () => {
    vi.mocked(ApiClient.post).mockRejectedValue(new Error('network down'))
    const { result } = renderOverRealCaches(() => useReportComment())

    await runAndSwallow(() =>
      result.current.mutateAsync({
        commentId: COMMENT_ID,
        plannerId: PLANNER_ID,
        reason: 'SPAM',
      }),
    )

    await waitFor(() => {
      expect(sonnerToast.error).toHaveBeenCalledOnce()
    })
    expect(sonnerToast.error).toHaveBeenCalledWith('common:errors.generic.message', {
      description: expect.anything(),
    })
  })
})
