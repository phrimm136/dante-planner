/**
 * useDeleteAccountFlow.test.tsx
 *
 * The deletion flow arms a redirect the success toast is read during. A user who
 * leaves the settings page inside that window must not be pulled to the landing
 * page by a timer nothing owns.
 */

import { renderHook, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { QueryClient } from '@tanstack/react-query'

import { buildMutationResult } from '@/test-utils'
import { authQueryKeys } from '@/shared/auth'

import type { MutationFunctionContext, UseMutateFunction } from '@tanstack/react-query'
import type { UserDeletionResponse } from '../../types/UserSettingsTypes'

const mockNavigate = vi.fn()
const mockSetQueryData = vi.fn<(key: unknown, data: unknown) => void>()
const mockMutate = vi.fn<UseMutateFunction<UserDeletionResponse, Error, void, unknown>>()

vi.mock('react-i18next', () => ({
  initReactI18next: { type: '3rdParty', init: () => {} },
  useTranslation: () => ({ i18n: { language: 'EN' } }),
}))

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mockNavigate,
}))

vi.mock('@tanstack/react-query', async () => {
  const actual =
    await vi.importActual<typeof import('@tanstack/react-query')>('@tanstack/react-query')
  return {
    ...actual,
    useQueryClient: () => ({ setQueryData: mockSetQueryData }),
  }
})

vi.mock('../useAccountData', () => ({
  useDeleteAccountMutation: vi.fn(),
}))

vi.mock('@/lib/errorPresentation', () => ({
  showSuccess: vi.fn(),
}))

import { showSuccess } from '@/lib/errorPresentation'
import { useDeleteAccountMutation } from '../useAccountData'
import { useDeleteAccountFlow } from '../useDeleteAccountFlow'

/** The context react-query threads into mutation callbacks. */
const mutationContext: MutationFunctionContext = {
  client: new QueryClient(),
  meta: undefined,
}

const DELETION_RESPONSE: UserDeletionResponse = {
  message: 'Account scheduled for deletion',
  deletedAt: '2026-01-09T10:00:00Z',
  permanentDeleteAt: '2026-02-08T10:00:00Z',
  gracePeriodDays: 30,
}

describe('useDeleteAccountFlow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()

    // A deletion that succeeds the moment it is asked for.
    mockMutate.mockImplementation((_variables, options) => {
      options?.onSuccess?.(DELETION_RESPONSE, undefined, undefined, mutationContext)
    })
    vi.mocked(useDeleteAccountMutation).mockReturnValue(
      buildMutationResult<UserDeletionResponse, Error, void, unknown>({
        mutate: mockMutate,
        isPending: false,
      }),
    )
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('opens and closes the confirmation dialog', () => {
    const { result } = renderHook(() => useDeleteAccountFlow())

    expect(result.current.dialogOpen).toBe(false)

    act(() => {
      result.current.openDialog()
    })
    expect(result.current.dialogOpen).toBe(true)

    act(() => {
      result.current.closeDialog()
    })
    expect(result.current.dialogOpen).toBe(false)
  })

  it('reports the deletion, empties the auth cache, and closes the dialog', () => {
    const { result } = renderHook(() => useDeleteAccountFlow())

    act(() => {
      result.current.openDialog()
    })
    act(() => {
      result.current.confirmDelete()
    })

    expect(showSuccess).toHaveBeenCalledWith(
      'common:settings.deleteAccount.success',
      expect.objectContaining({ days: 30 }),
    )
    expect(mockSetQueryData).toHaveBeenCalledWith(authQueryKeys.me, null)
    expect(result.current.dialogOpen).toBe(false)
  })

  it('redirects once the grace window elapses', () => {
    const { result } = renderHook(() => useDeleteAccountFlow())

    act(() => {
      result.current.confirmDelete()
    })
    expect(mockNavigate).not.toHaveBeenCalled()

    act(() => {
      vi.runAllTimers()
    })

    expect(mockNavigate).toHaveBeenCalledWith({ to: '/' })
  })

  it('cancels the redirect when the section unmounts first', () => {
    const { result, unmount } = renderHook(() => useDeleteAccountFlow())

    act(() => {
      result.current.confirmDelete()
    })

    unmount()

    act(() => {
      vi.runAllTimers()
    })

    expect(mockNavigate).not.toHaveBeenCalled()
  })
})
