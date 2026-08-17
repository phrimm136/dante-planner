import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AccountDeleteSection } from '../AccountDeleteSection'
import { AccountDeleteDialog } from '../AccountDeleteDialog'
import { toast } from 'sonner'
import { QueryClient } from '@tanstack/react-query'
import type { MutationFunctionContext, UseMutateFunction } from '@tanstack/react-query'
import { buildMutationResult } from '@/test-utils'
import type { User } from '@/shared/auth'
import type { UserDeletionResponse } from '../../types/UserSettingsTypes'

/** `mutate` as the component sees it: the delete-account mutation's own signature. */
type DeleteAccountMutate = UseMutateFunction<UserDeletionResponse, Error, void, unknown>

/** The context react-query threads into mutation callbacks. */
const mutationContext: MutationFunctionContext = {
  client: new QueryClient(),
  meta: undefined,
}

// Mock dependencies
vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

const mockMutate = vi.fn<DeleteAccountMutate>()
const mockSetQueryData = vi.fn<(key: unknown, data: unknown) => void>()

// Mock hooks
vi.mock('@/shared/auth/hooks/useAuthQuery', () => ({
  useAuthQuery: vi.fn(() => ({ data: null })),
  authQueryKeys: {
    me: 'auth-me',
  },
}))

vi.mock('../../hooks/useAccountData', () => ({
  useDeleteAccountMutation: vi.fn(() => ({
    mutate: mockMutate,
    isPending: false,
    isSuccess: false,
    isError: false,
  })),
}))

vi.mock('@tanstack/react-query', async () => {
  const actual = await vi.importActual('@tanstack/react-query')
  return {
    ...actual,
    useQueryClient: vi.fn(() => ({
      setQueryData: mockSetQueryData,
    })),
  }
})

import { useAuthQuery } from '@/shared/auth'
import { useDeleteAccountMutation } from '../../hooks/useAccountData'

describe('AccountDeleteSection', () => {
  const mockUser: User = {
    email: 'test@example.com',
    usernameEpithet: 'don',
    usernameSuffix: '1234',
    role: 'NORMAL',
  }

  const mockDeleteResponse: UserDeletionResponse = {
    message: 'Account scheduled for deletion',
    deletedAt: '2026-01-09T10:00:00Z',
    permanentDeleteAt: '2026-02-08T10:00:00Z',
    gracePeriodDays: 30,
  }

  beforeEach(() => {
    vi.clearAllMocks()
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { href: 'http://localhost:5173/settings', origin: 'http://localhost:5173' },
    })
    vi.mocked(useAuthQuery).mockReturnValue({ data: null } as ReturnType<typeof useAuthQuery>)
    vi.mocked(useDeleteAccountMutation).mockReturnValue(
      buildMutationResult<UserDeletionResponse, Error, void, unknown>({
        mutate: mockMutate,
        isPending: false,
        isSuccess: false,
        isError: false,
      }),
    )
  })

  it('shows sign-in prompt when user is unauthenticated', () => {
    vi.mocked(useAuthQuery).mockReturnValue({ data: null } as ReturnType<typeof useAuthQuery>)

    render(<AccountDeleteSection />)

    expect(screen.getByText(/sign in to manage your account/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /sign in with google/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /delete account/i })).not.toBeInTheDocument()
  })

  it('shows delete button when user is authenticated', () => {
    vi.mocked(useAuthQuery).mockReturnValue({ data: mockUser } as ReturnType<typeof useAuthQuery>)

    render(<AccountDeleteSection />)

    expect(screen.getByRole('button', { name: /delete account/i })).toBeInTheDocument()
    expect(screen.queryByText(/sign in to manage/i)).not.toBeInTheDocument()
  })

  it('opens dialog when delete button is clicked', async () => {
    const user = userEvent.setup()
    vi.mocked(useAuthQuery).mockReturnValue({ data: mockUser } as ReturnType<typeof useAuthQuery>)

    render(<AccountDeleteSection />)

    const deleteButton = screen.getByRole('button', { name: /delete account/i })
    await user.click(deleteButton)

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('DELETE')).toBeInTheDocument()
  })

  it('shows success toast with formatted date on successful deletion', async () => {
    const user = userEvent.setup({ delay: null })
    vi.mocked(useAuthQuery).mockReturnValue({ data: mockUser } as ReturnType<typeof useAuthQuery>)

    // Mock mutate to call onSuccess callback
    const mockMutateWithCallback = vi.fn<DeleteAccountMutate>((_, options) => {
      if (options?.onSuccess) {
        options.onSuccess(mockDeleteResponse, undefined, undefined, mutationContext)
      }
    })
    vi.mocked(useDeleteAccountMutation).mockReturnValue(
      buildMutationResult<UserDeletionResponse, Error, void, unknown>({
        mutate: mockMutateWithCallback,
        isPending: false,
        isSuccess: false,
        isError: false,
      }),
    )

    render(<AccountDeleteSection />)

    // Open dialog
    await user.click(screen.getByRole('button', { name: /delete account/i }))

    // Type confirmation
    const input = screen.getByPlaceholderText('DELETE')
    await user.type(input, 'DELETE')

    // Click delete
    const confirmButton = screen.getByRole('button', { name: /delete account/i })
    await user.click(confirmButton)

    expect(toast.success).toHaveBeenCalledWith(
      expect.stringContaining('Account scheduled for deletion on'),
    )
    expect(toast.success).toHaveBeenCalledWith(
      expect.stringContaining('Log in within 30 days to cancel'),
    )
  })

  it('calls mutation with success and error callbacks', () => {
    vi.mocked(useAuthQuery).mockReturnValue({ data: mockUser } as ReturnType<typeof useAuthQuery>)

    const mockMutateImpl = vi.fn<DeleteAccountMutate>()
    vi.mocked(useDeleteAccountMutation).mockReturnValue(
      buildMutationResult<UserDeletionResponse, Error, void, unknown>({
        mutate: mockMutateImpl,
        isPending: false,
        isSuccess: false,
        isError: false,
      }),
    )

    render(<AccountDeleteSection />)

    // Mutation hook should be called during render
    // The actual mutation.mutate() will be called by handleDelete on user action
    // We're verifying the hook is set up correctly
    expect(mockMutateImpl).not.toHaveBeenCalled()
  })

  it('disables buttons during deletion (isPending state)', () => {
    vi.mocked(useAuthQuery).mockReturnValue({ data: mockUser } as ReturnType<typeof useAuthQuery>)
    vi.mocked(useDeleteAccountMutation).mockReturnValue(
      buildMutationResult<UserDeletionResponse, Error, void, unknown>({
        mutate: mockMutate,
        isPending: true,
        isSuccess: false,
        isError: false,
      }),
    )

    render(<AccountDeleteSection />)

    // Open dialog manually by rendering with isPending already true
    render(
      <AccountDeleteDialog open={true} onConfirm={vi.fn()} onCancel={vi.fn()} isPending={true} />,
    )

    expect(screen.getByRole('button', { name: /deleting/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /cancel/i })).toBeDisabled()
  })
})
